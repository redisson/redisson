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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
import org.redisson.core.RBloomFilter;

import io.netty.util.concurrent.Future;
import net.openhft.hashing.LongHashFunction;

/**
 * Bloom filter based on 64-bit hash derived from 128-bit hash (xxHash 64-bit + FarmHash 64-bit).
 *
 * Code parts from Guava BloomFilter
 *
 * @author Nikita Koksharov
 *
 * @param <T>
 */
public class RedissonBloomFilter<T> extends RedissonExpirable implements RBloomFilter<T> {

    private static final long MAX_SIZE = Integer.MAX_VALUE*2L;

    private volatile long size;
    private volatile int hashIterations;

    private final CommandExecutor commandExecutor;

    protected RedissonBloomFilter(CommandExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        this.commandExecutor = commandExecutor;
    }

    protected RedissonBloomFilter(Codec codec, CommandExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        this.commandExecutor = commandExecutor;
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

    @Override
    public boolean add(T object) {
        byte[] state = encode(object);

        while (true) {
            if (size == 0) {
                readConfig();
            }

            int hashIterations = this.hashIterations;
            long size = this.size;

            long[] indexes = hash(state, hashIterations, size);

            CommandBatchService executorService = new CommandBatchService(commandExecutor.getConnectionManager());
            addConfigCheck(hashIterations, size, executorService);
            for (int i = 0; i < indexes.length; i++) {
                executorService.writeAsync(getName(), codec, RedisCommands.SETBIT, getName(), indexes[i], 1);
            }
            try {
                List<Boolean> result = (List<Boolean>) executorService.execute();

                for (Boolean val : result.subList(1, result.size()-1)) {
                    if (val) {
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

    private long[] hash(byte[] state, int iterations, long size) {
        long hash1 = LongHashFunction.xx_r39().hashBytes(state);
        long hash2 = LongHashFunction.farmUo().hashBytes(state);

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
        byte[] state = encode(object);

        while (true) {
            if (size == 0) {
                readConfig();
            }

            int hashIterations = this.hashIterations;
            long size = this.size;

            long[] indexes = hash(state, hashIterations, size);

            CommandBatchService executorService = new CommandBatchService(commandExecutor.getConnectionManager());
            addConfigCheck(hashIterations, size, executorService);
            for (int i = 0; i < indexes.length; i++) {
                executorService.readAsync(getName(), codec, RedisCommands.GETBIT, getName(), indexes[i]);
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

    private byte[] encode(T object) {
        byte[] state = null;
        try {
            state = codec.getValueEncoder().encode(object);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return state;
    }

    private void addConfigCheck(int hashIterations, long size, CommandBatchService executorService) {
        executorService.evalReadAsync(getConfigName(), codec, RedisCommands.EVAL_VOID,
                "local size = redis.call('hget', KEYS[1], 'size');" +
                        "local hashIterations = redis.call('hget', KEYS[1], 'hashIterations');" +
                        "assert(size == ARGV[1] and hashIterations == ARGV[2], 'Bloom filter config has been changed')",
                        Arrays.<Object>asList(getConfigName()), size, hashIterations);
    }

    @Override
    public int count() {
        CommandBatchService executorService = new CommandBatchService(commandExecutor.getConnectionManager());
        Future<Map<String, String>> configFuture = executorService.readAsync(getConfigName(), StringCodec.INSTANCE,
                new RedisCommand<Map<Object, Object>>("HGETALL", new ObjectMapReplayDecoder()), getConfigName());
        Future<Long> cardinalityFuture = executorService.readAsync(getName(), codec, RedisCommands.BITCOUNT, getName());
        executorService.execute();

        readConfig(configFuture.getNow());

        return (int) (-size / ((double) hashIterations) * Math.log(1 - cardinalityFuture.getNow() / ((double) size)));
    }

    @Override
    public Future<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_OBJECTS, getName(), getConfigName());
    }

    private void readConfig() {
        Future<Map<String, String>> future = commandExecutor.readAsync(getConfigName(), StringCodec.INSTANCE,
                new RedisCommand<Map<Object, Object>>("HGETALL", new ObjectMapReplayDecoder()), getConfigName());
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

    @Override
    public boolean tryInit(long expectedInsertions, double falseProbability) {
        size = optimalNumOfBits(expectedInsertions, falseProbability);
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("Bloom filter can't be greater than " + MAX_SIZE + ". But calculated size is " + size);
        }
        hashIterations = optimalNumOfHashFunctions(expectedInsertions, size);

        CommandBatchService executorService = new CommandBatchService(commandExecutor.getConnectionManager());
        executorService.evalReadAsync(getConfigName(), codec, RedisCommands.EVAL_VOID,
                "local size = redis.call('hget', KEYS[1], 'size');" +
                        "local hashIterations = redis.call('hget', KEYS[1], 'hashIterations');" +
                        "assert(size == false and hashIterations == false, 'Bloom filter config has been changed')",
                        Arrays.<Object>asList(getConfigName()), size, hashIterations);
        executorService.writeAsync(getConfigName(), StringCodec.INSTANCE,
                                                new RedisCommand<Void>("HMSET", new VoidReplayConvertor()), getConfigName(),
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

    private String getConfigName() {
        return "{" + getName() + "}" + "__config";
    }

    @Override
    public long getExpectedInsertions() {
        Long result = commandExecutor.read(getConfigName(), LongCodec.INSTANCE, RedisCommands.HGET, getConfigName(), "expectedInsertions");
        return check(result);
    }

    @Override
    public double getFalseProbability() {
        Double result = commandExecutor.read(getConfigName(), DoubleCodec.INSTANCE, RedisCommands.HGET, getConfigName(), "falseProbability");
        return check(result);
    }

    @Override
    public long getSize() {
        Long result = commandExecutor.read(getConfigName(), LongCodec.INSTANCE, RedisCommands.HGET, getConfigName(), "size");
        return check(result);
    }

    @Override
    public int getHashIterations() {
        Integer result = commandExecutor.read(getConfigName(), IntegerCodec.INSTANCE, RedisCommands.HGET, getConfigName(), "hashIterations");
        return check(result);
    }

    private <V> V check(V result) {
        if (result == null) {
            throw new IllegalStateException("Bloom filter is not initialized!");
        }
        return result;
    }

}
