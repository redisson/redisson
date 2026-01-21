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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.redisson.api.RBloomFilterNative;
import org.redisson.api.RFuture;
import org.redisson.api.bloomfilter.BloomFilterInfo;
import org.redisson.api.bloomfilter.BloomFilterInfoOption;
import org.redisson.api.bloomfilter.BloomFilterInitArgs;
import org.redisson.api.bloomfilter.BloomFilterInitParams;
import org.redisson.api.bloomfilter.BloomFilterInsertArgs;
import org.redisson.api.bloomfilter.BloomFilterInsertParams;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;

/**
 * Bloom filter based on BF.* commands
 *
 * @author Su Ko
 *
 * @param <T> type of object
 */
public class RedissonBloomFilterNative<T> extends RedissonExpirable implements RBloomFilterNative<T> {

    final CommandAsyncExecutor commandExecutor;

    protected RedissonBloomFilterNative(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        this.commandExecutor = commandExecutor;
    }

    protected RedissonBloomFilterNative(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        this.commandExecutor = commandExecutor;
    }

    @Override
    public Boolean add(T element) {
        return commandExecutor.get(addAsync(element));
    }

    @Override
    public RFuture<Boolean> addAsync(T element) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.BF_ADD, getRawName(), element);
    }

    @Override
    public List<Boolean> add(Collection<T> elements) {
        return commandExecutor.get(addAsync(elements));
    }

    @Override
    public RFuture<List<Boolean>> addAsync(Collection<T> elements) {
        if (elements == null || elements.isEmpty()) {
            return new CompletableFutureWrapper<>(Collections.emptyList());
        }

        List<Object> params = new ArrayList<>();
        params.add(getRawName());
        params.addAll(elements);

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.BF_MADD, params.toArray());
    }

    @Override
    public List<Boolean> insert(BloomFilterInsertArgs args) {
        return commandExecutor.get(insertAsync(args));
    }

    @Override
    public RFuture<List<Boolean>> insertAsync(BloomFilterInsertArgs args) {
        BloomFilterInsertParams bloomFilterInsertParams = (BloomFilterInsertParams) args;

        String[] items = bloomFilterInsertParams.getItems();

        if (items == null || items.length == 0) {
            return new CompletableFutureWrapper<>(Collections.emptyList());
        }

        Long capacity = bloomFilterInsertParams.getCapacity();
        Double errorRate = bloomFilterInsertParams.getErrorRate();
        Long expansionRate = bloomFilterInsertParams.getExpansionRate();
        Boolean nonScaling = bloomFilterInsertParams.isNonScaling();
        Boolean noCreate = bloomFilterInsertParams.isNoCreate();

        List<Object> params = new ArrayList<Object>();
        params.add(getRawName());

        if (noCreate != null && noCreate && (capacity != null || errorRate != null)) {
            throw new IllegalArgumentException("BloomFilter Native noCreate and capacity/errorRate are mutually exclusive");
        }

        if (capacity != null) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("BloomFilter Native capacity must be greater than 0");
            }

            params.add("CAPACITY");
            params.add(capacity);
        }

        if (errorRate != null) {
            if (errorRate <= 0 || errorRate >= 1) {
                throw new IllegalArgumentException("BloomFilter Native errorRate must be greater than 0 and less than 1");
            }

            params.add("ERROR");
            params.add(errorRate);
        }

        if (expansionRate != null) {
            if (expansionRate <= 0) {
                throw new IllegalArgumentException("BloomFilter Native expansionRate must be greater than 0");
            }

            params.add("EXPANSION");
            params.add(expansionRate);
        }

        if (noCreate != null && noCreate) {
            params.add("NOCREATE");
        }

        if (nonScaling != null && nonScaling) {
            params.add("NONSCALING");
        }

        params.add("ITEMS");
        params.addAll(Arrays.asList(items));

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.BF_INSERT, params.toArray());
    }

    @Override
    public void init(double errorRate, long capacity) {
        commandExecutor.get(initAsync(errorRate, capacity));
    }

    @Override
    public RFuture<Void> initAsync(double errorRate, long capacity) {
        if (errorRate <= 0 || errorRate >= 1) {
            throw new IllegalArgumentException("BloomFilter Native errorRate must be greater than 0 and less than 1");
        }

        if (capacity <= 0) {
            throw new IllegalArgumentException("BloomFilter Native capacity must be greater than 0");
        }

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.BF_RESERVE, getRawName(), errorRate, capacity);
    }


    @Override
    public void init(BloomFilterInitArgs args) {
        commandExecutor.get(initAsync(args));
    }

    @Override
    public RFuture<Void> initAsync(BloomFilterInitArgs args) {
        BloomFilterInitParams bloomFilterInitParams = (BloomFilterInitParams) args;
        double errorRate = bloomFilterInitParams.getErrorRate();
        long capacity = bloomFilterInitParams.getCapacity();
        Long expansionRate = bloomFilterInitParams.getExpansionRate();
        Boolean nonScaling = bloomFilterInitParams.isNonScaling();

        if (errorRate <= 0 || errorRate >= 1) {
            throw new IllegalArgumentException("BloomFilter Native errorRate must be greater than 0 and less than 1");
        }

        if (capacity <= 0) {
            throw new IllegalArgumentException("BloomFilter Native capacity must be greater than 0");
        }

        if (expansionRate != null && nonScaling != null) {
            throw new IllegalArgumentException("BloomFilter Native expansionRate and nonScaling are mutually exclusive");
        }

        List<Object> params = new ArrayList<>();
        params.add(getRawName());
        params.add(errorRate);
        params.add(capacity);

        if (expansionRate != null) {
            if (expansionRate <= 1) {
                throw new IllegalArgumentException("BloomFilter Native expansionRate must be greater than 1");
            }

            params.add("EXPANSION");
            params.add(expansionRate);
        }

        if (nonScaling != null && nonScaling) {
            params.add("NONSCALING");
        }

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.BF_RESERVE, params.toArray());
    }

    @Override
    public Boolean exists(T element) {
        return commandExecutor.get(existsAsync(element));
    }

    @Override
    public RFuture<Boolean> existsAsync(T element) {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.BF_EXISTS, getRawName(), element);
    }

    @Override
    public List<Boolean> exists(Collection<T> elements) {
        return commandExecutor.get(existsAsync(elements));
    }

    @Override
    public RFuture<List<Boolean>> existsAsync(Collection<T> elements) {
        if (elements == null || elements.isEmpty()) {
            return new CompletableFutureWrapper<>(Collections.emptyList());
        }

        List<Object> params = new ArrayList<>();
        params.add(getRawName());
        params.addAll(elements);

        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.BF_MEXISTS, params.toArray());
    }


    @Override
    public Long count() {
        return commandExecutor.get(countAsync());
    }

    @Override
    public RFuture<Long> countAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.BF_CARD, getRawName());
    }

    @Override
    public BloomFilterInfo getInfo() {
        return commandExecutor.get(getInfoAsync());
    }

    @Override
    public RFuture<BloomFilterInfo> getInfoAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.BF_INFO, getRawName());
    }

    @Override
    public long getInfo(BloomFilterInfoOption option) {
        return commandExecutor.get(getInfoAsync(option));
    }

    @Override
    public RFuture<Long> getInfoAsync(BloomFilterInfoOption option) {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.BF_INFO_SINGLE, getRawName(), option.getOptionString());
    }
}
