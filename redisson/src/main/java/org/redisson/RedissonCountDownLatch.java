/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RFuture;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.pubsub.CountDownLatchPubSub;

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

    private final CountDownLatchPubSub pubSub;

    private final String id;

    protected RedissonCountDownLatch(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        this.id = commandExecutor.getConnectionManager().getId();
        this.pubSub = commandExecutor.getConnectionManager().getSubscribeService().getCountDownLatchPubSub();
    }

    @Override
    public void await() throws InterruptedException {
        if (getCount() == 0) {
            return;
        }

        RFuture<RedissonCountDownLatchEntry> future = subscribe();
        try {
            commandExecutor.syncSubscriptionInterrupted(future);

            while (getCount() > 0) {
                // waiting for open state
                future.getNow().getLatch().await();
            }
        } finally {
            unsubscribe(future);
        }
    }

    @Override
    public RFuture<Void> awaitAsync() {
        RPromise<Void> result = new RedissonPromise<>();
        RFuture<Long> countFuture = getCountAsync();
        countFuture.onComplete((r, e) -> {
            if (e != null) {
                result.tryFailure(e);
                return;
            }

            RFuture<RedissonCountDownLatchEntry> subscribeFuture = subscribe();
            subscribeFuture.onComplete((res, ex) -> {
                if (ex != null) {
                    result.tryFailure(ex);
                    return;
                }

                await(result, subscribeFuture);
            });
        });
        return result;
    }

    private void await(RPromise<Void> result, RFuture<RedissonCountDownLatchEntry> subscribeFuture) {
        if (result.isDone()) {
            unsubscribe(subscribeFuture);
            return;
        }

        RFuture<Long> countFuture = getCountAsync();
        countFuture.onComplete((r, e) -> {
            if (e != null) {
                unsubscribe(subscribeFuture);
                result.tryFailure(e);
                return;
            }

            if (r == 0) {
                unsubscribe(subscribeFuture);
                result.trySuccess(null);
                return;
            }

            subscribeFuture.getNow().addListener(() -> {
                await(result, subscribeFuture);
            });
        });
    }

    @Override
    public boolean await(long time, TimeUnit unit) throws InterruptedException {
        long remainTime = unit.toMillis(time);
        long current = System.currentTimeMillis();
        if (getCount() == 0) {
            return true;
        }
        RFuture<RedissonCountDownLatchEntry> promise = subscribe();
        if (!promise.await(time, unit)) {
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
                promise.getNow().getLatch().await(remainTime, TimeUnit.MILLISECONDS);

                remainTime -= System.currentTimeMillis() - current;
            }

            return true;
        } finally {
            unsubscribe(promise);
        }
    }

    @Override
    public RFuture<Boolean> awaitAsync(long waitTime, TimeUnit unit) {
        RPromise<Boolean> result = new RedissonPromise<>();

        AtomicLong time = new AtomicLong(unit.toMillis(waitTime));
        long currentTime = System.currentTimeMillis();
        RFuture<Long> countFuture = getCountAsync();
        countFuture.onComplete((r, e) -> {
            if (e != null) {
                result.tryFailure(e);
                return;
            }

            long el = System.currentTimeMillis() - currentTime;
            time.addAndGet(-el);

            if (time.get() <= 0) {
                result.trySuccess(false);
                return;
            }

            long current = System.currentTimeMillis();
            AtomicReference<Timeout> futureRef = new AtomicReference<>();
            RFuture<RedissonCountDownLatchEntry> subscribeFuture = subscribe();
            subscribeFuture.onComplete((res, ex) -> {
                if (ex != null) {
                    result.tryFailure(ex);
                    return;
                }

                if (futureRef.get() != null) {
                    futureRef.get().cancel();
                }

                long elapsed = System.currentTimeMillis() - current;
                time.addAndGet(-elapsed);

                await(time, result, subscribeFuture);
            });
        });
        return result;
    }

    private void await(AtomicLong time, RPromise<Boolean> result, RFuture<RedissonCountDownLatchEntry> subscribeFuture) {
        if (result.isDone()) {
            unsubscribe(subscribeFuture);
            return;
        }

        if (time.get() <= 0) {
            unsubscribe(subscribeFuture);
            result.trySuccess(false);
            return;
        }

        long curr = System.currentTimeMillis();
        RFuture<Long> countFuture = getCountAsync();
        countFuture.onComplete((r, e) -> {
            if (e != null) {
                unsubscribe(subscribeFuture);
                result.tryFailure(e);
                return;
            }

            if (r == 0) {
                unsubscribe(subscribeFuture);
                result.trySuccess(true);
                return;
            }

            long el = System.currentTimeMillis() - curr;
            time.addAndGet(-el);

            if (time.get() <= 0) {
                unsubscribe(subscribeFuture);
                result.trySuccess(false);
                return;
            }

            long current = System.currentTimeMillis();
            AtomicBoolean executed = new AtomicBoolean();
            RedissonCountDownLatchEntry entry = subscribeFuture.getNow();
            AtomicReference<Timeout> futureRef = new AtomicReference<>();
            Runnable listener = () -> {
                executed.set(true);
                if (futureRef.get() != null) {
                    futureRef.get().cancel();
                }

                long elapsed = System.currentTimeMillis() - current;
                time.addAndGet(-elapsed);

                await(time, result, subscribeFuture);
            };
            entry.addListener(listener);

            if (!executed.get()) {
                Timeout timeoutFuture = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                    @Override
                    public void run(Timeout timeout) throws Exception {
                        if (entry.removeListener(listener)) {
                            long elapsed = System.currentTimeMillis() - current;
                            time.addAndGet(-elapsed);

                            await(time, result, subscribeFuture);
                        }
                    }
                }, time.get(), TimeUnit.MILLISECONDS);
                futureRef.set(timeoutFuture);
            }
        });
    }

    private RFuture<RedissonCountDownLatchEntry> subscribe() {
        return pubSub.subscribe(getEntryName(), getChannelName());
    }

    private void unsubscribe(RFuture<RedissonCountDownLatchEntry> future) {
        pubSub.unsubscribe(future.getNow(), getEntryName(), getChannelName());
    }

    @Override
    public void countDown() {
        get(countDownAsync());
    }

    @Override
    public RFuture<Void> countDownAsync() {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "local v = redis.call('decr', KEYS[1]);" +
                        "if v <= 0 then redis.call('del', KEYS[1]) end;" +
                        "if v == 0 then redis.call('publish', KEYS[2], ARGV[1]) end;",
                    Arrays.<Object>asList(getName(), getChannelName()), CountDownLatchPubSub.ZERO_COUNT_MESSAGE);
    }

    private String getEntryName() {
        return id + getName();
    }

    private String getChannelName() {
        return "redisson_countdownlatch__channel__{" + getName() + "}";
    }

    @Override
    public long getCount() {
        return get(getCountAsync());
    }

    @Override
    public RFuture<Long> getCountAsync() {
        return commandExecutor.writeAsync(getName(), LongCodec.INSTANCE, RedisCommands.GET_LONG, getName());
    }

    @Override
    public boolean trySetCount(long count) {
        return get(trySetCountAsync(count));
    }

    @Override
    public RFuture<Boolean> trySetCountAsync(long count) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('exists', KEYS[1]) == 0 then "
                    + "redis.call('set', KEYS[1], ARGV[2]); "
                    + "redis.call('publish', KEYS[2], ARGV[1]); "
                    + "return 1 "
                + "else "
                    + "return 0 "
                + "end",
                Arrays.<Object>asList(getName(), getChannelName()), CountDownLatchPubSub.NEW_COUNT_MESSAGE, count);
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('del', KEYS[1]) == 1 then "
                    + "redis.call('publish', KEYS[2], ARGV[1]); "
                    + "return 1 "
                + "else "
                    + "return 0 "
                + "end",
                Arrays.<Object>asList(getName(), getChannelName()), CountDownLatchPubSub.NEW_COUNT_MESSAGE);
    }

}
