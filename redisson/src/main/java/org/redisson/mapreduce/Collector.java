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
package org.redisson.mapreduce;

import java.io.IOException;
import java.util.BitSet;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RListMultimap;
import org.redisson.api.RedissonClient;
import org.redisson.api.mapreduce.RCollector;
import org.redisson.client.codec.Codec;
import org.redisson.misc.Hash;

import io.netty.buffer.ByteBuf;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class Collector<K, V> implements RCollector<K, V> {

    private RedissonClient client;
    private String name;
    private int parts;
    private Codec codec;
    private long timeout;
    private BitSet expirationsBitSet = new BitSet();
    
    public Collector(Codec codec, RedissonClient client, String name, int parts, long timeout) {
        super();
        this.client = client;
        this.name = name;
        this.parts = parts;
        this.codec = codec;
        this.timeout = timeout;
        expirationsBitSet = new BitSet(parts);
    }

    @Override
    public void emit(K key, V value) {
        try {
            ByteBuf encodedKey = codec.getValueEncoder().encode(key);
            long hash = Hash.hash64(encodedKey);
            encodedKey.release();
            int part = (int) Math.abs(hash % parts);
            String partName = name + ":" + part;
            
            RListMultimap<K, V> multimap = client.getListMultimap(partName, codec);
            multimap.put(key, value);
            if (timeout > 0 && !expirationsBitSet.get(part)) {
                multimap.expire(timeout, TimeUnit.MILLISECONDS);
                expirationsBitSet.set(part);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
