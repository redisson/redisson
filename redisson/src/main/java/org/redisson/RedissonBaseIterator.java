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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.redisson.client.protocol.decoder.ListScanResult;

abstract class RedissonBaseIterator<V> implements Iterator<V> {

    private List<V> firstValues;
    private List<V> lastValues;
    private Iterator<V> lastIter;
    protected long nextIterPos;
    protected InetSocketAddress client;

    private boolean finished;
    private boolean currentElementRemoved;
    private boolean removeExecuted;
    private V value;

    @Override
    public boolean hasNext() {
        if (lastIter == null || !lastIter.hasNext()) {
            if (finished) {

                currentElementRemoved = false;
                removeExecuted = false;
                client = null;
                firstValues = null;
                lastValues = null;
                nextIterPos = 0;

                if (!tryAgain()) {
                    return false;
                }
                finished = false;
            }
            long prevIterPos;
            do {
                prevIterPos = nextIterPos;
                ListScanResult<V> res = iterator(client, nextIterPos);
                lastValues = new ArrayList<V>(res.getValues());
                client = res.getRedisClient();

                if (nextIterPos == 0 && firstValues == null) {
                    firstValues = lastValues;
                    lastValues = null;
                    if (firstValues.isEmpty() && tryAgain()) {
                        client = null;
                        firstValues = null;
                        nextIterPos = 0;
                        prevIterPos = -1;
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
                                prevIterPos = -1;
                                continue;
                            }
                            if (res.getPos() == 0) {
                                finished = true;
                                return false;
                            }
                        }
                    } else if (lastValues.removeAll(firstValues)) {
                        currentElementRemoved = false;
                        removeExecuted = false;
                        client = null;
                        firstValues = null;
                        lastValues = null;
                        nextIterPos = 0;
                        prevIterPos = -1;
                        if (tryAgain()) {
                            continue;
                        }
                        finished = true;
                        return false;
                    }
                }
                lastIter = res.getValues().iterator();
                nextIterPos = res.getPos();
            } while (!lastIter.hasNext() && nextIterPos != prevIterPos);
            if (prevIterPos == nextIterPos && !removeExecuted) {
                finished = true;
            }
        }
        return lastIter.hasNext();
    }
    
    protected boolean tryAgain() {
        return false;
    }

    abstract ListScanResult<V> iterator(InetSocketAddress client, long nextIterPos);

    @Override
    public V next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No such element");
        }

        value = lastIter.next();
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
        removeExecuted = true;
    }

    abstract void remove(V value);

}
