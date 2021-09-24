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
import org.redisson.misc.RedissonPromise;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonBuckets implements RBuckets {

    protected final Codec codec;
    protected final CommandAsyncExecutor commandExecutor;

    public RedissonBuckets(CommandAsyncExecutor commandExecutor) {
        this(commandExecutor.getConnectionManager().getCodec(), commandExecutor);
    }
    
    public RedissonBuckets(Codec codec, CommandAsyncExecutor commandExecutor) {
        super();
        this.codec = codec;
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
            return RedissonPromise.newSucceededFuture(Collections.emptyMap());
        }
        
        Codec commandCodec = new CompositeCodec(StringCodec.INSTANCE, codec, codec);
        
        RedisCommand<Map<Object, Object>> command = new RedisCommand<Map<Object, Object>>("MGET", new MapGetAllDecoder(Arrays.<Object>asList(keys), 0));
        return commandExecutor.readBatchedAsync(commandCodec, command, new SlotCallback<Map<Object, Object>, Map<String, V>>() {
            final Map<String, V> results = new ConcurrentHashMap<>();

            @Override
            public void onSlotResult(Map<Object, Object> result) {
                for (Map.Entry<Object, Object> entry : result.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        results.put((String) entry.getKey(), (V) entry.getValue());
                    }
                }
            }

            @Override
            public Map<String, V> onFinish() {
                return results;
            }

            @Override
            public RedisCommand<Map<Object, Object>> createCommand(List<String> keys) {
                return new RedisCommand<>("MGET", new BucketsDecoder(keys));
            }
        }, keys);
    }

    @Override
    public RFuture<Boolean> trySetAsync(Map<String, ?> buckets) {
        if (buckets.isEmpty()) {
            return RedissonPromise.newSucceededFuture(false);
        }

        return commandExecutor.writeBatchedAsync(codec, RedisCommands.MSET, new SlotCallback<Void, Boolean>() {
            @Override
            public void onSlotResult(Void result) {
            }

            @Override
            public Boolean onFinish() {
                return true;
            }

            @Override
            public Object[] createParams(List<String> keys) {
                List<Object> params = new ArrayList<>(keys.size());
                for (String key : keys) {
                    params.add(key);
                    try {
                        params.add(codec.getValueEncoder().encode(buckets.get(key)));
                    } catch (IOException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
                return params.toArray();
            }
        }, buckets.keySet().toArray(new String[]{}));
    }

    @Override
    public RFuture<Void> setAsync(Map<String, ?> buckets) {
        if (buckets.isEmpty()) {
            return RedissonPromise.newSucceededFuture(null);
        }

        return commandExecutor.writeBatchedAsync(codec, RedisCommands.MSET, new SlotCallback<Void, Void>() {
            @Override
            public void onSlotResult(Void result) {
            }

            @Override
            public Void onFinish() {
                return null;
            }

            @Override
            public Object[] createParams(List<String> keys) {
                List<Object> params = new ArrayList<>(keys.size());
                for (String key : keys) {
                    params.add(key);
                    try {
                        params.add(codec.getValueEncoder().encode(buckets.get(key)));
                    } catch (IOException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
                return params.toArray();
            }
        }, buckets.keySet().toArray(new String[]{}));
    }

}
