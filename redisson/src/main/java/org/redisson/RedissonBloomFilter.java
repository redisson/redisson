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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBitSetAsync;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RFuture;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.VoidReplayConvertor;
import org.redisson.client.protocol.decoder.ObjectMapReplayDecoder;
import org.redisson.command.CommandBatchService;
import org.redisson.command.CommandExecutor;
import org.redisson.misc.Hash;

import io.netty.buffer.ByteBuf;

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

    private final CommandExecutor commandExecutor;
    private final String configName;

    protected RedissonBloomFilter(CommandExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        this.commandExecutor = commandExecutor;
        this.configName = suffixName(getName(), "config");
    }

    protected RedissonBloomFilter(Codec codec, CommandExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        this.commandExecutor = commandExecutor;
        this.configName = suffixName(getName(), "config");
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
        long[] hashes = hash(object);

        while (true) {
            if (size == 0) {
                readConfig();
            }

            int hashIterations = this.hashIterations;
            long size = this.size;

            long[] indexes = hash(hashes[0], hashes[1], hashIterations, size);

            CommandBatchService executorService = new CommandBatchService(commandExecutor.getConnectionManager());
            addConfigCheck(hashIterations, size, executorService);
            RBitSetAsync bs = createBitSet(executorService);
            for (int i = 0; i < indexes.length; i++) {
                bs.setAsync(indexes[i]);
            }
            try {
                List<Boolean> result = (List<Boolean>) executorService.execute();

                for (Boolean val : result.subList(1, result.size()-1)) {
                    if (!val) {
                        return true;
                    }
                }
                return false;
            } catch (RedisException e) {
                if (!e.getMessage().contains("Bloom filter config has been changed")) {
                    throw e;
                }
            }
        }
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
    public boolean contains(T object) {
        long[] hashes = hash(object);

        while (true) {
            if (size == 0) {
                readConfig();
            }

            int hashIterations = this.hashIterations;
            long size = this.size;

            long[] indexes = hash(hashes[0], hashes[1], hashIterations, size);

            CommandBatchService executorService = new CommandBatchService(commandExecutor.getConnectionManager());
            addConfigCheck(hashIterations, size, executorService);
            RBitSetAsync bs = createBitSet(executorService);
            for (int i = 0; i < indexes.length; i++) {
                bs.getAsync(indexes[i]);
            }
            try {
                List<Boolean> result = (List<Boolean>) executorService.execute();

                for (Boolean val : result.subList(1, result.size()-1)) {
                    if (!val) {
                        return false;
                    }
                }

                return true;
            } catch (RedisException e) {
                if (!e.getMessage().contains("Bloom filter config has been changed")) {
                    throw e;
                }
            }
        }
    }

    protected RBitSetAsync createBitSet(CommandBatchService executorService) {
        return new RedissonBitSet(executorService, getName());
    }

    private void addConfigCheck(int hashIterations, long size, CommandBatchService executorService) {
        executorService.evalReadAsync(configName, codec, RedisCommands.EVAL_VOID,
                "local size = redis.call('hget', KEYS[1], 'size');" +
                        "local hashIterations = redis.call('hget', KEYS[1], 'hashIterations');" +
                        "assert(size == ARGV[1] and hashIterations == ARGV[2], 'Bloom filter config has been changed')",
                        Arrays.<Object>asList(configName), size, hashIterations);
    }

    @Override
    public long count() {
        CommandBatchService executorService = new CommandBatchService(commandExecutor.getConnectionManager());
        RFuture<Map<String, String>> configFuture = executorService.readAsync(configName, StringCodec.INSTANCE,
                new RedisCommand<Map<Object, Object>>("HGETALL", new ObjectMapReplayDecoder()), configName);
        RBitSetAsync bs = createBitSet(executorService);
        RFuture<Long> cardinalityFuture = bs.cardinalityAsync();
        executorService.execute();

        readConfig(configFuture.getNow());

        return Math.round(-size / ((double) hashIterations) * Math.log(1 - cardinalityFuture.getNow() / ((double) size)));
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_OBJECTS, getName(), configName);
    }

    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        List<Object> keys = Arrays.<Object>asList(getName(), configName);
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

        CommandBatchService executorService = new CommandBatchService(commandExecutor.getConnectionManager());
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
            if (!e.getMessage().contains("Bloom filter config has been changed")) {
                throw e;
            }
            readConfig();
            return false;
        }

        return true;
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                        "return redis.call('pexpire', KEYS[2], ARGV[1]); ",
                Arrays.<Object>asList(getName(), configName),
                timeUnit.toMillis(timeToLive));
    }

    @Override
    public RFuture<Boolean> expireAtAsync(long timestamp) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "redis.call('pexpireat', KEYS[1], ARGV[1]); " +
                        "return redis.call('pexpireat', KEYS[2], ARGV[1]); ",
                Arrays.<Object>asList(getName(), configName),
                timestamp);
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "redis.call('persist', KEYS[1]); " +
                        "return redis.call('persist', KEYS[2]); ",
                Arrays.<Object>asList(getName(), configName));
    }
    
    @Override
    public long getExpectedInsertions() {
        Long result = commandExecutor.read(configName, LongCodec.INSTANCE, RedisCommands.HGET, configName, "expectedInsertions");
        return check(result);
    }

    @Override
    public double getFalseProbability() {
        Double result = commandExecutor.read(configName, DoubleCodec.INSTANCE, RedisCommands.HGET, configName, "falseProbability");
        return check(result);
    }

    @Override
    public long getSize() {
        Long result = commandExecutor.read(configName, LongCodec.INSTANCE, RedisCommands.HGET, configName, "size");
        return check(result);
    }

    @Override
    public int getHashIterations() {
        Integer result = commandExecutor.read(configName, IntegerCodec.INSTANCE, RedisCommands.HGET, configName, "hashIterations");
        return check(result);
    }

    private <V> V check(V result) {
        if (result == null) {
            throw new IllegalStateException("Bloom filter is not initialized!");
        }
        return result;
    }

}
