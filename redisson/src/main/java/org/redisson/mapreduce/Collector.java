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
package org.redisson.mapreduce;

import java.io.IOException;

import org.redisson.api.RListMultimap;
import org.redisson.api.RedissonClient;
import org.redisson.api.mapreduce.RCollector;
import org.redisson.client.codec.Codec;

import net.openhft.hashing.LongHashFunction;

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
    
    public Collector(Codec codec, RedissonClient client, String name, int parts) {
        super();
        this.client = client;
        this.name = name;
        this.parts = parts;
        this.codec = codec;
    }

    @Override
    public void emit(K key, V value) {
        try {
            byte[] encodedKey = codec.getValueEncoder().encode(key);
            long hash = LongHashFunction.xx_r39().hashBytes(encodedKey);
            String partName = name + ":" + Math.abs(hash % parts);
            
            RListMultimap<K, V> multimap = client.getListMultimap(partName, codec);
            multimap.put(key, value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
