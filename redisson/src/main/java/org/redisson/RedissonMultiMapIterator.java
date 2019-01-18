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

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.command.CommandAsyncExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 * @param <M> map type
 */
abstract class RedissonMultiMapIterator<K, V, M> implements Iterator<M> {

    private Iterator<Map.Entry<Object, Object>> keysIter;
    protected long keysIterPos = 0;

    private K currentKey;
    private Iterator<V> valuesIter;
    protected long valuesIterPos = 0;

    protected RedisClient client;

    private boolean finished;
    private boolean keysFinished;
    private boolean removeExecuted;
    protected V entry;

    final RedissonMultimap<K, V> map;

    final CommandAsyncExecutor commandExecutor;
    final Codec codec;


    public RedissonMultiMapIterator(RedissonMultimap<K, V> map, CommandAsyncExecutor commandExecutor, Codec codec) {
        this.map = map;
        this.commandExecutor = commandExecutor;
        this.codec = codec;
    }


    @Override
    public boolean hasNext() {
        if (valuesIter != null && valuesIter.hasNext()) {
            return true;
        }
        if (finished) {
            return false;
        }

        while (true) {
            if (!keysFinished && (keysIter == null || !keysIter.hasNext())) {
                MapScanResult<Object, Object> res = map.scanIterator(client, keysIterPos);
                client = res.getRedisClient();
                keysIter = res.getMap().entrySet().iterator();
                keysIterPos = res.getPos();
                
                if (res.getPos() == 0) {
                    keysFinished = true;
                }
            }
            
            while (keysIter.hasNext()) {
                Entry<Object, Object> e = keysIter.next();
                currentKey = (K) e.getKey();
                String name = e.getValue().toString();
                valuesIter = getIterator(name);
                if (valuesIter.hasNext()) {
                    return true;
                }
            }
            
            if (keysIterPos == 0) {
                finished = true;
                return false;
            }
        }
    }

    protected abstract Iterator<V> getIterator(String name);

    @Override
    public M next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No such element at index");
        }

        entry = valuesIter.next();
        removeExecuted = false;
        return getValue(entry);
    }

    @SuppressWarnings("unchecked")
    M getValue(V entry) {
        return (M)new AbstractMap.SimpleEntry<K, V>(currentKey, entry) {

            @Override
            public V setValue(V value) {
                throw new UnsupportedOperationException();
            }

        };
    }

    @Override
    public void remove() {
        if (removeExecuted) {
            throw new IllegalStateException("Element been already deleted");
        }
        if (valuesIter == null || entry == null) {
            throw new IllegalStateException();
        }

        map.remove(currentKey, entry);
        removeExecuted = true;
    }

}
