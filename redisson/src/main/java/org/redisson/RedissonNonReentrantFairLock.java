/**
 * Copyright (c) 2013-2026 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson;

import org.redisson.api.RFuture;
import org.redisson.client.RedisException;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.pubsub.LockPubSub;

import java.util.Arrays;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Distributed implementation of {@link java.util.concurrent.locks.Lock} that is
 * <b>fair</b> and <b>non-reentrant</b>. Acquisition order is FIFO across all
 * Redisson instances; a thread that already holds the lock and attempts to
 * acquire it again throws {@link IllegalMonitorStateException}, both for
 * {@code lock()} and {@code tryLock()}. Other threads contend for the lock
 * via the fair queue as usual.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonNonReentrantFairLock extends RedissonFairLock {

    public RedissonNonReentrantFairLock(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    @Override
    <T> RFuture<T> tryLockInnerAsync(long waitTime, long leaseTime, TimeUnit unit,
                                     long threadId, RedisStrictCommand<T> command) {
        long wait = queuedWaitTime(waitTime, unit);
        long currentTime = System.currentTimeMillis();

        RFuture<T> raw;
        if (command == RedisCommands.EVAL_NULL_BOOLEAN) {
            raw = evalWriteSyncedNoRetryAsync(getRawName(), LongCodec.INSTANCE, command,
                    EVICT_DEAD_WAITERS +
                    PUBLISH_TO_NEW_HEAD +
                    PRUNE_ALIVE_SET +

                    "if evictDeadWaiters(KEYS[2], KEYS[3], KEYS[4], tonumber(ARGV[3])) then " +
                        "publishToNewHead(KEYS[1], KEYS[2], KEYS[5], ARGV[2], ARGV[6], ARGV[7]); " +
                    "end; " +

                    // first acquire path
                    "if (redis.call('exists', KEYS[1]) == 0) " +
                        "and ((redis.call('exists', KEYS[2]) == 0) " +
                            "or (redis.call('lindex', KEYS[2], 0) == ARGV[2])) then " +
                        "redis.call('lpop', KEYS[2]);" +
                        "redis.call('zrem', KEYS[3], ARGV[2]);" +
                        "redis.call('zrem', KEYS[4], ARGV[2]);" +
                        "pruneAliveSet(KEYS[3], KEYS[4]); " +

                        // decrease timeouts for all waiting in the queue
                        "local keys = redis.call('zrange', KEYS[3], 0, -1);" +
                        "for i = 1, #keys, 1 do " +
                            "redis.call('zincrby', KEYS[3], -tonumber(ARGV[4]), keys[i]);" +
                        "end;" +

                        "redis.call('hset', KEYS[1], ARGV[2], 1);" +
                        "redis.call('pexpire', KEYS[1], ARGV[1]);" +
                        "return nil;" +
                    "end;" +
                    // non-reentrant: same-thread reacquire is rejected
                    "if redis.call('hexists', KEYS[1], ARGV[2]) == 1 then " +
                        "return redis.error_reply(ARGV[5]);" +
                    "end;" +
                    "return 1;",
                    Arrays.asList(getRawName(), threadsQueueName, timeoutSetName, aliveSetName, getChannelName()),
                    unit.toMillis(leaseTime), getLockName(threadId), currentTime, wait,
                    RedissonNonReentrantLock.REENTRY_ERR,
                    getSubscribeService().getPublishCommand(), LockPubSub.UNLOCK_MESSAGE);
        } else if (command == RedisCommands.EVAL_LONG) {
            raw = evalWriteSyncedNoRetryAsync(getRawName(), LongCodec.INSTANCE, command,
                    EVICT_DEAD_WAITERS +
                    PUBLISH_TO_NEW_HEAD +
                    PRUNE_ALIVE_SET +
                    TTL_WHEN_QUEUED +

                    "local now = tonumber(ARGV[4]); " +
                    "local liveness = tonumber(ARGV[8]); " +
                    "if evictDeadWaiters(KEYS[2], KEYS[3], KEYS[4], now) then " +
                        "publishToNewHead(KEYS[1], KEYS[2], KEYS[5], ARGV[2], ARGV[6], ARGV[7]); " +
                    "end; " +

                    // first acquire path
                    "if (redis.call('exists', KEYS[1]) == 0) " +
                        "and ((redis.call('exists', KEYS[2]) == 0) " +
                            "or (redis.call('lindex', KEYS[2], 0) == ARGV[2])) then " +

                        "redis.call('lpop', KEYS[2]);" +
                        "redis.call('zrem', KEYS[3], ARGV[2]);" +
                        "redis.call('zrem', KEYS[4], ARGV[2]);" +
                        "pruneAliveSet(KEYS[3], KEYS[4]); " +

                        // decrease timeouts for all waiting in the queue
                        "local keys = redis.call('zrange', KEYS[3], 0, -1);" +
                        "for i = 1, #keys, 1 do " +
                            "redis.call('zincrby', KEYS[3], -tonumber(ARGV[3]), keys[i]);" +
                        "end;" +

                        "redis.call('hset', KEYS[1], ARGV[2], 1);" +
                        "redis.call('pexpire', KEYS[1], ARGV[1]);" +
                        "return nil;" +
                    "end;" +

                    // non-reentrant: same-thread reacquire is rejected
                    "if redis.call('hexists', KEYS[1], ARGV[2]) == 1 then " +
                        "return redis.error_reply(ARGV[5]);" +
                    "end;" +

                    // contention path: check if the thread is already in the queue. A thread
                    // that asks again is alive, so the deadline moves forward here as well.
                    "local timeout = redis.call('zscore', KEYS[3], ARGV[2]);" +
                    "if timeout ~= false then " +
                        "redis.call('zadd', KEYS[4], now + liveness, ARGV[2]); " +
                        "return ttlWhenQueued(KEYS[1], KEYS[2], KEYS[3], KEYS[4], now, liveness); " +
                    "end;" +

                    // enqueue
                    "local lastThreadId = redis.call('lindex', KEYS[2], -1);" +
                    "local ttl;" +
                    "if lastThreadId ~= false and lastThreadId ~= ARGV[2] and redis.call('zscore', KEYS[3], lastThreadId) ~= false then " +
                        "ttl = tonumber(redis.call('zscore', KEYS[3], lastThreadId)) - now;" +
                    "else " +
                        "ttl = redis.call('pttl', KEYS[1]);" +
                    "end;" +
                    "local timeout = ttl + tonumber(ARGV[3]) + now;" +
                    "if redis.call('zadd', KEYS[3], timeout, ARGV[2]) == 1 then " +
                        "redis.call('rpush', KEYS[2], ARGV[2]);" +
                    "end;" +
                    "redis.call('zadd', KEYS[4], now + liveness, ARGV[2]); " +
                    // the turn estimate is only a hint for the sleep time. See
                    // RedissonFairLock for the reason why a negative value becomes 0.
                    "return math.max(ttl, 0);",
                    Arrays.asList(getRawName(), threadsQueueName, timeoutSetName, aliveSetName, getChannelName()),
                    unit.toMillis(leaseTime), getLockName(threadId), wait, currentTime,
                    RedissonNonReentrantLock.REENTRY_ERR,
                    getSubscribeService().getPublishCommand(), LockPubSub.UNLOCK_MESSAGE,
                    waiterLiveness);
        } else {
            throw new IllegalArgumentException();
        }

        return new CompletableFutureWrapper<>(raw.handle(this::translate));
    }

    private <V> V translate(V v, Throwable ex) {
        if (ex == null) {
            return v;
        }
        if (isReentryError(ex)) {
            throw new CompletionException(new IllegalMonitorStateException(
                    "Lock '" + getRawName() + "' is non-reentrant and is already held by thread "
                            + Thread.currentThread().getId()));
        }
        if (ex instanceof CompletionException) {
            throw (CompletionException) ex;
        }
        throw new CompletionException(ex);
    }

    private static boolean isReentryError(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getMessage() != null && c.getMessage().contains(RedissonNonReentrantLock.REENTRY_ERR)) {
                return true;
            }
        }
        return false;
    }

    private static RuntimeException unwrapImse(RedisException e) {
        if (e.getCause() instanceof IllegalMonitorStateException) {
            return (IllegalMonitorStateException) e.getCause();
        }
        return e;
    }

    @Override
    public void lock() {
        try {
            super.lock();
        } catch (RedisException e) {
            throw unwrapImse(e);
        }
    }

    @Override
    public void lock(long leaseTime, TimeUnit unit) {
        try {
            super.lock(leaseTime, unit);
        } catch (RedisException e) {
            throw unwrapImse(e);
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        try {
            super.lockInterruptibly();
        } catch (RedisException e) {
            throw unwrapImse(e);
        }
    }

    @Override
    public void lockInterruptibly(long leaseTime, TimeUnit unit) throws InterruptedException {
        try {
            super.lockInterruptibly(leaseTime, unit);
        } catch (RedisException e) {
            throw unwrapImse(e);
        }
    }

    @Override
    public boolean tryLock() {
        try {
            return super.tryLock();
        } catch (RedisException e) {
            throw unwrapImse(e);
        }
    }

    @Override
    public boolean tryLock(long waitTime, TimeUnit unit) throws InterruptedException {
        try {
            return super.tryLock(waitTime, unit);
        } catch (RedisException e) {
            throw unwrapImse(e);
        }
    }

    @Override
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        try {
            return super.tryLock(waitTime, leaseTime, unit);
        } catch (RedisException e) {
            throw unwrapImse(e);
        }
    }
}
