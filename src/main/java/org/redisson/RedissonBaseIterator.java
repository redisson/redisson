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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.redisson.client.protocol.decoder.ListScanResult;

abstract class RedissonBaseIterator<V> implements Iterator<V> {

    private List<V> firstValues;
    private Iterator<V> iter;
    private InetSocketAddress client;
    private long nextIterPos;
    private long startPos = -1;

    private boolean currentElementRemoved;
    private boolean removeExecuted;
    private V value;

    @Override
    public boolean hasNext() {
        if (iter == null || !iter.hasNext()) {
            if (nextIterPos == -1) {
                return false;
            }
            long prevIterPos;
            do {
                prevIterPos = nextIterPos;
                ListScanResult<V> res = iterator(client, nextIterPos);
                client = res.getRedisClient();
                if (startPos == -1) {
                    startPos = res.getPos();
                }
                if (nextIterPos == 0 && firstValues == null) {
                    firstValues = res.getValues();
                } else if (res.getValues().equals(firstValues) && res.getPos() == startPos) {
                    return false;
                }
                iter = res.getValues().iterator();
                nextIterPos = res.getPos();
            } while (!iter.hasNext() && nextIterPos != prevIterPos);
            if (prevIterPos == nextIterPos && !removeExecuted) {
                nextIterPos = -1;
            }
        }
        return iter.hasNext();
    }

    abstract ListScanResult<V> iterator(InetSocketAddress client, long nextIterPos);

    @Override
    public V next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No such element");
        }

        value = iter.next();
        currentElementRemoved = false;
        return value;
    }

    @Override
    public void remove() {
        if (currentElementRemoved) {
            throw new IllegalStateException("Element been already deleted");
        }
        if (iter == null) {
            throw new IllegalStateException();
        }

        iter.remove();
        remove(value);
        currentElementRemoved = true;
        removeExecuted = true;
    }

    abstract void remove(V value);

}
