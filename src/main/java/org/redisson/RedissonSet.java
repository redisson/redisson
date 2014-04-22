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

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.redisson.connection.ConnectionManager;
import org.redisson.core.RSet;

import com.lambdaworks.redis.RedisConnection;

/**
 * Distributed and concurrent implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSet<V> extends RedissonExpirable implements RSet<V> {

    RedissonSet(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public int size() {
        RedisConnection<Object, Object> connection = connectionManager.connectionReadOp();
        try {
            return connection.scard(getName()).intValue();
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        RedisConnection<Object, Object> connection = connectionManager.connectionReadOp();
        try {
            return connection.sismember(getName(), o);
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public Iterator<V> iterator() {
        RedisConnection<Object, Object> connection = connectionManager.connectionReadOp();
        try {
            // TODO use SSCAN in case of usage Redis 2.8
            final Iterator<V> iter = (Iterator<V>) connection.smembers(getName()).iterator();
            return new Iterator<V>() {

                private boolean removeExecuted;
                private V value;

                @Override
                public boolean hasNext() {
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

                    iter.remove();
                    RedissonSet.this.remove(value);
                    removeExecuted = true;
                }

            };
        } finally {
            connectionManager.release(connection);
        }

    }

    @Override
    public Object[] toArray() {
        RedisConnection<Object, Object> connection = connectionManager.connectionReadOp();
        try {
            return connection.smembers(getName()).toArray();
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public <T> T[] toArray(T[] a) {
        RedisConnection<Object, Object> connection = connectionManager.connectionReadOp();
        try {
            return connection.smembers(getName()).toArray(a);
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean add(V e) {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            return connection.sadd(getName(), e) > 0;
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean remove(Object o) {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            return connection.srem(getName(), o) > 0;
        } finally {
            connectionManager.release(connection);
        }
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
    public boolean addAll(Collection<? extends V> c) {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            return connection.sadd(getName(), c.toArray()) > 0;
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = false;
        for (Object object : this) {
            if (!c.contains(object)) {
                remove(object);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            return connection.srem(getName(), c.toArray()) > 0;
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public void clear() {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            connection.del(getName());
        } finally {
            connectionManager.release(connection);
        }
    }

}
