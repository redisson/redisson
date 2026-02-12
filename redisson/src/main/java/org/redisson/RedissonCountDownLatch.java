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
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RFuture;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.pubsub.CountDownLatchPubSub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Distributed alternative to the {@link java.util.concurrent.CountDownLatch}
 *
 * It has a advantage over {@link java.util.concurrent.CountDownLatch} --
 * count can be reset via {@link #trySetCount}.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCountDownLatch extends RedissonObject implements RCountDownLatch {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedissonCountDownLatch.class);

    private final CountDownLatchPubSub pubSub;

    private final String id;

    protected RedissonCountDownLatch(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        this.id = commandExecutor.getServiceManager().getId();
        this.pubSub = commandExecutor.getConnectionManager().getSubscribeService().getCountDownLatchPubSub();
    }

    @Override
    public void await() throws InterruptedException {
        if (getCount() == 0) {
            return;
        }

        CompletableFuture<RedissonCountDownLatchEntry> future = subscribe();
        RedissonCountDownLatchEntry entry = commandExecutor.getInterrupted(future);
        try {
            while (getCount() > 0) {
                // waiting for open state
                entry.getLatch().await();
            }
        } finally {
            unsubscribe(entry);
        }
    }

    private CompletableFuture<Void> await(RedissonCountDownLatchEntry entry) {
        CompletableFuture<Long> countFuture = getCountAsync().toCompletableFuture();
        return countFuture.whenComplete((r, e) -> {
            if (e != null) {
                unsubscribe(entry);
            }
        }).thenCompose(r -> {
            if (r == 0) {
                unsubscribe(entry);
                return CompletableFuture.completedFuture(null);
            }

            CompletableFuture<Void> future = new CompletableFuture<>();
            entry.addListener(() -> {
                await(entry).whenComplete((res, e) -> {
                    if (e != null) {
                        future.completeExceptionally(e);
                        return;
                    }
                    future.complete(res);
                });
            });
            return future;
        });
    }

    @Override
    public RFuture<Void> awaitAsync() {
        CompletableFuture<Long> countFuture = getCountAsync().toCompletableFuture();
        CompletableFuture<Void> f = countFuture.thenCompose(r -> {
            return subscribe();
        }).thenCompose(res -> {
            return await(res);
        });

        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public boolean await(long time, TimeUnit unit) throws InterruptedException {
        long remainTime = unit.toMillis(time);
        long current = System.currentTimeMillis();
        if (getCount() == 0) {
            return true;
        }
        CompletableFuture<RedissonCountDownLatchEntry> promise = subscribe();
        try {
            promise.toCompletableFuture().get(time, unit);
        } catch (ExecutionException e) {
            LOGGER.error(e.getMessage(), e);
            return false;
        } catch (TimeoutException | CancellationException e) {
            return false;
        }

        try {
            remainTime -= System.currentTimeMillis() - current;
            if (remainTime <= 0) {
                return false;
            }

            while (getCount() > 0) {
                if (remainTime <= 0) {
                    return false;
                }
                current = System.currentTimeMillis();
                // waiting for open state
                promise.join().getLatch().await(remainTime, TimeUnit.MILLISECONDS);

                remainTime -= System.currentTimeMillis() - current;
            }

            return true;
        } finally {
            unsubscribe(promise.join());
        }
    }

    @Override
    public RFuture<Boolean> awaitAsync(long waitTime, TimeUnit unit) {
        AtomicLong time = new AtomicLong(unit.toMillis(waitTime));
        long currentTime = System.currentTimeMillis();
        CompletableFuture<Long> countFuture = getCountAsync().toCompletableFuture();
        CompletableFuture<Boolean> f = countFuture.thenCompose(r -> {
            long el = System.currentTimeMillis() - currentTime;
            time.addAndGet(-el);

            if (time.get() <= 0) {
                return CompletableFuture.completedFuture(false);
            }

            long current = System.currentTimeMillis();
            CompletableFuture<RedissonCountDownLatchEntry> subscribeFuture = subscribe();
            pubSub.timeout(subscribeFuture, time.get());
            return subscribeFuture.thenCompose(entry -> {
                long elapsed = System.currentTimeMillis() - current;
                time.addAndGet(-elapsed);

                return await(time, entry);
            });
        });
        return new CompletableFutureWrapper<>(f);
    }

    private CompletableFuture<Boolean> await(AtomicLong time, RedissonCountDownLatchEntry entry) {
        if (time.get() <= 0) {
            unsubscribe(entry);
            return CompletableFuture.completedFuture(false);
        }

        long curr = System.currentTimeMillis();
        CompletableFuture<Long> countFuture = getCountAsync().toCompletableFuture();
        return countFuture.whenComplete((r, e) -> {
            if (e != null) {
                unsubscribe(entry);
            }
        }).thenCompose(r -> {
            if (r == 0) {
                unsubscribe(entry);
                return CompletableFuture.completedFuture(true);
            }

            long el = System.currentTimeMillis() - curr;
            time.addAndGet(-el);

            if (time.get() <= 0) {
                unsubscribe(entry);
                return CompletableFuture.completedFuture(false);
            }

            CompletableFuture<Boolean> future = new CompletableFuture<>();
            long current = System.currentTimeMillis();
            AtomicBoolean executed = new AtomicBoolean();
            AtomicReference<Timeout> futureRef = new AtomicReference<>();
            Runnable listener = () -> {
                executed.set(true);
                if (futureRef.get() != null) {
                    futureRef.get().cancel();
                }

                long elapsed = System.currentTimeMillis() - current;
                time.addAndGet(-elapsed);

                commandExecutor.transfer(await(time, entry), future);
            };
            entry.addListener(listener);

            if (!executed.get()) {
                Timeout timeoutFuture = commandExecutor.getServiceManager().newTimeout(new TimerTask() {
                    @Override
                    public void run(Timeout timeout) throws Exception {
                        if (entry.removeListener(listener)) {
                            long elapsed = System.currentTimeMillis() - current;
                            time.addAndGet(-elapsed);

                            commandExecutor.transfer(await(time, entry), future);
                        }
                    }
                }, time.get(), TimeUnit.MILLISECONDS);
                futureRef.set(timeoutFuture);
            }

            return future;
        });
    }

    private CompletableFuture<RedissonCountDownLatchEntry> subscribe() {
        return pubSub.subscribe(getEntryName(), getChannelName());
    }

    private void unsubscribe(RedissonCountDownLatchEntry entry) {
        pubSub.unsubscribe(entry, getEntryName(), getChannelName());
    }

    @Override
    public void countDown() {
        get(countDownAsync());
    }

    @Override
    public RFuture<Void> countDownAsync() {
        return commandExecutor.evalWriteNoRetryAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "local v = redis.call('decr', KEYS[1]);" +
                        "if v <= 0 then redis.call('del', KEYS[1]) end;" +
                        "if v == 0 then redis.call(ARGV[2], KEYS[2], ARGV[1]) end;",
                    Arrays.<Object>asList(getRawName(), getChannelName()),
                CountDownLatchPubSub.ZERO_COUNT_MESSAGE, getSubscribeService().getPublishCommand());
    }

    private String getEntryName() {
        return id + getRawName();
    }

    private String getChannelName() {
        return "redisson_countdownlatch__channel__{" + getRawName() + "}";
    }

    @Override
    public long getCount() {
        return get(getCountAsync());
    }

    @Override
    public RFuture<Long> getCountAsync() {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.GET_LONG, getRawName());
    }

    @Override
    public boolean trySetCount(long count) {
        return get(trySetCountAsync(count));
    }

    @Override
    public RFuture<Boolean> trySetCountAsync(long count) {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('exists', KEYS[1]) == 0 then "
                    + "redis.call('set', KEYS[1], ARGV[2]); "
                    + "redis.call(ARGV[3], KEYS[2], ARGV[1]); "
                    + "return 1 "
                + "else "
                    + "return 0 "
                + "end",
                Arrays.asList(getRawName(), getChannelName()),
                CountDownLatchPubSub.NEW_COUNT_MESSAGE, count, getSubscribeService().getPublishCommand());
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('del', KEYS[1]) == 1 then "
                    + "redis.call(ARGV[2], KEYS[2], ARGV[1]); "
                    + "return 1 "
                + "else "
                    + "return 0 "
                + "end",
                Arrays.asList(getRawName(), getChannelName()),
                CountDownLatchPubSub.NEW_COUNT_MESSAGE, getSubscribeService().getPublishCommand());
    }

}
