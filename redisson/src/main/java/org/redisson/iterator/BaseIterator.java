/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.iterator;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.redisson.ScanResult;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisNodeNotFoundException;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <E> entry type
 * @param <V> value type
 */
public abstract class BaseIterator<V, E> implements Iterator<V> {

    private Iterator<E> lastIter;
    protected String nextIterPos;
    protected RedisClient client;

    private boolean finished;
    private boolean currentElementRemoved;
    protected E value;

    protected BaseIterator() {
        nextIterPos = initValue();
    }

    protected String initValue() {
        return "0";
    }

    protected void reset() {
    }

    @Override
    public boolean hasNext() {
        if (lastIter == null || !lastIter.hasNext()) {
            if (finished) {
                currentElementRemoved = false;
                client = null;
                nextIterPos = initValue();

                if (!tryAgain()) {
                    return false;
                }
                finished = false;
            }
            do {
                ScanResult<E> res;
                try {
                    res = iterator(client, nextIterPos);
                } catch (RedisNodeNotFoundException e) {
                    if (client != null) {
                        client = null;
                        nextIterPos = initValue();
                    }
                    reset();
                    res = iterator(client, nextIterPos);
                }
                
                client = res.getRedisClient();
                
                lastIter = res.getValues().iterator();
                nextIterPos = res.getPos();

                if (initValue().equals(res.getPos())) {
                    finished = true;
                    if (res.getValues().isEmpty()) {
                        currentElementRemoved = false;
                        
                        client = null;
                        nextIterPos = initValue();
                        if (tryAgain()) {
                            continue;
                        }
                        
                        return false;
                    }
                }
            } while (!lastIter.hasNext());
        }
        return lastIter.hasNext();
    }
    
    protected boolean tryAgain() {
        return false;
    }

    protected abstract ScanResult<E> iterator(RedisClient client, String nextIterPos);

    @Override
    public V next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No such element");
        }

        value = lastIter.next();
        currentElementRemoved = false;
        return getValue(value);
    }

    protected abstract V getValue(E entry);
    
    @Override
    public void remove() {
        if (currentElementRemoved) {
            throw new IllegalStateException("Element been already deleted");
        }
        if (lastIter == null || value == null) {
            throw new IllegalStateException();
        }

        lastIter.remove();
        remove(value);
        currentElementRemoved = true;
    }

    protected abstract void remove(E value);

}
