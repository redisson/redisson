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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;

import io.netty.buffer.ByteBuf;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
abstract class RedissonBaseIterator<V> implements Iterator<V> {

    private List<ByteBuf> firstValues;
    private List<ByteBuf> lastValues;
    private Iterator<ScanObjectEntry> lastIter;
    protected long nextIterPos;
    protected RedisClient client;

    private boolean finished;
    private boolean currentElementRemoved;
    private V value;

    @Override
    public boolean hasNext() {
        if (lastIter == null || !lastIter.hasNext()) {
            if (finished) {
                free(firstValues);
                free(lastValues);

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
                ListScanResult<ScanObjectEntry> res = iterator(client, nextIterPos);
                if (lastValues != null) {
                    free(lastValues);
                }
                
                lastValues = convert(res.getValues());
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
                                free(firstValues);
                                free(lastValues);
                                
                                finished = true;
                                return false;
                            }
                        }
                    } else if (lastValues.removeAll(firstValues)
                            || (lastValues.isEmpty() && nextIterPos == 0)) {
                        free(firstValues);
                        free(lastValues);

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
                lastIter = res.getValues().iterator();
                nextIterPos = res.getPos();
            } while (!lastIter.hasNext());
        }
        return lastIter.hasNext();
    }
    
    private List<ByteBuf> convert(List<ScanObjectEntry> list) {
        List<ByteBuf> result = new ArrayList<ByteBuf>(list.size());
        for (ScanObjectEntry entry : list) {
            result.add(entry.getBuf());
        }
        return result;
    }
    
    private void free(List<ByteBuf> list) {
        if (list == null) {
            return;
        }
        for (ByteBuf byteBuf : list) {
            byteBuf.release();
        }
    }
    
    protected boolean tryAgain() {
        return false;
    }

    abstract ListScanResult<ScanObjectEntry> iterator(RedisClient client, long nextIterPos);

    @Override
    public V next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No such element");
        }

        value = (V) lastIter.next().getObj();
        currentElementRemoved = false;
        return value;
    }

    @Override
    public void remove() {
        if (currentElementRemoved) {
            throw new IllegalStateException("Element been already deleted");
        }
        if (lastIter == null) {
            throw new IllegalStateException();
        }

        firstValues.remove(value);
        lastIter.remove();
        remove(value);
        currentElementRemoved = true;
    }

    abstract void remove(V value);

}
