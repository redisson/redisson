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

import org.redisson.api.RFuture;
import org.redisson.api.RIdGenerator;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class RedissonIdGenerator extends RedissonExpirable implements RIdGenerator {

    final Logger log = LoggerFactory.getLogger(getClass());

    private String allocationSizeName;

    RedissonIdGenerator(CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
        allocationSizeName = getAllocationSizeName(getRawName());
    }

    private String getAllocationSizeName(String name) {
        return suffixName(name, "allocation");
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
        return commandExecutor.evalWriteNoRetryAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                          "redis.call('setnx', KEYS[1], ARGV[1]); "
                        + "return redis.call('setnx', KEYS[2], ARGV[2]); ",
                Arrays.asList(getRawName(), allocationSizeName), value, allocationSize);
    }

    private final AtomicLong start = new AtomicLong();
    private final AtomicLong counter = new AtomicLong();
    private final Queue<CompletableFuture<Long>> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isWorkerActive = new AtomicBoolean();

    private void startIdRequestsHandle() {
        if (!isWorkerActive.compareAndSet(false, true)) {
            return;
        }

        handleIdRequests();
    }

    private void handleIdRequests() {
        if (getServiceManager().isShuttingDown()) {
            return;
        }

        if (queue.peek() == null) {
            isWorkerActive.set(false);
            if (!queue.isEmpty()) {
                startIdRequestsHandle();
            }
            return;
        }

        long v = counter.decrementAndGet();
        if (v >= 0) {
            CompletableFuture<Long> pp = queue.poll();
            if (pp != null) {
                pp.complete(start.incrementAndGet());
                handleIdRequests();
            } else {
                counter.incrementAndGet();
                isWorkerActive.set(false);
                if (!queue.isEmpty()) {
                    startIdRequestsHandle();
                }
            }
        } else {
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
                    Arrays.asList(getRawName(), allocationSizeName));
            future.whenComplete((res, ex) -> {
                if (ex != null) {
                    if (getServiceManager().isShuttingDown(ex)) {
                        return;
                    }

                    log.error(ex.getMessage(), ex);

                    commandExecutor.getServiceManager().newTimeout(task -> {
                        handleIdRequests();
                    }, 1, TimeUnit.SECONDS);
                    return;
                }

                long value = (long) res.get(0);
                long allocationSize = (long) res.get(1);
                start.set(value);
                counter.set(allocationSize);

                CompletableFuture<Long> pp = queue.poll();
                if (pp != null) {
                    counter.decrementAndGet();
                    pp.complete(start.get());
                }
                handleIdRequests();
            });
        }
    }

    @Override
    public RFuture<Long> nextIdAsync() {
        CompletableFuture<Long> promise = new CompletableFuture<>();
        queue.add(promise);
        startIdRequestsHandle();
        return new CompletableFutureWrapper<>(promise);
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return deleteAsync(getRawName(), allocationSizeName);
    }

    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        return super.sizeInMemoryAsync(Arrays.asList(getRawName(), allocationSizeName));
    }

    @Override
    public RFuture<Boolean> copyAsync(List<Object> keys, int database, boolean replace) {
        String newName = (String) keys.get(1);
        List<Object> kks = Arrays.asList(getRawName(), allocationSizeName,
                                         newName, getAllocationSizeName(newName));
        return super.copyAsync(kks, database, replace);
    }

    @Override
    public RFuture<Void> renameAsync(String nn) {
        String newName = mapName(nn);
        List<Object> kks = Arrays.asList(getRawName(), allocationSizeName,
                newName, getAllocationSizeName(newName));
        return renameAsync(commandExecutor, kks, () -> {
            setName(nn);
            this.allocationSizeName = getAllocationSizeName(newName);
        });
    }

    @Override
    public RFuture<Boolean> renamenxAsync(String nn) {
        String newName = mapName(nn);
        List<Object> kks = Arrays.asList(getRawName(), allocationSizeName,
                newName, getAllocationSizeName(newName));
        return renamenxAsync(commandExecutor, kks, value -> {
            if (value) {
                setName(nn);
                this.allocationSizeName = getAllocationSizeName(newName);
            }
        });
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        return super.expireAsync(timeToLive, timeUnit, param, getRawName(), allocationSizeName);
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        return super.expireAtAsync(timestamp, param, getRawName(), allocationSizeName);
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return clearExpireAsync(getRawName(), allocationSizeName);
    }

}
