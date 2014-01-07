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

import org.redisson.core.RSet;

import com.lambdaworks.redis.RedisConnection;

public class RedissonSet<V> implements RSet<V> {

    private final ConnectionManager connectionManager;
    private final String name;

    RedissonSet(ConnectionManager connectionManager, String name) {
        this.connectionManager = connectionManager;
        this.name = name;
    }

    @Override
    public int size() {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            return connection.scard(name).intValue();
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
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            return connection.sismember(name, o);
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public Iterator<V> iterator() {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            // TODO use SSCAN in case of usage Redis 2.8
            return (Iterator<V>) connection.smembers(name).iterator();
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public Object[] toArray() {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            return connection.smembers(name).toArray();
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public <T> T[] toArray(T[] a) {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            return connection.smembers(name).toArray(a);
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean add(V e) {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            return connection.sadd(name, e) > 0;
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean remove(Object o) {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            return connection.srem(name, o) > 0;
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
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            return connection.sadd(name, c.toArray()) > 0;
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = false;
        for (Iterator<V> iterator = iterator(); iterator.hasNext();) {
            V object = iterator.next();
            if (!c.contains(object)) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            return connection.srem(name, c.toArray()) > 0;
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public void clear() {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            connection.del(name);
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void destroy() {
//        redisson.remove(this);
    }

}
