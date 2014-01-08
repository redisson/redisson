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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.redisson.connection.ConnectionManager;
import org.redisson.core.RMap;

import com.lambdaworks.redis.RedisConnection;

//TODO implement watching by keys instead of map name
public class RedissonMap<K, V> implements RMap<K, V> {

    private final ConnectionManager connectionManager;
    private final String name;

    RedissonMap(ConnectionManager connectionManager, String name) {
        this.connectionManager = connectionManager;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public int size() {
        RedisConnection<String, Object> connection = connectionManager.acquireConnection();
        try {
            return connection.hlen(name).intValue();
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            return connection.hexists(name, key);
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean containsValue(Object value) {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            return connection.hvals(name).contains(value);
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public V get(Object key) {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            return (V) connection.hget(name, key);
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public V put(K key, V value) {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            V prev = (V) connection.hget(name, key);
            connection.hset(name, key, value);
            return prev;
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public V remove(Object key) {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            V prev = (V) connection.hget(name, key);
            connection.hdel(name, key);
            return prev;
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            connection.hmset(name, (Map<Object, Object>) map);
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
    public Set<K> keySet() {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            return new LinkedHashSet<K>((Collection<? extends K>) connection.hkeys(name));
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public Collection<V> values() {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            return (Collection<V>) connection.hvals(name);
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            Map<Object, Object> map = connection.hgetall(name);
            Map<K, V> result = new HashMap<K, V>();
            for (java.util.Map.Entry<Object, Object> entry : map.entrySet()) {
                result.put((K)entry.getKey(), (V)entry.getValue());
            }
            return result.entrySet();
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            while (true) {
                Boolean res = connection.hsetnx(getName(), key, value);
                if (!res) {
                    V result = get(key);
                    if (result != null) {
                        return result;
                    }
                } else {
                    return null;
                }
            }
        } finally {
            connectionManager.release(connection);
        }

    }

    private boolean isEquals(RedisConnection<Object, Object> connection, Object key, Object value) {
        Object val = connection.hget(getName(), key);
        return (value != null && value.equals(val)) || (value == null && val == null);
    }

    @Override
    public boolean remove(Object key, Object value) {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            while (true) {
                connection.watch(getName());
                if (connection.hexists(getName(), key)
                        && isEquals(connection, key, value)) {
                    connection.multi();
                    connection.hdel(getName(), key);
                    if (connection.exec().size() == 1) {
                        return true;
                    }
                } else {
                    return false;
                }
            }
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            while (true) {
                connection.watch(getName());
                if (connection.hexists(getName(), key)
                        && isEquals(connection, key, oldValue)) {
                    connection.multi();
                    connection.hset(getName(), key, newValue);
                    if (connection.exec().size() == 1) {
                        return true;
                    }
                } else {
                    return false;
                }
            }
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public V replace(K key, V value) {
        RedisConnection<Object, Object> connection = connectionManager.acquireConnection();
        try {
            while (true) {
                connection.watch(getName());
                if (connection.hexists(getName(), key)) {
                    V prev = (V) connection.hget(getName(), key);
                    connection.multi();
                    connection.hset(getName(), key, value);
                    if (connection.exec().size() == 1) {
                        return prev;
                    }
                }
                return null;
            }
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public void destroy() {
//        redisson.remove(this);
    }

}
