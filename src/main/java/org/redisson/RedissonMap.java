package org.redisson;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.lambdaworks.redis.RedisConnection;

public class RedissonMap<K, V> implements Map<K, V> {

    private final RedisConnection<Object, Object> connection;
    private final String name;

    RedissonMap(RedisConnection<Object, Object> connection, String name) {
        this.connection = connection;
        this.name = name;
    }

    protected String getName() {
        return name;
    }

    protected RedisConnection<Object, Object> getConnection() {
        return connection;
    }

    @Override
    public int size() {
        return connection.hlen(name).intValue();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return connection.hexists(name, key);
    }

    @Override
    public boolean containsValue(Object value) {
        return connection.hvals(name).contains(value);
    }

    @Override
    public V get(Object key) {
        return (V) connection.hget(name, key);
    }

    @Override
    public V put(K key, V value) {
        V prev = get(key);
        connection.hset(name, key, value);
        return prev;
    }

    @Override
    public V remove(Object key) {
        V prev = get(key);
        connection.hdel(name, key);
        return prev;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        connection.hmset(name, (Map<Object, Object>) map);
    }

    @Override
    public void clear() {
        Object[] fields = connection.hkeys(name).toArray();
        connection.hdel(name, fields);
    }

    @Override
    public Set<K> keySet() {
        // TODO use Set in internals
        return new HashSet<K>((Collection<? extends K>) connection.hkeys(name));
    }

    @Override
    public Collection<V> values() {
        return (Collection<V>) connection.hvals(name);
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        Map<Object, Object> map = connection.hgetall(name);
        Map<K, V> result = new HashMap<K, V>();
        for (java.util.Map.Entry<Object, Object> entry : map.entrySet()) {
            result.put((K)entry.getKey(), (V)entry.getValue());
        }
        return result.entrySet();
    }

}
