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
import org.redisson.api.RJsonBuckets;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.codec.JsonCodec;
import org.redisson.codec.JsonCodecWrapper;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.decoder.BucketsDecoder;
import org.redisson.connection.decoder.MapGetAllDecoder;
import org.redisson.misc.CompletableFutureWrapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RedissonJsonBuckets implements RJsonBuckets {
    
    protected final JsonCodec codec;
    protected final CommandAsyncExecutor commandExecutor;
    
    public RedissonJsonBuckets(JsonCodec codec, CommandAsyncExecutor commandExecutor) {
        this.codec = codec;
        this.commandExecutor = commandExecutor;
    }
    
    @Override
    public <V> Map<String, V> get(String... keys) {
        RFuture<Map<String, V>> future = getAsync(keys);
        return commandExecutor.get(future);
    }
    
    @Override
    public <V> RFuture<Map<String, V>> getAsync(String... keys) {
        return getAsync(codec, ".", keys);
    }
    
    @Override
    public <V> Map<String, V> get(JsonCodec codec, String path, String... keys) {
        RFuture<Map<String, V>> future = getAsync(codec, path, keys);
        return commandExecutor.get(future);
    }
    
    @Override
    public <V> RFuture<Map<String, V>> getAsync(JsonCodec codec, String path, String... keys) {
        if (keys.length == 0) {
            return new CompletableFutureWrapper<>(Collections.emptyMap());
        }
        
        List<Object> keysList = Arrays.stream(keys)
                .map(k -> commandExecutor.getServiceManager().getConfig().getNameMapper().map(k))
                .collect(Collectors.toList());
        
        JsonCodecWrapper jsonCodec = new JsonCodecWrapper(codec);
        RedisCommand<Map<Object, Object>> command = new RedisCommand<Map<Object, Object>>("JSON.MGET", new MapGetAllDecoder(keysList, 0));
        return commandExecutor.readBatchedAsync(jsonCodec, command, new SlotCallback<Map<Object, Object>, Map<String, V>>() {
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
                return new RedisCommand<>("JSON.MGET", new BucketsDecoder(keys));
            }
            
            @Override
            public Object[] createParams(List<Object> params) {
                ArrayList<Object> newParams = new ArrayList<>(params);
                newParams.add(path);
                return newParams.toArray();
            }
        }, keysList.toArray(new Object[0]));
    }
    
    @Override
    public void set(Map<String, ?> buckets) {
        commandExecutor.get(setAsync(buckets));
    }
    
    @Override
    public RFuture<Void> setAsync(Map<String, ?> buckets) {
        return setAsync(codec, ".", buckets);
    }
    
    @Override
    public void set(JsonCodec codec, String path, Map<String, ?> buckets) {
        commandExecutor.get(setAsync(codec, path, buckets));
    }
    
    @Override
    public RFuture<Void> setAsync(JsonCodec codec, String path, Map<String, ?> buckets) {
        if (buckets.isEmpty()) {
            return new CompletableFutureWrapper<>((Void) null);
        }
        
        Map<String, ?> mappedBuckets = buckets.entrySet().stream().collect(
                Collectors.toMap(e -> commandExecutor.getServiceManager().getConfig().getNameMapper().map(e.getKey()),
                        Map.Entry::getValue));
        JsonCodecWrapper jsonCodec = new JsonCodecWrapper(codec);
        return commandExecutor.writeBatchedAsync(jsonCodec, RedisCommands.JSON_MSET, new VoidSlotCallback() {
            @Override
            public Object[] createParams(List<Object> keys) {
                List<Object> params = new ArrayList<>(keys.size());
                for (Object key : keys) {
                    params.add(key);
                    params.add(path);
                    try {
                        params.add(jsonCodec.getValueEncoder().encode(mappedBuckets.get(key)));
                    } catch (IOException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
                return params.toArray();
            }
        }, mappedBuckets.keySet().toArray(new Object[0]));
    }
}