/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;

import io.netty.buffer.ByteBuf;

abstract class RedissonBaseMapIterator<K, V, M> implements Iterator<M> {

    private Map<ByteBuf, ByteBuf> firstValues;
    private Iterator<Map.Entry<ScanObjectEntry, ScanObjectEntry>> iter;
    protected long iterPos = 0;
    protected InetSocketAddress client;

    private boolean finished;
    private boolean removeExecuted;
    protected Map.Entry<ScanObjectEntry, ScanObjectEntry> entry;

    @Override
    public boolean hasNext() {
        if (finished) {
            return false;
        }
        if (iter == null || !iter.hasNext()) {
            MapScanResult<ScanObjectEntry, ScanObjectEntry> res = iterator();
            client = res.getRedisClient();
            if (iterPos == 0 && firstValues == null) {
                firstValues = convert(res.getMap());
            } else {
                Map<ByteBuf, ByteBuf> newValues = convert(res.getMap());
                if (newValues.equals(firstValues)) {
                    finished = true;
                    free(firstValues);
                    free(newValues);
                    firstValues = null;
                    return false;
                }
                free(newValues);
            }
            iter = res.getMap().entrySet().iterator();
            iterPos = res.getPos();
        }
        return iter.hasNext();
    }

    protected abstract MapScanResult<ScanObjectEntry, ScanObjectEntry> iterator();

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

        entry = iter.next();
        removeExecuted = false;
        return getValue(entry);
    }

    @SuppressWarnings("unchecked")
    M getValue(final Entry<ScanObjectEntry, ScanObjectEntry> entry) {
        return (M)new AbstractMap.SimpleEntry<K, V>((K)entry.getKey().getObj(), (V)entry.getValue().getObj()) {

            @Override
            public V setValue(V value) {
                return put(entry, value);
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
        iter.remove();
        removeKey();
        removeExecuted = true;
    }

    protected abstract void removeKey();

    protected abstract V put(Entry<ScanObjectEntry, ScanObjectEntry> entry, V value);

}
