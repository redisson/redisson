/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import org.redisson.api.LockOptions;
import org.redisson.api.RFuture;
import org.redisson.client.RedisException;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Distributed implementation of {@link java.util.concurrent.locks.Lock}
 * Implements reentrant lock.<br>
 * Lock will be removed automatically if client disconnects.
 * This lock implementation doesn't use pub/sub mechanism. It can be used in large Redis clusters despite current naive
 * pub/sub implementation.
 * <p>
 * Implements a <b>non-fair</b> locking so doesn't guarantees an acquire order.
 *
 * @author Danila Varatyntsev
 */
public class RedissonSpinLock extends RedissonBaseLock {

    protected long internalLockLeaseTime;

    protected final LockOptions.BackOff backOff;

    final CommandAsyncExecutor commandExecutor;

    public RedissonSpinLock(CommandAsyncExecutor commandExecutor, String name,
                            LockOptions.BackOff backOff) {
        super(commandExecutor, name);
        this.commandExecutor = commandExecutor;
        this.internalLockLeaseTime = commandExecutor.getConnectionManager().getCfg().getLockWatchdogTimeout();
        this.backOff = backOff;
    }

    @Override
    public void lock() {
        try {
            lockInterruptibly(-1, null);
        } catch (InterruptedException e) {
            throw new IllegalStateException();
        }
    }

    @Override
    public void lock(long leaseTime, TimeUnit unit) {
        try {
            lockInterruptibly(leaseTime, unit);
        } catch (InterruptedException e) {
            throw new IllegalStateException();
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        lockInterruptibly(-1, null);
    }

    @Override
    public void lockInterruptibly(long leaseTime, TimeUnit unit) throws InterruptedException {
        long threadId = Thread.currentThread().getId();
        Long ttl = tryAcquire(leaseTime, unit, threadId);
        // lock acquired
        if (ttl == null) {
            return;
        }
        LockOptions.BackOffPolicy backOffPolicy = backOff.create();
        while (ttl != null) {
            long nextSleepPeriod = backOffPolicy.getNextSleepPeriod();
            Thread.sleep(nextSleepPeriod);
            ttl = tryAcquire(leaseTime, unit, threadId);
        }
    }

    private Long tryAcquire(long leaseTime, TimeUnit unit, long threadId) {
        return get(tryAcquireAsync(leaseTime, unit, threadId));
    }

    private <T> RFuture<Long> tryAcquireAsync(long leaseTime, TimeUnit unit, long threadId) {
        if (leaseTime > 0) {
            return tryLockInnerAsync(leaseTime, unit, threadId, RedisCommands.EVAL_LONG);
        }
        RFuture<Long> ttlRemainingFuture = tryLockInnerAsync(internalLockLeaseTime,
                TimeUnit.MILLISECONDS, threadId, RedisCommands.EVAL_LONG);
        ttlRemainingFuture.thenAccept(ttlRemaining -> {
            // lock acquired
            if (ttlRemaining == null) {
                scheduleExpirationRenewal(threadId);
            }
        });
        return ttlRemainingFuture;
    }

    @Override
    public boolean tryLock() {
        return get(tryLockAsync());
    }

    <T> RFuture<T> tryLockInnerAsync(long leaseTime, TimeUnit unit, long threadId, RedisStrictCommand<T> command) {
        internalLockLeaseTime = unit.toMillis(leaseTime);

        return evalWriteAsync(getRawName(), LongCodec.INSTANCE, command,
                "if (redis.call('exists', KEYS[1]) == 0) then " +
                        "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                        "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                        "return nil; " +
                        "end; " +
                        "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                        "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                        "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                        "return nil; " +
                        "end; " +
                        "return redis.call('pttl', KEYS[1]);",
                Collections.singletonList(getRawName()), internalLockLeaseTime, getLockName(threadId));
    }

    @Override
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        long time = unit.toMillis(waitTime);
        long current = System.currentTimeMillis();
        long threadId = Thread.currentThread().getId();
        Long ttl = tryAcquire(leaseTime, unit, threadId);
        // lock acquired
        if (ttl == null) {
            return true;
        }

        time -= System.currentTimeMillis() - current;
        if (time <= 0) {
            acquireFailed(waitTime, unit, threadId);
            return false;
        }

        LockOptions.BackOffPolicy backOffPolicy = backOff.create();
        while (true) {
            current = System.currentTimeMillis();
            Thread.sleep(backOffPolicy.getNextSleepPeriod());
            ttl = tryAcquire(leaseTime, unit, threadId);
            if (ttl == null) {
                return true;
            }
            time -= System.currentTimeMillis() - current;
            if (time <= 0) {
                acquireFailed(waitTime, unit, threadId);
                return false;
            }
        }
    }

    @Override
    public boolean tryLock(long waitTime, TimeUnit unit) throws InterruptedException {
        return tryLock(waitTime, -1, unit);
    }

    @Override
    public void unlock() {
        try {
            get(unlockAsync(Thread.currentThread().getId()));
        } catch (RedisException e) {
            if (e.getCause() instanceof IllegalMonitorStateException) {
                throw (IllegalMonitorStateException) e.getCause();
            } else {
                throw e;
            }
        }
    }

    @Override
    public boolean forceUnlock() {
        return get(forceUnlockAsync());
    }

    @Override
    public RFuture<Boolean> forceUnlockAsync() {
        cancelExpirationRenewal(null);
        return evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if (redis.call('del', KEYS[1]) == 1) then "
                        + "return 1 "
                        + "else "
                        + "return 0 "
                        + "end",
                Collections.singletonList(getRawName()));
    }


    protected RFuture<Boolean> unlockInnerAsync(long threadId) {
        return evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if (redis.call('hexists', KEYS[1], ARGV[2]) == 0) then " +
                        "return nil;" +
                        "end; " +
                        "local counter = redis.call('hincrby', KEYS[1], ARGV[2], -1); " +
                        "if (counter > 0) then " +
                        "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                        "return 0; " +
                        "else " +
                        "redis.call('del', KEYS[1]); " +
                        "return 1; " +
                        "end; " +
                        "return nil;",
                Collections.singletonList(getRawName()), internalLockLeaseTime, getLockName(threadId));
    }

    @Override
    public RFuture<Void> lockAsync() {
        return lockAsync(-1, null);
    }

    @Override
    public RFuture<Void> lockAsync(long leaseTime, TimeUnit unit) {
        long currentThreadId = Thread.currentThread().getId();
        return lockAsync(leaseTime, unit, currentThreadId);
    }

    @Override
    public RFuture<Void> lockAsync(long currentThreadId) {
        return lockAsync(-1, null, currentThreadId);
    }

    @Override
    public RFuture<Void> lockAsync(long leaseTime, TimeUnit unit, long currentThreadId) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        LockOptions.BackOffPolicy backOffPolicy = backOff.create();

        lockAsync(leaseTime, unit, currentThreadId, result, backOffPolicy);
        return new CompletableFutureWrapper<>(result);
    }

    private void lockAsync(long leaseTime, TimeUnit unit, long currentThreadId, CompletableFuture<Void> result,
                           LockOptions.BackOffPolicy backOffPolicy) {
        RFuture<Long> ttlFuture = tryAcquireAsync(leaseTime, unit, currentThreadId);
        ttlFuture.whenComplete((ttl, e) -> {
            if (e != null) {
                result.completeExceptionally(e);
                return;
            }

            // lock acquired
            if (ttl == null) {
                if (!result.complete(null)) {
                    unlockAsync(currentThreadId);
                }
                return;
            }

            long nextSleepPeriod = backOffPolicy.getNextSleepPeriod();
            commandExecutor.getConnectionManager().newTimeout(
                    timeout -> lockAsync(leaseTime, unit, currentThreadId, result, backOffPolicy),
                    nextSleepPeriod, TimeUnit.MILLISECONDS);
        });
    }

    @Override
    public RFuture<Boolean> tryLockAsync() {
        return tryLockAsync(Thread.currentThread().getId());
    }

    @Override
    public RFuture<Boolean> tryLockAsync(long threadId) {
        RFuture<Long> longRFuture = tryAcquireAsync(-1, null, threadId);
        CompletionStage<Boolean> f = longRFuture.thenApply(res -> res == null);
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Boolean> tryLockAsync(long waitTime, TimeUnit unit) {
        return tryLockAsync(waitTime, -1, unit);
    }

    @Override
    public RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit) {
        long currentThreadId = Thread.currentThread().getId();
        return tryLockAsync(waitTime, leaseTime, unit, currentThreadId);
    }

    @Override
    public RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit,
                                         long currentThreadId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        AtomicLong time = new AtomicLong(unit.toMillis(waitTime));
        LockOptions.BackOffPolicy backOffPolicy = backOff.create();

        tryLock(leaseTime, unit, currentThreadId, result, time, backOffPolicy);
        return new CompletableFutureWrapper<>(result);
    }

    private void tryLock(long leaseTime, TimeUnit unit, long currentThreadId, CompletableFuture<Boolean> result,
                         AtomicLong time, LockOptions.BackOffPolicy backOffPolicy) {
        long startTime = System.currentTimeMillis();
        RFuture<Long> ttlFuture = tryAcquireAsync(leaseTime, unit, currentThreadId);
        ttlFuture.whenComplete((ttl, e) -> {
            if (e != null) {
                result.completeExceptionally(e);
                return;
            }

            // lock acquired
            if (ttl == null) {
                if (!result.complete(true)) {
                    unlockAsync(currentThreadId);
                }
                return;
            }

            long el = System.currentTimeMillis() - startTime;
            time.addAndGet(-el);

            if (time.get() <= 0) {
                trySuccessFalse(currentThreadId, result);
                return;
            }

            long nextSleepPeriod = backOffPolicy.getNextSleepPeriod();
            commandExecutor.getConnectionManager().newTimeout(
                    timeout -> tryLock(leaseTime, unit, currentThreadId, result, time, backOffPolicy),
                    nextSleepPeriod, TimeUnit.MILLISECONDS);
        });
    }

}
