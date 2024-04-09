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

import org.redisson.api.RBuckets;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.codec.CompositeCodec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.decoder.BucketsDecoder;
import org.redisson.connection.decoder.MapGetAllDecoder;
import org.redisson.misc.CompletableFutureWrapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonBuckets implements RBuckets {

    protected final Codec codec;
    protected final CommandAsyncExecutor commandExecutor;

    public RedissonBuckets(CommandAsyncExecutor commandExecutor) {
        this(commandExecutor.getServiceManager().getCfg().getCodec(), commandExecutor);
    }
    
    public RedissonBuckets(Codec codec, CommandAsyncExecutor commandExecutor) {
        super();
        this.codec = commandExecutor.getServiceManager().getCodec(codec);
        this.commandExecutor = commandExecutor;
    }

    @Override
    public <V> Map<String, V> get(String... keys) {
        RFuture<Map<String, V>> future = getAsync(keys);
        return commandExecutor.get(future);
    }

    @Override
    public boolean trySet(Map<String, ?> buckets) {
        RFuture<Boolean> future = trySetAsync(buckets);
        return commandExecutor.get(future);
    }

    @Override
    public void set(Map<String, ?> buckets) {
        commandExecutor.get(setAsync(buckets));
    }

    @Override
    public <V> RFuture<Map<String, V>> getAsync(String... keys) {
        if (keys.length == 0) {
            return new CompletableFutureWrapper<>(Collections.emptyMap());
        }

        List<Object> keysList = Arrays.stream(keys)
                                        .map(k -> commandExecutor.getServiceManager().getConfig().getNameMapper().map(k))
                                        .collect(Collectors.toList());

        Codec commandCodec = new CompositeCodec(StringCodec.INSTANCE, codec, codec);
        
        RedisCommand<Map<Object, Object>> command = new RedisCommand<Map<Object, Object>>("MGET", new MapGetAllDecoder(keysList, 0));
        return commandExecutor.readBatchedAsync(commandCodec, command, new SlotCallback<Map<Object, Object>, Map<String, V>>() {
            final Map<String, V> results = new ConcurrentHashMap<>();

            @Override
            public void onSlotResult(List<Object> keys, Map<Object, Object> result) {
                for (Map.Entry<Object, Object> entry : result.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        String key = commandExecutor.getServiceManager().getConfig().getNameMapper().unmap((String) entry.getKey());
                        results.put(key, (V) entry.getValue());
                    }
                }
            }

            @Override
            public Map<String, V> onFinish() {
                return results;
            }

            @Override
            public RedisCommand<Map<Object, Object>> createCommand(List<Object> keys) {
                return new RedisCommand<>("MGET", new BucketsDecoder(keys));
            }
        }, keysList.toArray(new Object[0]));
    }

    @Override
    public RFuture<Boolean> trySetAsync(Map<String, ?> buckets) {
        if (buckets.isEmpty()) {
            return new CompletableFutureWrapper<>(false);
        }

        Map<String, ?> mappedBuckets = map(buckets);

        return commandExecutor.writeBatchedAsync(codec, RedisCommands.MSETNX, new SlotCallback<Boolean, Boolean>() {
            final AtomicBoolean result = new AtomicBoolean(true);

            @Override
            public void onSlotResult(List<Object> keys, Boolean result) {
                if (!result && this.result.get()){
                    this.result.set(result);
                }
            }

            @Override
            public Boolean onFinish() {
                return this.result.get();
            }

            @Override
            public Object[] createParams(List<Object> keys) {
                List<Object> params = new ArrayList<>(keys.size());
                for (Object key : keys) {
                    params.add(key);
                    try {
                        params.add(codec.getValueEncoder().encode(mappedBuckets.get(key)));
                    } catch (IOException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
                return params.toArray();
            }
        }, mappedBuckets.keySet().toArray(new Object[0]));
    }

    private Map<String, ?> map(Map<String, ?> buckets) {
        return buckets.entrySet().stream().collect(
                Collectors.toMap(e -> commandExecutor.getServiceManager().getConfig().getNameMapper().map(e.getKey()),
                        e -> e.getValue()));
    }

    @Override
    public RFuture<Void> setAsync(Map<String, ?> buckets) {
        if (buckets.isEmpty()) {
            return new CompletableFutureWrapper<>((Void) null);
        }

        Map<String, ?> mappedBuckets = map(buckets);

        return commandExecutor.writeBatchedAsync(codec, RedisCommands.MSET, new SlotCallback<Void, Void>() {
            @Override
            public void onSlotResult(List<Object> keys, Void result) {
            }

            @Override
            public Void onFinish() {
                return null;
            }

            @Override
            public Object[] createParams(List<Object> keys) {
                List<Object> params = new ArrayList<>(keys.size());
                for (Object key : keys) {
                    params.add(key);
                    try {
                        params.add(codec.getValueEncoder().encode(mappedBuckets.get(key)));
                    } catch (IOException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
                return params.toArray();
            }
        }, mappedBuckets.keySet().toArray(new Object[0]));
    }

}
