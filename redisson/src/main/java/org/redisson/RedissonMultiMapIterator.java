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

import java.net.InetSocketAddress;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.command.CommandAsyncExecutor;

import io.netty.buffer.ByteBuf;

abstract class RedissonMultiMapIterator<K, V, M> implements Iterator<M> {

    private Map<ByteBuf, ByteBuf> firstKeys;
    private Iterator<Map.Entry<ScanObjectEntry, ScanObjectEntry>> keysIter;
    protected long keysIterPos = 0;

    private K currentKey;
    private Iterator<V> valuesIter;
    protected long valuesIterPos = 0;

    protected InetSocketAddress client;

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
                Map<ByteBuf, ByteBuf> newValues = convert(res.getMap());
                if (newValues.equals(firstKeys)) {
                    finished = true;
                    free(firstKeys);
                    free(newValues);
                    firstKeys = null;
                    return false;
                }
                free(newValues);
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

    private void free(Map<ByteBuf, ByteBuf> map) {
        for (Entry<ByteBuf, ByteBuf> entry : map.entrySet()) {
            entry.getKey().release();
            entry.getValue().release();
        }
    }

    private Map<ByteBuf, ByteBuf> convert(Map<ScanObjectEntry, ScanObjectEntry> map) {
        Map<ByteBuf, ByteBuf> result = new HashMap<ByteBuf, ByteBuf>(map.size());
        for (Entry<ScanObjectEntry, ScanObjectEntry> entry : map.entrySet()) {
            result.put(entry.getKey().getBuf(), entry.getValue().getBuf());
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
