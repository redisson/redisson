/**
 * Copyright 2018 Nikita Koksharov
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.HashValue;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 * @param <M> map type
 */
abstract class RedissonMultiMapIterator<K, V, M> implements Iterator<M> {

    private Map<HashValue, HashValue> firstKeys;
    private Iterator<Map.Entry<ScanObjectEntry, ScanObjectEntry>> keysIter;
    protected long keysIterPos = 0;

    private K currentKey;
    private Iterator<V> valuesIter;
    protected long valuesIterPos = 0;

    protected RedisClient client;

    private boolean finished;
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
        if (finished) {
            return false;
        }

        if (valuesIter != null && valuesIter.hasNext()) {
            return true;
        }

        if (keysIter == null || !keysIter.hasNext()) {
            MapScanResult<ScanObjectEntry, ScanObjectEntry> res = map.scanIterator(client, keysIterPos);
            client = res.getRedisClient();
            if (keysIterPos == 0 && firstKeys == null) {
                firstKeys = convert(res.getMap());
            } else {
                Map<HashValue, HashValue> newValues = convert(res.getMap());
                if (newValues.equals(firstKeys)) {
                    finished = true;
                    firstKeys = null;
                    return false;
                }
            }
            keysIter = res.getMap().entrySet().iterator();
            keysIterPos = res.getPos();
        }

        while (true) {
            if (keysIter.hasNext()) {
                Entry<ScanObjectEntry, ScanObjectEntry> e = keysIter.next();
                currentKey = (K) e.getKey().getObj();
                String name = e.getValue().getObj().toString();
                valuesIter = getIterator(name);
                if (valuesIter.hasNext()) {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    protected abstract Iterator<V> getIterator(String name);

    private Map<HashValue, HashValue> convert(Map<ScanObjectEntry, ScanObjectEntry> map) {
        Map<HashValue, HashValue> result = new HashMap<HashValue, HashValue>(map.size());
        for (Entry<ScanObjectEntry, ScanObjectEntry> entry : map.entrySet()) {
            result.put(entry.getKey().getHash(), entry.getValue().getHash());
        }
        return result;
    }

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

        // lazy init iterator
        hasNext();
        keysIter.remove();
        map.remove(currentKey, entry);
        removeExecuted = true;
    }

}
