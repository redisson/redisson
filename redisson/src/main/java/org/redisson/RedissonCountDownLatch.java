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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RCountDownLatch;
import org.redisson.api.RFuture;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
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

    private final UUID id;

    protected RedissonCountDownLatch(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        this.id = commandExecutor.getConnectionManager().getId();
        this.pubSub = commandExecutor.getConnectionManager().getSubscribeService().getCountDownLatchPubSub();
    }

    public void await() throws InterruptedException {
        RFuture<RedissonCountDownLatchEntry> future = subscribe();
        try {
            commandExecutor.syncSubscription(future);

            while (getCount() > 0) {
                // waiting for open state
                RedissonCountDownLatchEntry entry = getEntry();
                if (entry != null) {
                    entry.getLatch().await();
                }
            }
        } finally {
            unsubscribe(future);
        }
    }

    @Override
    public boolean await(long time, TimeUnit unit) throws InterruptedException {
        long remainTime = unit.toMillis(time);
        long current = System.currentTimeMillis();
        RFuture<RedissonCountDownLatchEntry> promise = subscribe();
        if (!await(promise, time, unit)) {
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
                RedissonCountDownLatchEntry entry = getEntry();
                if (entry != null) {
                    entry.getLatch().await(remainTime, TimeUnit.MILLISECONDS);
                }

                remainTime -= System.currentTimeMillis() - current;
            }

            return true;
        } finally {
            unsubscribe(promise);
        }
    }

    private RedissonCountDownLatchEntry getEntry() {
        return pubSub.getEntry(getEntryName());
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
