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
package org.redisson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.redisson.api.RBuckets;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.codec.CompositeCodec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.decoder.MapGetAllDecoder;
import org.redisson.misc.RedissonPromise;

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

        RedisCommand<Map<Object, Object>> command = new RedisCommand<Map<Object, Object>>("MGET", new MapGetAllDecoder(Arrays.<Object>asList(keys), 0));
        return commandExecutor.readAsync(keys[0], new CompositeCodec(StringCodec.INSTANCE, codec, codec), command, keys);
    }

    @Override
    public RFuture<Boolean> trySetAsync(Map<String, ?> buckets) {
        if (buckets.isEmpty()) {
            return RedissonPromise.newSucceededFuture(false);
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

        return commandExecutor.writeAsync(params.get(0).toString(), RedisCommands.MSETNX, params.toArray());
    }

    @Override
    public RFuture<Void> setAsync(Map<String, ?> buckets) {
        if (buckets.isEmpty()) {
            return RedissonPromise.newSucceededFuture(null);
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

        return commandExecutor.writeAsync(params.get(0).toString(), RedisCommands.MSET, params.toArray());
    }

    @Override
    public RFuture<Long> deleteAsync(String... keys) {
        RedissonKeys ks = new RedissonKeys(commandExecutor);
        return ks.deleteAsync(keys);
    }

    @Override
    public long delete(String... keys) {
        return commandExecutor.get(deleteAsync(keys));
    }

}
