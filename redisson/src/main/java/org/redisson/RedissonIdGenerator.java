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

import org.redisson.api.RFuture;
import org.redisson.api.RIdGenerator;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonIdGenerator extends RedissonExpirable implements RIdGenerator {

    final Logger log = LoggerFactory.getLogger(getClass());

    RedissonIdGenerator(CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    private String getAllocationSizeName() {
        return suffixName(getRawName(), "allocation");
    }

    @Override
    public boolean tryInit(long value, long allocationSize) {
        return get(tryInitAsync(value, allocationSize));
    }

    @Override
    public long nextId() {
        return get(nextIdAsync());
    }

    @Override
    public RFuture<Boolean> tryInitAsync(long value, long allocationSize) {
        return commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                          "redis.call('setnx', KEYS[1], ARGV[1]); "
                        + "return redis.call('setnx', KEYS[2], ARGV[2]); ",
                Arrays.asList(getRawName(), getAllocationSizeName()), value, allocationSize);
    }

    private final AtomicLong start = new AtomicLong();
    private final AtomicLong counter = new AtomicLong();
    private final Queue<RPromise<Long>> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isWorkerActive = new AtomicBoolean();

    private void send() {
        if (!isWorkerActive.compareAndSet(false, true)
                || commandExecutor.getConnectionManager().getExecutor().isShutdown()) {
            return;
        }

        commandExecutor.getConnectionManager().getExecutor().execute(() -> {
            while (true) {
                if (queue.peek() == null) {
                    isWorkerActive.set(false);
                    if (!queue.isEmpty()) {
                        send();
                    }
                    break;
                }

                long v = counter.decrementAndGet();
                if (v >= 0) {
                    RPromise<Long> pp = queue.poll();
                    pp.trySuccess(start.incrementAndGet());
                } else {
                    try {
                        RFuture<List<Object>> future = commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_LIST,
                              "local allocationSize = redis.call('get', KEYS[2]); " +
                                    "if allocationSize == false then " +
                                        "allocationSize = 5000; " +
                                        "redis.call('set', KEYS[2], allocationSize);" +
                                    "end;" +
                                    "local value = redis.call('get', KEYS[1]); " +
                                    "if value == false then " +
                                        "redis.call('incr', KEYS[1]);" +
                                        "value = 1; " +
                                    "end; " +
                                    "redis.call('incrby', KEYS[1], allocationSize); " +
                                    "return {value, allocationSize}; ",
                            Arrays.asList(getRawName(), getAllocationSizeName()));
                        List<Object> res = get(future);

                        long value = (long) res.get(0);
                        long allocationSize = (long) res.get(1);
                        start.set(value);
                        counter.set(allocationSize);

                        RPromise<Long> pp = queue.poll();
                        counter.decrementAndGet();
                        pp.trySuccess(start.get());
                    } catch (Exception e) {
                        if (e instanceof RedissonShutdownException) {
                            break;
                        }

                        log.error(e.getMessage(), e);

                        isWorkerActive.set(false);
                        send();
                        break;
                    }
                }
            }
        });
    }

    @Override
    public RFuture<Long> nextIdAsync() {
        RPromise<Long> promise = new RedissonPromise<>();
        queue.add(promise);
        send();
        return promise;
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return deleteAsync(getRawName(), getAllocationSizeName());
    }

    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        return super.sizeInMemoryAsync(Arrays.asList(getRawName(), getAllocationSizeName()));
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return expireAsync(timeToLive, timeUnit, getRawName(), getAllocationSizeName());
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String... keys) {
        return super.expireAtAsync(timestamp, getRawName(), getAllocationSizeName());
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return clearExpireAsync(getRawName(), getAllocationSizeName());
    }

}
