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
import org.redisson.api.bucket.SetParams;
import org.redisson.api.bucket.SetArgs;
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
                                        .map(k -> commandExecutor.getServiceManager().getNameMapper().map(k))
                                        .collect(Collectors.toList());

        Codec commandCodec = new CompositeCodec(StringCodec.INSTANCE, codec, codec);
        
        RedisCommand<Map<Object, Object>> command = new RedisCommand<>(RedisCommands.MGET.getName(), new MapGetAllDecoder(keysList, 0));
        return commandExecutor.readBatchedAsync(commandCodec, command, new SlotCallback<Map<Object, Object>, Map<String, V>>() {

            @Override
            public Map<String, V> onResult(Collection<Map<Object, Object>> result) {
                return result.stream()
                        .flatMap(c -> c.entrySet().stream())
                        .filter(e -> e.getKey() != null && e.getValue() != null)
                        .map(e -> {
                            String key = commandExecutor.getServiceManager().getNameMapper().unmap((String) e.getKey());
                            return new AbstractMap.SimpleEntry<>(key, (V) e.getValue());
                        }).collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
            }

            @Override
            public RedisCommand<Map<Object, Object>> createCommand(List<Object> keys) {
                return new RedisCommand<>(RedisCommands.MGET.getName(), new BucketsDecoder(keys));
            }
        }, keysList.toArray(new Object[0]));
    }

    @Override
    public RFuture<Boolean> trySetAsync(Map<String, ?> buckets) {
        if (buckets.isEmpty()) {
            return new CompletableFutureWrapper<>(false);
        }

        Map<String, ?> mappedBuckets = map(buckets);

        return commandExecutor.writeBatchedAsync(codec, RedisCommands.MSETNX, new BooleanSlotCallback() {
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
                Collectors.toMap(e -> commandExecutor.getServiceManager().getNameMapper().map(e.getKey()),
                        e -> e.getValue()));
    }

    @Override
    public RFuture<Void> setAsync(Map<String, ?> buckets) {
        if (buckets.isEmpty()) {
            return new CompletableFutureWrapper<>((Void) null);
        }

        Map<String, ?> mappedBuckets = map(buckets);

        return commandExecutor.writeBatchedAsync(codec, RedisCommands.MSET, new VoidSlotCallback() {
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

    @Override
    public boolean setIfAllKeysExist(SetArgs args) {
        return commandExecutor.get(setIfAllKeysExistAsync(args));
    }

    @Override
    public RFuture<Boolean> setIfAllKeysExistAsync(SetArgs args) {
        return setAsyncInternal("XX", args);
    }

    private RFuture<Boolean> setAsyncInternal(String subCommand, SetArgs args) {

        SetParams pps = (SetParams) args;
        if (pps.getEntries().isEmpty()) {
            return new CompletableFutureWrapper<>(false);
        }

        Map<String, ?> mappedBuckets = map(pps.getEntries());
        return commandExecutor.writeBatchedAsync(codec, RedisCommands.MSETEX, new BooleanSlotCallback() {
            @Override
            public Object[] createParams(List<Object> keys) {
                List<Object> params = new ArrayList<>(keys.size());
                params.add(keys.size());
                for (Object key : keys) {
                    params.add(key);
                    try {
                        params.add(codec.getValueEncoder().encode(mappedBuckets.get(key)));
                    } catch (IOException e) {
                        throw new IllegalArgumentException(e);
                    }
                }

                if (!subCommand.isEmpty()) {
                    params.add(subCommand);
                }

                if (pps.getTimeToLive() != null) {
                    params.add("PX");
                    params.add(pps.getTimeToLive().toMillis());
                }

                if (pps.getExpireAt() != null) {
                    params.add("PXAT");
                    params.add(pps.getExpireAt().toEpochMilli());
                }

                if (pps.isKeepTTL()) {
                    params.add("KEEPTTL");
                }

                return params.toArray();
            }
        }, mappedBuckets.keySet().toArray(new Object[0]));
    }
}
