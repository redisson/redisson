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
/**
 * Copyright (C) 2011 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.redisson;

import io.netty.buffer.ByteBuf;
import org.redisson.api.RBitSetAsync;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RFuture;
import org.redisson.client.RedisException;
import org.redisson.client.codec.*;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.VoidReplayConvertor;
import org.redisson.client.protocol.decoder.ObjectMapReplayDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.Hash;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Bloom filter based on Highway 128-bit hash.
 *
 * @author Nikita Koksharov
 *
 * @param <T> type of object
 */
public class RedissonBloomFilter<T> extends RedissonExpirable implements RBloomFilter<T> {

    private volatile long size;
    private volatile int hashIterations;

    private final CommandAsyncExecutor commandExecutor;
    private String configName;

    protected RedissonBloomFilter(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        this.commandExecutor = commandExecutor;
        this.configName = suffixName(getRawName(), "config");
    }

    protected RedissonBloomFilter(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        this.commandExecutor = commandExecutor;
        this.configName = suffixName(getRawName(), "config");
    }

    private int optimalNumOfHashFunctions(long n, long m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
      }

    private long optimalNumOfBits(long n, double p) {
        if (p == 0) {
            p = Double.MIN_VALUE;
        }
        return (long) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }
    
    private long[] hash(Object object) {
        ByteBuf state = encode(object);
        try {
            return Hash.hash128(state);
        } finally {
            state.release();
        }
    }

    @Override
    public boolean add(T object) {
        return add(Arrays.asList(object)) > 0;
    }

    @Override
    public long add(Collection<T> objects) {
        if (size == 0) {
            readConfig();
        }

        List<Long> allIndexes = index(objects);

        List<Object> params = new ArrayList<>();
        params.add(size);
        params.add(hashIterations);
        int s = allIndexes.size() / objects.size();
        params.add(s);
        params.addAll(allIndexes);

        return get(commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_LONG,
                  "local size = redis.call('hget', KEYS[1], 'size');" +
                        "local hashIterations = redis.call('hget', KEYS[1], 'hashIterations');" +
                        "assert(size == ARGV[1] and hashIterations == ARGV[2], 'Bloom filter config has been changed')" +

                        "local k = 0;" +
                        "local c = 0;" +
                        "for i = 4, #ARGV, 1 do " +
                            "local r = redis.call('setbit', KEYS[2], ARGV[i], 1); " +
                            "if r == 0 then " +
                                "k = k + 1;" +
                            "end; " +
                            "if ((i - 4) + 1) % ARGV[3] == 0 then " +
                                "if k > 0 then " +
                                    "c = c + 1;" +
                                "end; " +
                                "k = 0; " +
                            "end; " +
                        "end; " +
                        "return c;",
                Arrays.asList(configName, getRawName()),
                params.toArray()));
    }

    private long[] hash(long hash1, long hash2, int iterations, long size) {
        long[] indexes = new long[iterations];
        long hash = hash1;
        for (int i = 0; i < iterations; i++) {
            indexes[i] = (hash & Long.MAX_VALUE) % size;
            if (i % 2 == 0) {
                hash += hash2;
            } else {
                hash += hash1;
            }
        }
        return indexes;
    }

    @Override
    public long contains(Collection<T> objects) {
        if (size == 0) {
            readConfig();
        }

        List<Long> allIndexes = index(objects);

        List<Object> params = new ArrayList<>();
        params.add(size);
        params.add(hashIterations);
        params.add(objects.size());
        params.addAll(allIndexes);

        return get(commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_LONG,
                  "local size = redis.call('hget', KEYS[1], 'size');" +
                        "local hashIterations = redis.call('hget', KEYS[1], 'hashIterations');" +
                        "assert(size == ARGV[1] and hashIterations == ARGV[2], 'Bloom filter config has been changed')" +

                        "local k = 0;" +
                        "local c = 0;" +
                        "local cc = (#ARGV - 3) / ARGV[3];" +
                        "for i = 4, #ARGV, 1 do " +
                            "local r = redis.call('getbit', KEYS[2], ARGV[i]); " +
                            "if r == 0 then " +
                                "k = k + 1;" +
                            "end; " +
                            "if ((i - 4) + 1) % cc == 0 then " +
                                "if k > 0 then " +
                                    "c = c + 1;" +
                                "end; " +
                                "k = 0; " +
                            "end; " +
                        "end; " +
                        "return ARGV[3] - c;",
                Arrays.asList(configName, getRawName()),
                params.toArray()));
    }

    private List<Long> index(Collection<T> objects) {
        List<Long> allIndexes = new LinkedList<>();
        for (T object : objects) {
            long[] hashes = hash(object);
            long[] indexes = hash(hashes[0], hashes[1], hashIterations, size);
            allIndexes.addAll(Arrays.stream(indexes).boxed().collect(Collectors.toList()));
        }
        return allIndexes;
    }

    @Override
    public boolean contains(T object) {
        return contains(Arrays.asList(object)) > 0;
    }

    protected RBitSetAsync createBitSet(CommandBatchService executorService) {
        return new RedissonBitSet(executorService, getName());
    }

    @Override
    public long count() {
        CommandBatchService executorService = new CommandBatchService(commandExecutor);
        RFuture<Map<String, String>> configFuture = executorService.readAsync(configName, StringCodec.INSTANCE,
                new RedisCommand<Map<Object, Object>>("HGETALL", new ObjectMapReplayDecoder()), configName);
        RBitSetAsync bs = createBitSet(executorService);
        RFuture<Long> cardinalityFuture = bs.cardinalityAsync();
        executorService.execute();

        readConfig(commandExecutor.getNow(configFuture.toCompletableFuture()));

        return Math.round(-size / ((double) hashIterations) * Math.log(1 - commandExecutor.getNow(cardinalityFuture.toCompletableFuture()) / ((double) size)));
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return deleteAsync(getRawName(), configName);
    }

    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        List<Object> keys = Arrays.<Object>asList(getRawName(), configName);
        return super.sizeInMemoryAsync(keys);
    }
    
    private void readConfig() {
        RFuture<Map<String, String>> future = commandExecutor.readAsync(configName, StringCodec.INSTANCE,
                new RedisCommand<Map<Object, Object>>("HGETALL", new ObjectMapReplayDecoder()), configName);
        Map<String, String> config = commandExecutor.get(future);

        readConfig(config);
    }

    private void readConfig(Map<String, String> config) {
        if (config.get("hashIterations") == null
                || config.get("size") == null) {
            throw new IllegalStateException("Bloom filter is not initialized!");
        }
        size = Long.valueOf(config.get("size"));
        hashIterations = Integer.valueOf(config.get("hashIterations"));
    }

    protected long getMaxSize() {
        return Integer.MAX_VALUE*2L;
    }
    
    @Override
    public boolean tryInit(long expectedInsertions, double falseProbability) {
        if (falseProbability > 1) {
            throw new IllegalArgumentException("Bloom filter false probability can't be greater than 1");
        }
        if (falseProbability < 0) {
            throw new IllegalArgumentException("Bloom filter false probability can't be negative");
        }

        size = optimalNumOfBits(expectedInsertions, falseProbability);
        if (size == 0) {
            throw new IllegalArgumentException("Bloom filter calculated size is " + size);
        }
        if (size > getMaxSize()) {
            throw new IllegalArgumentException("Bloom filter size can't be greater than " + getMaxSize() + ". But calculated size is " + size);
        }
        hashIterations = optimalNumOfHashFunctions(expectedInsertions, size);

        CommandBatchService executorService = new CommandBatchService(commandExecutor);
        executorService.evalReadAsync(configName, codec, RedisCommands.EVAL_VOID,
                "local size = redis.call('hget', KEYS[1], 'size');" +
                        "local hashIterations = redis.call('hget', KEYS[1], 'hashIterations');" +
                        "assert(size == false and hashIterations == false, 'Bloom filter config has been changed')",
                        Arrays.<Object>asList(configName), size, hashIterations);
        executorService.writeAsync(configName, StringCodec.INSTANCE,
                                                new RedisCommand<Void>("HMSET", new VoidReplayConvertor()), configName,
                "size", size, "hashIterations", hashIterations,
                "expectedInsertions", expectedInsertions, "falseProbability", BigDecimal.valueOf(falseProbability).toPlainString());
        try {
            executorService.execute();
        } catch (RedisException e) {
            if (e.getMessage() == null || !e.getMessage().contains("Bloom filter config has been changed")) {
                throw e;
            }
            readConfig();
            return false;
        }

        return true;
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        return super.expireAsync(timeToLive, timeUnit, param, getRawName(), configName);
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        return super.expireAtAsync(timestamp, param, getRawName(), configName);
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return clearExpireAsync(getRawName(), configName);
    }
    
    @Override
    public long getExpectedInsertions() {
        Long result = get(commandExecutor.readAsync(configName, LongCodec.INSTANCE, RedisCommands.HGET, configName, "expectedInsertions"));
        return check(result);
    }

    @Override
    public double getFalseProbability() {
        Double result = get(commandExecutor.readAsync(configName, DoubleCodec.INSTANCE, RedisCommands.HGET, configName, "falseProbability"));
        return check(result);
    }

    @Override
    public long getSize() {
        Long result = get(commandExecutor.readAsync(configName, LongCodec.INSTANCE, RedisCommands.HGET, configName, "size"));
        return check(result);
    }

    @Override
    public int getHashIterations() {
        Integer result = get(commandExecutor.readAsync(configName, IntegerCodec.INSTANCE, RedisCommands.HGET, configName, "hashIterations"));
        return check(result);
    }

    @Override
    public RFuture<Boolean> isExistsAsync() {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.EXISTS, getRawName(), configName);
    }

    @Override
    public RFuture<Void> renameAsync(String newName) {
        String newConfigName = suffixName(newName, "config");
        RFuture<Void> future = commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_VOID,
                     "if redis.call('exists', KEYS[1]) == 1 then " +
                              "redis.call('rename', KEYS[1], ARGV[1]); " +
                          "end; " +
                          "return redis.call('rename', KEYS[2], ARGV[2]); ",
                Arrays.<Object>asList(getRawName(), configName), newName, newConfigName);
        CompletionStage<Void> f = future.thenApply(value -> {
            setName(newName);
            this.configName = newConfigName;
            return value;
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Boolean> renamenxAsync(String newName) {
        String newConfigName = suffixName(newName, "config");
        RFuture<Boolean> future = commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local r = redis.call('renamenx', KEYS[1], ARGV[1]); "
                        + "if r == 0 then "
                        + "  return 0; "
                        + "else  "
                        + "  return redis.call('renamenx', KEYS[2], ARGV[2]); "
                        + "end; ",
                Arrays.asList(getRawName(), configName), newName, newConfigName);
        CompletionStage<Boolean> f = future.thenApply(value -> {
            if (value) {
                setName(newName);
                this.configName = newConfigName;
            }
            return value;
        });
        return new CompletableFutureWrapper<>(f);
    }

    private <V> V check(V result) {
        if (result == null) {
            throw new IllegalStateException("Bloom filter is not initialized!");
        }
        return result;
    }

}
