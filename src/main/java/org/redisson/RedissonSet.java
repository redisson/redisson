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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RSet;

import io.netty.util.concurrent.Future;

/**
 * Distributed and concurrent implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSet<V> extends RedissonExpirable implements RSet<V> {

    protected RedissonSet(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public int size() {
        return ((Long)connectionManager.read(getName(), RedisCommands.SCARD, getName())).intValue();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        return connectionManager.read(getName(), RedisCommands.SISMEMBER, getName(), o);
    }

    private ListScanResult<V> scanIterator(long startPos) {
        return connectionManager.read(getName(), RedisCommands.SSCAN, getName(), startPos);
    }

    @Override
    public Iterator<V> iterator() {
        return new Iterator<V>() {

            private Iterator<V> iter;
            private Long iterPos;

            private boolean removeExecuted;
            private V value;

            @Override
            public boolean hasNext() {
                if (iter == null) {
                    ListScanResult<V> res = scanIterator(0);
                    iter = res.getValues().iterator();
                    iterPos = res.getPos();
                } else if (!iter.hasNext() && iterPos != 0) {
                    ListScanResult<V> res = scanIterator(iterPos);
                    iter = res.getValues().iterator();
                    iterPos = res.getPos();
                }
                return iter.hasNext();
            }

            @Override
            public V next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No such element at index");
                }

                value = iter.next();
                removeExecuted = false;
                return value;
            }

            @Override
            public void remove() {
                if (removeExecuted) {
                    throw new IllegalStateException("Element been already deleted");
                }

                // lazy init iterator
//                hasNext();
                iter.remove();
                RedissonSet.this.remove(value);
                removeExecuted = true;
            }

        };
    }

    @Override
    public Object[] toArray() {
        List<Object> res = connectionManager.read(getName(), RedisCommands.SMEMBERS, getName());
        return res.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        List<Object> res = connectionManager.read(getName(), RedisCommands.SMEMBERS, getName());
        return res.toArray(a);
    }

    @Override
    public boolean add(V e) {
        return connectionManager.get(addAsync(e));
    }

    @Override
    public Future<Boolean> addAsync(V e) {
        return connectionManager.writeAsync(getName(), RedisCommands.SADD_SINGLE, getName(), e);
    }

    @Override
    public Future<Boolean> removeAsync(V e) {
        return connectionManager.writeAsync(getName(), RedisCommands.SREM_SINGLE, getName(), e);
    }

    @Override
    public boolean remove(Object value) {
        return connectionManager.get(removeAsync((V)value));
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object object : c) {
            if (!contains(object)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(final Collection<? extends V> c) {
        if (c.isEmpty()) {
            return false;
        }

        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getName());
        args.addAll(c);
        Long res = connectionManager.write(getName(), RedisCommands.SADD, args.toArray());
        return res > 0;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        List<V> toRemove = new ArrayList<V>();
        for (V object : this) {
            if (!c.contains(object)) {
                toRemove.add(object);
            }
        }
        return removeAll(toRemove);
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        if (c.isEmpty()) {
            return false;
        }

        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getName());
        args.addAll(c);
        Long res = connectionManager.write(getName(), RedisCommands.SREM, args.toArray());
        return res > 0;
    }

    @Override
    public void clear() {
        delete();
    }

}
