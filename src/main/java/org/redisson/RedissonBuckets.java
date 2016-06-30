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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.DelegateDecoderCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.connection.decoder.MapGetAllDecoder;
import org.redisson.core.RBucket;
import org.redisson.core.RBuckets;

import io.netty.util.concurrent.Future;

public class RedissonBuckets implements RBuckets {

    private final Codec codec;
    private final CommandExecutor commandExecutor;
    private final Redisson redisson;
    
    public RedissonBuckets(Redisson redisson, CommandExecutor commandExecutor) {
        this(redisson, commandExecutor.getConnectionManager().getCodec(), commandExecutor);
    }
    
    public RedissonBuckets(Redisson redisson, Codec codec, CommandExecutor commandExecutor) {
        super();
        this.codec = codec;
        this.commandExecutor = commandExecutor;
        this.redisson = redisson;
    }

    @Override
    public <V> List<RBucket<V>> find(String pattern) {
        Collection<String> keys = commandExecutor.get(commandExecutor.<List<String>, String>readAllAsync(RedisCommands.KEYS, pattern));
        List<RBucket<V>> buckets = new ArrayList<RBucket<V>>(keys.size());
        for (String key : keys) {
            if(key == null) {
                continue;
            }
            buckets.add(redisson.<V>getBucket(key, codec));
        }
        return buckets;
    }

    @Override
    public <V> Map<String, V> get(String... keys) {
        if (keys.length == 0) {
            return Collections.emptyMap();
        }

        RedisCommand<Map<Object, Object>> command = new RedisCommand<Map<Object, Object>>("MGET", new MapGetAllDecoder(Arrays.<Object>asList(keys), 0), ValueType.OBJECTS);
        Future<Map<String, V>> future = commandExecutor.readAsync(keys[0], new DelegateDecoderCodec(codec), command, keys);
        return commandExecutor.get(future);
    }

    @Override
    public boolean trySet(Map<String, ?> buckets) {
        if (buckets.isEmpty()) {
            return false;
        }

        List<Object> params = new ArrayList<Object>(buckets.size());
        for (Entry<String, ?> entry : buckets.entrySet()) {
            params.add(entry.getKey());
            try {
                params.add(codec.getValueEncoder().encode(entry.getValue()));
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }

        return commandExecutor.write(params.get(0).toString(), RedisCommands.MSETNX, params.toArray());
    }

    @Override
    public void set(Map<String, ?> buckets) {
        if (buckets.isEmpty()) {
            return;
        }

        List<Object> params = new ArrayList<Object>(buckets.size());
        for (Entry<String, ?> entry : buckets.entrySet()) {
            params.add(entry.getKey());
            try {
                params.add(codec.getValueEncoder().encode(entry.getValue()));
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }

        commandExecutor.write(params.get(0).toString(), RedisCommands.MSET, params.toArray());
    }

}
