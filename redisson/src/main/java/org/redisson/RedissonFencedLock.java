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

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.redisson.api.RFencedLock;
import org.redisson.api.RFuture;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis based implementation of Fenced Lock with reentrancy support.
 * <p>
 * Each lock acquisition increases fencing token. It should be
 * checked if it's greater or equal with the previous one by
 * the service guarded by this lock and reject operation if condition is false.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonFencedLock extends RedissonLock implements RFencedLock {

    private final String tokenName;

    public RedissonFencedLock(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        tokenName = prefixName("redisson_lock_token", getRawName());
    }

    @Override
    public Long getToken() {
        return get(getTokenAsync());
    }

    @Override
    public RFuture<Long> getTokenAsync() {
        return commandExecutor.writeAsync(tokenName, StringCodec.INSTANCE, RedisCommands.GET_LONG, tokenName);
    }

    @Override
    public Long lockAndGetToken() {
        return get(lockAndGetTokenAsync());
    }

    public RFuture<Long> lockAndGetTokenAsync() {
        return tryLockAndGetTokenAsync(-1, -1, null);
    }

    @Override
    public Long lockAndGetToken(long leaseTime, TimeUnit unit) {
        return get(lockAndGetTokenAsync());
    }

    public RFuture<Long> lockAndGetTokenAsync(long leaseTime, TimeUnit unit) {
        return tryLockAndGetTokenAsync(-1, leaseTime, unit);
    }

    private <T> RFuture<List<Long>> tryAcquireAsync(long waitTime, long leaseTime, TimeUnit unit, long threadId) {
        RFuture<List<Long>> ttlRemainingFuture;
        if (leaseTime > 0) {
            ttlRemainingFuture = tryLockInnerAsync(leaseTime, unit, threadId);
        } else {
            ttlRemainingFuture = tryLockInnerAsync(internalLockLeaseTime, TimeUnit.MILLISECONDS, threadId);
        }
        CompletionStage<List<Long>> f = ttlRemainingFuture.thenApply(res -> {
            Long ttl = res.get(0);
            // lock acquired
            if (ttl == -1) {
                if (leaseTime > 0) {
                    internalLockLeaseTime = unit.toMillis(leaseTime);
                } else {
                    scheduleExpirationRenewal(threadId);
                }
            }
            return res;
        });
        return new CompletableFutureWrapper<>(f);
    }

    RFuture<List<Long>> tryLockInnerAsync(long leaseTime, TimeUnit unit, long threadId) {
        return commandExecutor.syncedEvalNoRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_LONG_LIST,
                "if (redis.call('exists', KEYS[1]) == 0 " +
                        "or (redis.call('hexists', KEYS[1], ARGV[2]) == 1)) then " +
                            "local token = redis.call('incr', KEYS[2]);" +
                            "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                            "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                            "return {-1, token}; " +
                        "end; " +
                        "return {redis.call('pttl', KEYS[1]), -1};",
                Arrays.asList(getRawName(), tokenName),
                unit.toMillis(leaseTime), getLockName(threadId));
    }

    @Override
    public Long tryLockAndGetToken() {
        return get(tryLockAndGetTokenAsync());
    }

    public RFuture<Long> tryLockAndGetTokenAsync() {
        return tryLockAndGetTokenAsync(-1, null);
    }

    @Override
    public Long tryLockAndGetToken(long waitTime, long leaseTime, TimeUnit unit) {
        return get(tryLockAndGetTokenAsync(waitTime, leaseTime, unit));
    }

    public RFuture<Long> tryLockAndGetTokenAsync(long waitTime, long leaseTime, TimeUnit unit) {
        return tryLockAndGetTokenAsync(waitTime, leaseTime, unit, Thread.currentThread().getId());
    }

    public Long tryLockAndGetToken(long waitTime, TimeUnit unit) {
        return get(tryLockAndGetTokenAsync(waitTime, unit));
    }

    public RFuture<Long> tryLockAndGetTokenAsync(long waitTime, TimeUnit unit) {
        return tryLockAndGetTokenAsync(waitTime, -1, unit);
    }

    public RFuture<Long> tryLockAndGetTokenAsync(long waitTime, long leaseTime, TimeUnit unit,
                                                 long currentThreadId) {
        CompletableFuture<Long> result = new CompletableFuture<>();

        AtomicLong time;
        if (waitTime < 0) {
            time = new AtomicLong(Long.MAX_VALUE);
        } else {
            time = new AtomicLong(unit.toMillis(waitTime));
        }
        long currentTime = System.currentTimeMillis();
        RFuture<List<Long>> ttlFuture = tryAcquireAsync(waitTime, leaseTime, unit, currentThreadId);
        ttlFuture.whenComplete((res, e) -> {
            if (e != null) {
                result.completeExceptionally(e);
                return;
            }

            Long ttl = res.get(0);
            // lock acquired
            if (ttl == -1) {
                if (!result.complete(res.get(1))) {
                    unlockAsync(currentThreadId);
                }
                return;
            }

            long el = System.currentTimeMillis() - currentTime;
            time.addAndGet(-el);

            if (time.get() <= 0) {
                result.complete(null);
                return;
            }

            long current = System.currentTimeMillis();
            AtomicReference<Timeout> futureRef = new AtomicReference<>();
            CompletableFuture<RedissonLockEntry> subscribeFuture = subscribe(currentThreadId);
            pubSub.timeout(subscribeFuture, time.get());
            subscribeFuture.whenComplete((r, ex) -> {
                if (ex != null) {
                    result.completeExceptionally(ex);
                    return;
                }

                if (futureRef.get() != null) {
                    futureRef.get().cancel();
                }

                long elapsed = System.currentTimeMillis() - current;
                time.addAndGet(-elapsed);

                tryLockAsync(time, waitTime, leaseTime, unit, r, result, currentThreadId);
            });
            if (!subscribeFuture.isDone()) {
                Timeout scheduledFuture = commandExecutor.getServiceManager().newTimeout(new TimerTask() {
                    @Override
                    public void run(Timeout timeout) throws Exception {
                        if (!subscribeFuture.isDone()) {
                            subscribeFuture.cancel(false);
                            result.complete(null);
                        }
                    }
                }, time.get(), TimeUnit.MILLISECONDS);
                futureRef.set(scheduledFuture);
            }
        });


        return new CompletableFutureWrapper<>(result);
    }

    private void tryLockAsync(AtomicLong time, long waitTime, long leaseTime, TimeUnit unit,
                              RedissonLockEntry entry, CompletableFuture<Long> result, long currentThreadId) {
        if (result.isDone()) {
            unsubscribe(entry, currentThreadId);
            return;
        }

        if (time.get() <= 0) {
            unsubscribe(entry, currentThreadId);
            result.complete(null);
            return;
        }

        long curr = System.currentTimeMillis();
        RFuture<List<Long>> ttlFuture = tryAcquireAsync(waitTime, leaseTime, unit, currentThreadId);
        ttlFuture.whenComplete((res, e) -> {
            if (e != null) {
                unsubscribe(entry, currentThreadId);
                result.completeExceptionally(e);
                return;
            }

            Long ttl = res.get(0);
            // lock acquired
            if (ttl == -1) {
                unsubscribe(entry, currentThreadId);
                if (!result.complete(res.get(1))) {
                    unlockAsync(currentThreadId);
                }
                return;
            }

            long el = System.currentTimeMillis() - curr;
            time.addAndGet(-el);

            if (time.get() <= 0) {
                unsubscribe(entry, currentThreadId);
                result.complete(null);
                return;
            }

            // waiting for message
            long current = System.currentTimeMillis();
            if (entry.getLatch().tryAcquire()) {
                tryLockAsync(time, waitTime, leaseTime, unit, entry, result, currentThreadId);
            } else {
                AtomicBoolean executed = new AtomicBoolean();
                AtomicReference<Timeout> futureRef = new AtomicReference<>();
                Runnable listener = () -> {
                    executed.set(true);
                    if (futureRef.get() != null) {
                        futureRef.get().cancel();
                    }

                    long elapsed = System.currentTimeMillis() - current;
                    time.addAndGet(-elapsed);

                    tryLockAsync(time, waitTime, leaseTime, unit, entry, result, currentThreadId);
                };
                entry.addListener(listener);

                long t = time.get();
                if (ttl < time.get()) {
                    t = ttl;
                }
                if (!executed.get()) {
                    Timeout scheduledFuture = commandExecutor.getServiceManager().newTimeout(timeout -> {
                        if (entry.removeListener(listener)) {
                            long elapsed = System.currentTimeMillis() - current;
                            time.addAndGet(-elapsed);

                            tryLockAsync(time, waitTime, leaseTime, unit, entry, result, currentThreadId);
                        }
                    }, t, TimeUnit.MILLISECONDS);
                    futureRef.set(scheduledFuture);
                }
            }
        });
    }

    @Override
    <T> RFuture<T> tryLockInnerAsync(long waitTime, long leaseTime, TimeUnit unit, long threadId, RedisStrictCommand<T> command) {
        return commandExecutor.syncedEvalNoRetry(getRawName(), LongCodec.INSTANCE, command,
                "if ((redis.call('exists', KEYS[1]) == 0) " +
                        "or (redis.call('hexists', KEYS[1], ARGV[2]) == 1)) then " +
                            "redis.call('incr', KEYS[2]);" +
                            "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                            "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                            "return nil; " +
                        "end; " +
                        "return redis.call('pttl', KEYS[1]);",
                Arrays.asList(getRawName(), tokenName),
                unit.toMillis(leaseTime), getLockName(threadId));
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return deleteAsync(getRawName(), tokenName);
    }

    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        List<Object> keys = Arrays.asList(getRawName(), tokenName);
        return super.sizeInMemoryAsync(keys);
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        return super.expireAsync(timeToLive, timeUnit, param, getRawName(), tokenName);
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        return super.expireAtAsync(timestamp, param, getRawName(), tokenName);
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return clearExpireAsync(getRawName(), tokenName);
    }

}
