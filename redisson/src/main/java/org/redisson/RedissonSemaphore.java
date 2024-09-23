/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
import org.redisson.api.RFuture;
import org.redisson.api.RSemaphore;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.pubsub.SemaphorePubSub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Distributed and concurrent implementation of {@link java.util.concurrent.Semaphore}.
 * <p>
 * Works in non-fair mode. Therefore order of acquiring is unpredictable.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonSemaphore extends RedissonExpirable implements RSemaphore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedissonSemaphore.class);

    private final SemaphorePubSub semaphorePubSub;

    public RedissonSemaphore(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        this.semaphorePubSub = getSubscribeService().getSemaphorePubSub();
    }

    String getChannelName() {
        return getChannelName(getRawName());
    }
    
    public static String getChannelName(String name) {
        return prefixName("redisson_sc", name);
    }

    @Override
    public void acquire() throws InterruptedException {
        acquire(1);
    }

    @Override
    public void acquire(int permits) throws InterruptedException {
        if (tryAcquire(permits)) {
            return;
        }

        CompletableFuture<RedissonLockEntry> future = subscribe();
        semaphorePubSub.timeout(future);
        RedissonLockEntry entry = commandExecutor.getInterrupted(future);
        try {
            while (true) {
                if (tryAcquire(permits)) {
                    return;
                }

                entry.getLatch().acquire();
            }
        } finally {
            unsubscribe(entry);
        }
//        get(acquireAsync(permits));
    }
    
    @Override
    public RFuture<Void> acquireAsync() {
        return acquireAsync(1);
    }
    
    @Override
    public RFuture<Void> acquireAsync(int permits) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        RFuture<Boolean> tryAcquireFuture = tryAcquireAsync(permits);
        tryAcquireFuture.whenComplete((res, e) -> {
            if (e != null) {
                result.completeExceptionally(e);
                return;
            }

            if (res) {
                if (!result.complete(null)) {
                    releaseAsync(permits);
                }
                return;
            }
            
            CompletableFuture<RedissonLockEntry> subscribeFuture = subscribe();
            semaphorePubSub.timeout(subscribeFuture);
            subscribeFuture.whenComplete((r, e1) -> {
                if (e1 != null) {
                    result.completeExceptionally(e1);
                    return;
                }

                acquireAsync(permits, r, result);
            });
        });
        return new CompletableFutureWrapper<>(result);
    }
    
    private void tryAcquireAsync(AtomicLong time, int permits, RedissonLockEntry entry, CompletableFuture<Boolean> result) {
        if (result.isDone()) {
            unsubscribe(entry);
            return;
        }
        
        if (time.get() <= 0) {
            unsubscribe(entry);
            result.complete(false);
            return;
        }
        
        long curr = System.currentTimeMillis();
        RFuture<Boolean> tryAcquireFuture = tryAcquireAsync(permits);
        tryAcquireFuture.whenComplete((res, e) -> {
            if (e != null) {
                unsubscribe(entry);
                result.completeExceptionally(e);
                return;
            }
            
            if (res) {
                unsubscribe(entry);
                if (!result.complete(true)) {
                    releaseAsync(permits);
                }
                return;
            }

            long el = System.currentTimeMillis() - curr;
            time.addAndGet(-el);
            
            if (time.get() <= 0) {
                unsubscribe(entry);
                result.complete(false);
                return;
            }

            // waiting for message
            long current = System.currentTimeMillis();
            if (entry.getLatch().tryAcquire()) {
                tryAcquireAsync(time, permits, entry, result);
            } else {
                AtomicBoolean executed = new AtomicBoolean();
                AtomicReference<Timeout> futureRef = new AtomicReference<>();
                Runnable listener = () -> {
                    executed.set(true);
                    if (futureRef.get() != null && !futureRef.get().cancel()) {
                        entry.getLatch().release();
                        return;
                    }
                    long elapsed = System.currentTimeMillis() - current;
                    time.addAndGet(-elapsed);

                    tryAcquireAsync(time, permits, entry, result);
                };
                entry.addListener(listener);

                long t = time.get();
                if (!executed.get()) {
                    Timeout scheduledFuture = commandExecutor.getServiceManager().newTimeout(new TimerTask() {
                        @Override
                        public void run(Timeout timeout) throws Exception {
                            if (entry.removeListener(listener)) {
                                long elapsed = System.currentTimeMillis() - current;
                                time.addAndGet(-elapsed);
                                
                                tryAcquireAsync(time, permits, entry, result);
                            }
                        }
                    }, t, TimeUnit.MILLISECONDS);
                    futureRef.set(scheduledFuture);
                }
            }
        });
    }
    
    private void acquireAsync(int permits, RedissonLockEntry entry, CompletableFuture<Void> result) {
        if (result.isDone()) {
            unsubscribe(entry);
            return;
        }

        RFuture<Boolean> tryAcquireFuture = tryAcquireAsync(permits);
        tryAcquireFuture.whenComplete((res, e) -> {
            if (e != null) {
                unsubscribe(entry);
                result.completeExceptionally(e);
                return;
            }

            if (res) {
                unsubscribe(entry);
                if (!result.complete(null)) {
                    releaseAsync(permits);
                }
                return;
            }
            
            if (entry.getLatch().tryAcquire()) {
                acquireAsync(permits, entry, result);
            } else {
                entry.addListener(() -> {
                    acquireAsync(permits, entry, result);
                });
            }
        });
    }

    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    @Override
    public boolean tryAcquire(int permits) {
        return get(tryAcquireAsync(permits));
    }
    
    @Override
    public RFuture<Boolean> tryAcquireAsync() {
        return tryAcquireAsync(1);
    }
    
    @Override
    public RFuture<Boolean> tryAcquireAsync(int permits) {
        if (permits < 0) {
            throw new IllegalArgumentException("Permits amount can't be negative");
        }
        if (permits == 0) {
            return new CompletableFutureWrapper<>(true);
        }

        return commandExecutor.getServiceManager().execute(() -> {
            RFuture<Boolean> future = tryAcquireAsync0(permits);
            return commandExecutor.handleNoSync(future, () -> releaseAsync(permits));
        });
    }

    private RFuture<Boolean> tryAcquireAsync0(int permits) {
        return commandExecutor.syncedEvalNoRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "local value = redis.call('get', KEYS[1]); " +
                  "if (value ~= false and tonumber(value) >= tonumber(ARGV[1])) then " +
                      "local val = redis.call('decrby', KEYS[1], ARGV[1]); " +
                      "return 1; " +
                  "end; " +
                  "return 0;",
                  Collections.<Object>singletonList(getRawName()), permits);
    }

    @Override
    public RFuture<Boolean> tryAcquireAsync(long waitTime, TimeUnit unit) {
        return tryAcquireAsync(1, waitTime, unit);
    }

    @Override
    public boolean tryAcquire(Duration waitTime) throws InterruptedException {
        return tryAcquire(1, waitTime);
    }

    @Override
    public boolean tryAcquire(int permits, Duration waitTime) throws InterruptedException {
        LOGGER.debug("trying to acquire, permits: {}, waitTime: {}, name: {}", permits, waitTime,  getName());

        long time = waitTime.toMillis();
        long current = System.currentTimeMillis();

        if (tryAcquire(permits)) {
            LOGGER.debug("acquired, permits: {}, waitTime: {}, name: {}", permits, waitTime, getName());
            return true;
        }

        time -= System.currentTimeMillis() - current;
        if (time <= 0) {
            LOGGER.debug("unable to acquire, permits: {}, name: {}", permits, getName());
            return false;
        }

        current = System.currentTimeMillis();
        CompletableFuture<RedissonLockEntry> future = subscribe();
        RedissonLockEntry entry;
        try {
            entry = future.get(time, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            LOGGER.error(e.getMessage(), e);
            return false;
        } catch (TimeoutException | CancellationException e) {
            LOGGER.debug("unable to subscribe for permits acquisition, permits: {}, name: {}", permits, getName());
            return false;
        }

        try {
            time -= System.currentTimeMillis() - current;
            if (time <= 0) {
                LOGGER.debug("unable to acquire, permits: {}, name: {}", permits, getName());
                return false;
            }

            while (true) {
                current = System.currentTimeMillis();
                if (tryAcquire(permits)) {
                    LOGGER.debug("acquired, permits: {}, wait-time: {}, name: {}", permits, waitTime, getName());
                    return true;
                }

                time -= System.currentTimeMillis() - current;
                if (time <= 0) {
                    LOGGER.debug("unable to acquire, permits: {}, name: {}", permits, getName());
                    return false;
                }

                // waiting for message
                current = System.currentTimeMillis();

                LOGGER.debug("wait for acquisition, permits: {}, wait-time(ms): {}, name: {}", permits, time, getName());
                entry.getLatch().tryAcquire(time, TimeUnit.MILLISECONDS);

                time -= System.currentTimeMillis() - current;
                if (time <= 0) {
                    LOGGER.debug("unable to acquire, permits: {}, name: {}", permits, getName());
                    return false;
                }
            }
        } finally {
            unsubscribe(entry);
        }
//        return get(tryAcquireAsync(permits, waitTime));
    }

    @Override
    public RFuture<Boolean> tryAcquireAsync(Duration waitTime) {
        return tryAcquireAsync(1, waitTime);
    }

    @Override
    public RFuture<Boolean> tryAcquireAsync(int permits, Duration waitTime) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        AtomicLong time = new AtomicLong(waitTime.toMillis());
        long curr = System.currentTimeMillis();
        RFuture<Boolean> tryAcquireFuture = tryAcquireAsync(permits);
        tryAcquireFuture.whenComplete((res, e) -> {
            if (e != null) {
                result.completeExceptionally(e);
                return;
            }

            if (res) {
                if (!result.complete(true)) {
                    releaseAsync(permits);
                }
                return;
            }

            long elap = System.currentTimeMillis() - curr;
            time.addAndGet(-elap);

            if (time.get() <= 0) {
                result.complete(false);
                return;
            }

            long current = System.currentTimeMillis();
            CompletableFuture<RedissonLockEntry> subscribeFuture = subscribe();
            semaphorePubSub.timeout(subscribeFuture, time.get());
            subscribeFuture.whenComplete((r, ex) -> {
                if (ex != null) {
                    result.completeExceptionally(ex);
                    return;
                }

                long elapsed = System.currentTimeMillis() - current;
                time.addAndGet(-elapsed);

                if (time.get() < 0) {
                    unsubscribe(r);
                    result.complete(false);
                    return;
                }

                tryAcquireAsync(time, permits, r, result);
            });
        });
        return new CompletableFutureWrapper<>(result);
    }

    @Override
    public boolean tryAcquire(int permits, long waitTime, TimeUnit unit) throws InterruptedException {
        return tryAcquire(permits, Duration.ofMillis(unit.toMillis(waitTime)));
    }

    @Override
    public RFuture<Boolean> tryAcquireAsync(int permits, long waitTime, TimeUnit unit) {
        return tryAcquireAsync(permits, Duration.ofMillis(unit.toMillis(waitTime)));
    }

    private CompletableFuture<RedissonLockEntry> subscribe() {
        return semaphorePubSub.subscribe(getRawName(), getChannelName());
    }

    private void unsubscribe(RedissonLockEntry entry) {
        semaphorePubSub.unsubscribe(entry, getRawName(), getChannelName());
    }

    @Override
    public boolean tryAcquire(long time, TimeUnit unit) throws InterruptedException {
        return tryAcquire(1, time, unit);
    }

    @Override
    public void release() {
        release(1);
    }

    @Override
    public void release(int permits) {
        get(releaseAsync(permits));
    }
    
    @Override
    public RFuture<Void> releaseAsync() {
        return releaseAsync(1);
    }
    
    @Override
    public RFuture<Void> releaseAsync(int permits) {
        if (permits < 0) {
            throw new IllegalArgumentException("Permits amount can't be negative");
        }
        if (permits == 0) {
            return new CompletableFutureWrapper<>((Void) null);
        }

        RFuture<Void> future = commandExecutor.syncedEvalNoRetry(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "local value = redis.call('incrby', KEYS[1], ARGV[1]); " +
                        "redis.call(ARGV[2], KEYS[2], value); ",
                Arrays.asList(getRawName(), getChannelName()), permits, getSubscribeService().getPublishCommand());
        if (LOGGER.isDebugEnabled()) {
            future.thenAccept(o -> {
                LOGGER.debug("released, permits: {}, name: {}", permits, getName());
            });
        }
        return future;
    }

    @Override
    public int drainPermits() {
        return get(drainPermitsAsync());
    }

    @Override
    public RFuture<Integer> drainPermitsAsync() {
        return commandExecutor.syncedEvalWithRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "local value = redis.call('get', KEYS[1]); " +
                "if (value == false) then " +
                    "return 0; " +
                "end; " +
                "redis.call('set', KEYS[1], 0); " +
                "return value;",
                Collections.singletonList(getRawName()));
    }

    @Override
    public int availablePermits() {
        return get(availablePermitsAsync());
    }

    @Override
    public RFuture<Integer> availablePermitsAsync() {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.GET_INTEGER, getRawName());
    }

    @Override
    public boolean trySetPermits(int permits) {
        return get(trySetPermitsAsync(permits));
    }
    
    @Override
    public RFuture<Boolean> trySetPermitsAsync(int permits) {
        RFuture<Boolean> future = commandExecutor.syncedEvalWithRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "local value = redis.call('get', KEYS[1]); " +
                        "if (value == false) then "
                          + "redis.call('set', KEYS[1], ARGV[1]); "
                          + "redis.call(ARGV[2], KEYS[2], ARGV[1]); "
                          + "return 1;"
                      + "end;"
                      + "return 0;",
                Arrays.asList(getRawName(), getChannelName()),
                permits, getSubscribeService().getPublishCommand());

        if (LOGGER.isDebugEnabled()) {
            future.thenAccept(r -> {
                if (r) {
                    LOGGER.debug("permits set, permits: {}, name: {}", permits, getName());
                } else {
                    LOGGER.debug("unable to set permits, permits: {}, name: {}", permits, getName());
                }
            });
        }
        return future;
    }

    @Override
    public boolean trySetPermits(int permits, Duration timeToLive) {
        return get(trySetPermitsAsync(permits, timeToLive));
    }

    @Override
    public RFuture<Boolean> trySetPermitsAsync(int permits, Duration timeToLive) {
        RFuture<Boolean> future = commandExecutor.syncedEvalWithRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "local value = redis.call('get', KEYS[1]); " +
                        "if (value == false) then "
                          + "redis.call('set', KEYS[1], ARGV[1], 'px', ARGV[3]); "
                          + "redis.call(ARGV[2], KEYS[2], ARGV[1]); "
                          + "return 1;"
                      + "end;"
                      + "return 0;",
                Arrays.asList(getRawName(), getChannelName()),
                permits, getSubscribeService().getPublishCommand(), timeToLive.toMillis());

        if (LOGGER.isDebugEnabled()) {
            future.thenAccept(r -> {
                if (r) {
                    LOGGER.debug("permits set, permits: {}, name: {}", permits, getName());
                } else {
                    LOGGER.debug("unable to set permits, permits: {}, name: {}", permits, getName());
                }
            });
        }
        return future;
    }

    @Override
    public void addPermits(int permits) {
        get(addPermitsAsync(permits));
    }

    @Override
    public RFuture<Void> addPermitsAsync(int permits) {
        return commandExecutor.syncedEvalWithRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "local value = redis.call('get', KEYS[1]); " +
                "if (value == false) then "
                  + "value = 0;"
              + "end;"
              + "redis.call('set', KEYS[1], value + ARGV[1]); "
              + "redis.call(ARGV[2], KEYS[2], value + ARGV[1]); ",
                Arrays.asList(getRawName(), getChannelName()),
                permits, getSubscribeService().getPublishCommand());
    }


}
