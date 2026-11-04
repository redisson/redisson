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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.pubsub.LockPubSub;

/**
 * Distributed implementation of {@link java.util.concurrent.locks.Lock}
 * Implements reentrant lock.<br>
 * Lock will be removed automatically if client disconnects.
 * <p>
 * Implements a <b>fair</b> locking so it guarantees an acquire order by threads.
 * <p>
 * Four keys hold the state of the lock. The lock hash holds the owner. A list holds
 * the waiting threads in the order of their requests. The timeout set gives each
 * waiting thread a turn estimate: the time at which its turn is expected, computed
 * from the waits of the threads ahead of it. The alive set gives each waiting
 * thread a liveness deadline. The Redisson instance of the thread moves the deadline
 * forward while the thread waits. This is the same mechanism as the lock watchdog
 * for the holder. A thread whose deadline has passed is dead. The next script that
 * runs removes it from the queue. An older Redisson version does not write a
 * deadline. A thread without a deadline is dead when its turn estimate has passed.
 * This is the rule that the older version applies.
 * <p>
 * An older version and this version can share a lock during a rolling upgrade. The
 * older version judges every thread by its turn estimate. Thus the watchdog also
 * keeps the turn estimate of a renewed thread ahead of the clock. The older version
 * does not know the alive set. Thus a thread that it dequeues keeps its deadline.
 * The next acquisition or forced unlock removes the deadlines of threads that are
 * not queued.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonFairLock extends RedissonLock implements RLock {

    // Removes the deadlines of threads that are not in the timeout set. An older Redisson
    // version dequeues a thread and does not remove its deadline. If that thread then
    // dies, nothing else removes the deadline. The alive set has at most one member for
    // each queued thread, so the scan is short.
    static final String PRUNE_ALIVE_SET =
            "local function pruneAliveSet(timeoutKey, aliveKey) " +
                "local deadlines = redis.call('zrange', aliveKey, 0, -1); " +
                "for i = 1, #deadlines, 1 do " +
                    "if redis.call('zscore', timeoutKey, deadlines[i]) == false then " +
                        "redis.call('zrem', aliveKey, deadlines[i]); " +
                    "end; " +
                "end; " +
            "end; ";

    // evictDeadWaiters(queueKey, timeoutKey, aliveKey, now) -> true if it removed a thread
    // Removes dead threads from the head of the queue.
    static final String EVICT_DEAD_WAITERS =
            "local function evictDeadWaiters(queueKey, timeoutKey, aliveKey, now) " +
                "local evicted = false; " +
                "while true do " +
                    "local head = redis.call('lindex', queueKey, 0); " +
                    "if head == false then " +
                        "break; " +
                    "end; " +
                    "local deadline = redis.call('zscore', aliveKey, head); " +
                    "if deadline == false then " +
                        "deadline = redis.call('zscore', timeoutKey, head); " +
                    "end; " +
                    "if deadline == false or tonumber(deadline) > now then " +
                        "break; " +
                    "end; " +
                    "redis.call('zrem', timeoutKey, head); " +
                    "redis.call('zrem', aliveKey, head); " +
                    "redis.call('lpop', queueKey); " +
                    "evicted = true; " +
                "end; " +
                "return evicted; " +
            "end; ";

    // publishToNewHead(lockKey, queueKey, channelKey, me, publishCmd, unlockMsg)
    // The acquisition scripts call this function after evictDeadWaiters removed a thread.
    // No unlock occurred, so no unlock message was sent.
    static final String PUBLISH_TO_NEW_HEAD =
            "local function publishToNewHead(lockKey, queueKey, channelKey, me, publishCmd, unlockMsg) " +
                "if redis.call('exists', lockKey) == 0 then " +
                    "local head = redis.call('lindex', queueKey, 0); " +
                    "if head ~= false and head ~= me then " +
                        "redis.call(publishCmd, channelKey .. ':' .. head, unlockMsg); " +
                    "end; " +
                "end; " +
            "end; ";

    // ttlWhenQueued(lockKey, queueKey, timeoutKey, aliveKey, now, liveness) -> ms to wait
    // Returns the time that a queued thread must wait before it asks again.
    // Case 1: the lock is held. The answer is the remaining lease of the holder. An unlock
    // wakes the first thread in line earlier.
    // Case 2: the lock key is absent. The thread is not first in line, because a thread
    // that is first in line acquires the lock and does not reach this code.
    static final String TTL_WHEN_QUEUED =
            "local function ttlWhenQueued(lockKey, queueKey, timeoutKey, aliveKey, now, liveness) " +
                "local ttl = math.min(redis.call('pttl', lockKey), liveness); " +
                "if ttl < 0 then " +
                    "ttl = math.max(100, liveness / 100); " +
                    "local head = redis.call('lindex', queueKey, 0); " +
                    "if head ~= false then " +
                        "local deadline = redis.call('zscore', aliveKey, head); " +
                        "if deadline == false then " +
                            "deadline = redis.call('zscore', timeoutKey, head); " +
                        "end; " +
                        "if deadline ~= false then " +
                            "ttl = math.min(ttl, math.max(0, tonumber(deadline) - now)); " +
                        "end; " +
                    "end; " +
                "end; " +
                "return ttl; " +
            "end; ";

    private final long threadWaitTime;
    final long waiterLiveness;
    private final CommandAsyncExecutor commandExecutor;
    final String threadsQueueName;
    // Compatibility key. The timeout set holds the turn estimates. Only clients of Redisson
    // versions without liveness tracking act on the estimates: they remove a waiting thread
    // when its estimate has passed, and they sleep on it. This version writes the estimates
    // for those clients and reads them in two fallbacks, for a thread without a deadline.
    // Two uses are forced while those clients exist: the "already queued" check and the
    // dedupe on enqueue. An older client that removes a thread leaves the alive member in
    // place, so the alive set cannot answer "is this thread queued" in a mixed fleet. The
    // key will be removed together with the deprecated fairLockWaitTimeout setting.
    @Deprecated
    final String timeoutSetName;
    final String aliveSetName;

    public RedissonFairLock(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        this.commandExecutor = commandExecutor;
        // The setting is deprecated. Only clients of older Redisson versions read the turn
        // estimates that it shapes. This lock writes those estimates until the estimates are
        // removed, so it reads the deprecated setting on purpose.
        @SuppressWarnings("deprecation")
        long threadWaitTime = getServiceManager().getCfg().getFairLockWaitTimeout();
        this.threadWaitTime = threadWaitTime;
        this.waiterLiveness = getServiceManager().getCfg().getLockWatchdogTimeout();
        threadsQueueName = prefixName("redisson_lock_queue", getRawName());
        timeoutSetName = prefixName("redisson_lock_timeout", getRawName());
        aliveSetName = prefixName("redisson_lock_alive", getRawName());
    }

    @Override
    protected CompletableFuture<RedissonLockEntry> subscribe(long threadId) {
        return pubSub.subscribe(getEntryName() + ":" + threadId,
                getChannelName() + ":" + getLockName(threadId));
    }

    @Override
    protected void unsubscribe(RedissonLockEntry entry, long threadId) {
        pubSub.unsubscribe(entry, getEntryName() + ":" + threadId,
                getChannelName() + ":" + getLockName(threadId));
    }

    @Override
    protected void scheduleWaiterRenewal(long threadId) {
        renewalScheduler.renewFairLockWaiter(getRawName(), aliveSetName, timeoutSetName, threadId, getLockName(threadId));
    }

    @Override
    protected void cancelWaiterRenewal(long threadId) {
        renewalScheduler.cancelFairLockWaiterRenewal(getRawName(), threadId);
    }

    long queuedWaitTime(long waitTime, TimeUnit unit) {
        if (waitTime > 0) {
            return Math.min(unit.toMillis(waitTime), threadWaitTime);
        }
        return threadWaitTime;
    }

    @Override
    protected CompletableFuture<Void> acquireFailedAsync(long waitTime, TimeUnit unit, long threadId) {
        cancelWaiterRenewal(threadId);
        long wait = queuedWaitTime(waitTime, unit);

        RFuture<Void> f = commandExecutor.syncedEvalWithRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                // get the existing timeout for the thread to remove
                "local queue = redis.call('lrange', KEYS[1], 0, -1);" +
                        // find the location in the queue where the thread is
                        "local i = 1;" +
                        "while i <= #queue and queue[i] ~= ARGV[1] do " +
                        "i = i + 1;" +
                        "end;" +
                        // go to the next index which will exist after the current thread is removed
                        "i = i + 1;" +
                        // decrement the timeout for the rest of the queue after the thread being removed
                        "while i <= #queue do " +
                        "redis.call('zincrby', KEYS[2], -tonumber(ARGV[2]), queue[i]);" +
                        "i = i + 1;" +
                        "end;" +
                        // remove the thread from the queue, timeouts set and liveness set
                        "redis.call('zrem', KEYS[2], ARGV[1]);" +
                        "redis.call('zrem', KEYS[3], ARGV[1]);" +
                        "redis.call('lrem', KEYS[1], 0, ARGV[1]);",
                Arrays.asList(threadsQueueName, timeoutSetName, aliveSetName),
                getLockName(threadId), wait);
        return f.toCompletableFuture();
    }

    @Override
    <T> RFuture<T> tryLockInnerAsync(long waitTime, long leaseTime, TimeUnit unit, long threadId, RedisStrictCommand<T> command) {
        long wait = queuedWaitTime(waitTime, unit);

        long currentTime = System.currentTimeMillis();
        if (command == RedisCommands.EVAL_NULL_BOOLEAN) {
            return commandExecutor.syncedEvalNoRetry(getRawName(), LongCodec.INSTANCE, command,
                    EVICT_DEAD_WAITERS +
                    PUBLISH_TO_NEW_HEAD +
                    PRUNE_ALIVE_SET +

                    "if evictDeadWaiters(KEYS[2], KEYS[3], KEYS[4], tonumber(ARGV[3])) then " +
                        "publishToNewHead(KEYS[1], KEYS[2], KEYS[5], ARGV[2], ARGV[5], ARGV[6]); " +
                    "end; " +

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
                    "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                        "redis.call('hincrby', KEYS[1], ARGV[2], 1);" +
                        "redis.call('pexpire', KEYS[1], ARGV[1]);" +
                        "return nil;" +
                    "end;" +
                    "return 1;",
                    Arrays.asList(getRawName(), threadsQueueName, timeoutSetName, aliveSetName, getChannelName()),
                    unit.toMillis(leaseTime), getLockName(threadId), currentTime, wait,
                    getSubscribeService().getPublishCommand(), LockPubSub.UNLOCK_MESSAGE);
        }

        if (command == RedisCommands.EVAL_LONG) {
            return commandExecutor.syncedEvalNoRetry(getRawName(), LongCodec.INSTANCE, command,
                    EVICT_DEAD_WAITERS +
                    PUBLISH_TO_NEW_HEAD +
                    PRUNE_ALIVE_SET +
                    TTL_WHEN_QUEUED +

                    "local now = tonumber(ARGV[4]); " +
                    "local liveness = tonumber(ARGV[7]); " +
                    "if evictDeadWaiters(KEYS[2], KEYS[3], KEYS[4], now) then " +
                        "publishToNewHead(KEYS[1], KEYS[2], KEYS[5], ARGV[2], ARGV[5], ARGV[6]); " +
                    "end; " +

                    // check if the lock can be acquired now
                    "if (redis.call('exists', KEYS[1]) == 0) " +
                        "and ((redis.call('exists', KEYS[2]) == 0) " +
                            "or (redis.call('lindex', KEYS[2], 0) == ARGV[2])) then " +

                        // remove this thread from the queue, timeout set and liveness set
                        "redis.call('lpop', KEYS[2]);" +
                        "redis.call('zrem', KEYS[3], ARGV[2]);" +
                        "redis.call('zrem', KEYS[4], ARGV[2]);" +
                        "pruneAliveSet(KEYS[3], KEYS[4]); " +

                        // decrease timeouts for all waiting in the queue
                        "local keys = redis.call('zrange', KEYS[3], 0, -1);" +
                        "for i = 1, #keys, 1 do " +
                            "redis.call('zincrby', KEYS[3], -tonumber(ARGV[3]), keys[i]);" +
                        "end;" +

                        // acquire the lock and set the TTL for the lease
                        "redis.call('hset', KEYS[1], ARGV[2], 1);" +
                        "redis.call('pexpire', KEYS[1], ARGV[1]);" +
                        "return nil;" +
                    "end;" +

                    // check if the lock is already held, and this is a re-entry
                    "if redis.call('hexists', KEYS[1], ARGV[2]) == 1 then " +
                        "redis.call('hincrby', KEYS[1], ARGV[2],1);" +
                        "redis.call('pexpire', KEYS[1], ARGV[1]);" +
                        "return nil;" +
                    "end;" +

                    // the lock cannot be acquired
                    // check if the thread is already in the queue. A thread that asks again
                    // is alive, so the deadline moves forward here as well.
                    "local timeout = redis.call('zscore', KEYS[3], ARGV[2]);" +
                    "if timeout ~= false then " +
                        "redis.call('zadd', KEYS[4], now + liveness, ARGV[2]); " +
                        "return ttlWhenQueued(KEYS[1], KEYS[2], KEYS[3], KEYS[4], now, liveness); " +
                    "end;" +

                    // add the thread to the queue at the end, and set its timeout in the timeout set to the timeout of
                    // the prior thread in the queue (or the timeout of the lock if the queue is empty) plus the
                    // threadWaitTime
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
                    // the turn estimate is only a hint for the sleep time. A negative estimate
                    // means that the queue ahead is overdue. With a negative value the caller
                    // waits for a message without a time limit. Thus the value becomes 0,
                    // which means "ask again now".
                    "return math.max(ttl, 0);",
                    Arrays.asList(getRawName(), threadsQueueName, timeoutSetName, aliveSetName, getChannelName()),
                    unit.toMillis(leaseTime), getLockName(threadId), wait, currentTime,
                    getSubscribeService().getPublishCommand(), LockPubSub.UNLOCK_MESSAGE, waiterLiveness);
        }

        throw new IllegalArgumentException();
    }

    @Override
    protected RFuture<Boolean> unlockInnerAsync(long threadId, String requestId, long timeout) {
        return evalWriteSyncedNoRetryAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
          "local val = redis.call('get', KEYS[5]); " +
                "if val ~= false then " +
                    "return tonumber(val);" +
                "end; " +

                EVICT_DEAD_WAITERS +
                "evictDeadWaiters(KEYS[2], KEYS[3], KEYS[6], tonumber(ARGV[4])); " +

              "if (redis.call('exists', KEYS[1]) == 0) then " +
                    "local nextThreadId = redis.call('lindex', KEYS[2], 0); " +
                    "if nextThreadId ~= false then " +
                        "redis.call(ARGV[5], KEYS[4] .. ':' .. nextThreadId, ARGV[1]); " +
                    "end; " +
                    "redis.call('set', KEYS[5], 1, 'px', ARGV[6]); " +
                    "return 1; " +
                "end;" +
                "if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then " +
                    "return nil;" +
                "end; " +
                "local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1); " +
                "if (counter > 0) then " +
                    "redis.call('pexpire', KEYS[1], ARGV[2]); " +
                    "redis.call('set', KEYS[5], 0, 'px', ARGV[6]); " +
                    "return 0; " +
                "end; " +

                "redis.call('del', KEYS[1]); " +
                "redis.call('set', KEYS[5], 1, 'px', ARGV[6]); " +
                "local nextThreadId = redis.call('lindex', KEYS[2], 0); " +
                "if nextThreadId ~= false then " +
                    "redis.call(ARGV[5], KEYS[4] .. ':' .. nextThreadId, ARGV[1]); " +
                "end; " +
                "return 1; ",
                Arrays.asList(getRawName(), threadsQueueName, timeoutSetName, getChannelName(),
                        getUnlockLatchName(requestId), aliveSetName),
                    LockPubSub.UNLOCK_MESSAGE, internalLockLeaseTime, getLockName(threadId),
                    System.currentTimeMillis(), getSubscribeService().getPublishCommand(), timeout);
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return deleteAsync(getRawName(), threadsQueueName, timeoutSetName, aliveSetName);
    }

    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        List<Object> keys = Arrays.asList(getRawName(), threadsQueueName, timeoutSetName, aliveSetName);
        return super.sizeInMemoryAsync(keys);
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        return super.expireAsync(timeToLive, timeUnit, param, getRawName(), threadsQueueName, timeoutSetName, aliveSetName);
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        return super.expireAtAsync(timestamp, param, getRawName(), threadsQueueName, timeoutSetName, aliveSetName);
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return clearExpireAsync(getRawName(), threadsQueueName, timeoutSetName, aliveSetName);
    }


    @Override
    public RFuture<Boolean> forceUnlockAsync() {
        cancelExpirationRenewal(null, null);
        return commandExecutor.syncedEvalWithRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                EVICT_DEAD_WAITERS +
                PRUNE_ALIVE_SET +

                "local now = tonumber(ARGV[2]); " +
                "evictDeadWaiters(KEYS[2], KEYS[3], KEYS[5], now); " +

                "local waiters = redis.call('lrange', KEYS[2], 0, -1); " +
                "for i = 1, #waiters, 1 do " +
                    "local deadline = redis.call('zscore', KEYS[5], waiters[i]); " +
                    "if deadline ~= false and tonumber(deadline) <= now then " +
                        "redis.call('lrem', KEYS[2], 0, waiters[i]); " +
                        "redis.call('zrem', KEYS[3], waiters[i]); " +
                        "redis.call('zrem', KEYS[5], waiters[i]); " +
                    "end; " +
                "end; " +
                "pruneAliveSet(KEYS[3], KEYS[5]); " +

                "local deleted = redis.call('del', KEYS[1]); " +
                "local nextThreadId = redis.call('lindex', KEYS[2], 0); " +
                "if nextThreadId ~= false then " +
                    "redis.call(ARGV[3], KEYS[4] .. ':' .. nextThreadId, ARGV[1]); " +
                "end; " +
                "return deleted;",
                Arrays.asList(getRawName(), threadsQueueName, timeoutSetName, getChannelName(), aliveSetName),
                LockPubSub.UNLOCK_MESSAGE, System.currentTimeMillis(),
                getSubscribeService().getPublishCommand());
    }

}
