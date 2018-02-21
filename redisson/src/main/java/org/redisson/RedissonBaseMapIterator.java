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
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.misc.HashValue;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 * @param <M> loaded value type
 */
public abstract class RedissonBaseMapIterator<K, V, M> implements Iterator<M> {

    private Map<HashValue, HashValue> firstValues;
    private Map<HashValue, HashValue> lastValues;
    private Iterator<Map.Entry<ScanObjectEntry, ScanObjectEntry>> lastIter;
    protected long nextIterPos;
    protected RedisClient client;

    private boolean finished;
    private boolean currentElementRemoved;
    protected Map.Entry<ScanObjectEntry, ScanObjectEntry> entry;

    @Override
    public boolean hasNext() {
        if (lastIter == null || !lastIter.hasNext()) {
            if (finished) {
                currentElementRemoved = false;
                client = null;
                firstValues = null;
                lastValues = null;
                nextIterPos = 0;

                if (!tryAgain()) {
                    return false;
                }
                finished = false;
            }
            do {
                MapScanResult<ScanObjectEntry, ScanObjectEntry> res = iterator();
                
                lastValues = convert(res.getMap());
                client = res.getRedisClient();
                
                if (nextIterPos == 0 && firstValues == null) {
                    firstValues = lastValues;
                    lastValues = null;
                    if (firstValues.isEmpty() && tryAgain()) {
                        client = null;
                        firstValues = null;
                        nextIterPos = 0;
                    }
                } else {
                    if (firstValues.isEmpty()) {
                        firstValues = lastValues;
                        lastValues = null;
                        if (firstValues.isEmpty()) {
                            if (tryAgain()) {
                                client = null;
                                firstValues = null;
                                nextIterPos = 0;
                                continue;
                            }
                            if (res.getPos() == 0) {
                                finished = true;
                                return false;
                            }
                        }
                    } else if (lastValues.keySet().removeAll(firstValues.keySet())
                            || (lastValues.isEmpty() && nextIterPos == 0)) {
                        currentElementRemoved = false;

                        client = null;
                        firstValues = null;
                        lastValues = null;
                        nextIterPos = 0;
                        if (tryAgain()) {
                            continue;
                        }
                        
                        finished = true;
                        return false;
                    }
                }
                lastIter = res.getMap().entrySet().iterator();
                nextIterPos = res.getPos();
            } while (!lastIter.hasNext());
        }
        return lastIter.hasNext();
        
    }

    protected boolean tryAgain() {
        return false;
    }

    protected abstract MapScanResult<ScanObjectEntry, ScanObjectEntry> iterator();

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
            throw new NoSuchElementException();
        }

        entry = lastIter.next();
        currentElementRemoved = false;
        return getValue(entry);
    }

    @SuppressWarnings("unchecked")
    protected M getValue(final Entry<ScanObjectEntry, ScanObjectEntry> entry) {
        return (M)new AbstractMap.SimpleEntry<K, V>((K)entry.getKey().getObj(), (V)entry.getValue().getObj()) {

            @Override
            public V setValue(V value) {
                return put(entry, value);
            }

        };
    }

    @Override
    public void remove() {
        if (currentElementRemoved) {
            throw new IllegalStateException("Element been already deleted");
        }
        if (lastIter == null || entry == null) {
            throw new IllegalStateException();
        }

        firstValues.remove(entry.getKey().getHash());
        lastIter.remove();
        removeKey();
        currentElementRemoved = true;
        entry = null;
    }

    protected abstract void removeKey();

    protected abstract V put(Entry<ScanObjectEntry, ScanObjectEntry> entry, V value);

}
