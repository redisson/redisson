/**
 * Copyright 2016 Nikita Koksharov
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

import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RCountDownLatch;
import org.redisson.pubsub.CountDownLatchPubSub;

import io.netty.util.concurrent.Future;

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

    public static final Long zeroCountMessage = 0L;
    public static final Long newCountMessage = 1L;

    private static final CountDownLatchPubSub PUBSUB = new CountDownLatchPubSub();

    private final UUID id;

    protected RedissonCountDownLatch(CommandAsyncExecutor commandExecutor, String name, UUID id) {
        super(commandExecutor, name);
        this.id = id;
    }

    public void await() throws InterruptedException {
        Future<RedissonCountDownLatchEntry> promise = subscribe();
        try {
            get(promise);

            while (getCount() > 0) {
                // waiting for open state
                RedissonCountDownLatchEntry entry = getEntry();
                if (entry != null) {
                    entry.getLatch().await();
                }
            }
        } finally {
            unsubscribe(promise);
        }
    }

    @Override
    public boolean await(long time, TimeUnit unit) throws InterruptedException {
        Future<RedissonCountDownLatchEntry> promise = subscribe();
        try {
            if (!await(promise, time, unit)) {
                return false;
            }

            time = unit.toMillis(time);
            while (getCount() > 0) {
                if (time <= 0) {
                    return false;
                }
                long current = System.currentTimeMillis();
                // waiting for open state
                RedissonCountDownLatchEntry entry = getEntry();
                if (entry != null) {
                    entry.getLatch().await(time, TimeUnit.MILLISECONDS);
                }

                long elapsed = System.currentTimeMillis() - current;
                time = time - elapsed;
            }

            return true;
        } finally {
            unsubscribe(promise);
        }
    }

    private RedissonCountDownLatchEntry getEntry() {
        return PUBSUB.getEntry(getEntryName());
    }

    private Future<RedissonCountDownLatchEntry> subscribe() {
        return PUBSUB.subscribe(getEntryName(), getChannelName(), commandExecutor.getConnectionManager());
    }

    private void unsubscribe(Future<RedissonCountDownLatchEntry> future) {
        PUBSUB.unsubscribe(future.getNow(), getEntryName(), getChannelName(), commandExecutor.getConnectionManager());
    }

    @Override
    public void countDown() {
        get(countDownAsync());
    }

    @Override
    public Future<Void> countDownAsync() {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "local v = redis.call('decr', KEYS[1]);" +
                        "if v <= 0 then redis.call('del', KEYS[1]) end;" +
                        "if v == 0 then redis.call('publish', KEYS[2], ARGV[1]) end;",
                    Arrays.<Object>asList(getName(), getChannelName()), zeroCountMessage);
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
    public Future<Long> getCountAsync() {
        return commandExecutor.writeAsync(getName(), LongCodec.INSTANCE, RedisCommands.GET_LONG, getName());
    }

    @Override
    public boolean trySetCount(long count) {
        return get(trySetCountAsync(count));
    }

    @Override
    public Future<Boolean> trySetCountAsync(long count) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('exists', KEYS[1]) == 0 then "
                    + "redis.call('set', KEYS[1], ARGV[2]); "
                    + "redis.call('publish', KEYS[2], ARGV[1]); "
                    + "return 1 "
                + "else "
                    + "return 0 "
                + "end",
                Arrays.<Object>asList(getName(), getChannelName()), newCountMessage, count);
    }

    @Override
    public Future<Boolean> deleteAsync() {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('del', KEYS[1]) == 1 then "
                    + "redis.call('publish', KEYS[2], ARGV[1]); "
                    + "return 1 "
                + "else "
                    + "return 0 "
                + "end",
                Arrays.<Object>asList(getName(), getChannelName()), newCountMessage);
    }

}
