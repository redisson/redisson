package org.redisson;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;

import com.lambdaworks.redis.RedisConnection;

public class RedissonConcurrentMap<K, V> extends RedissonMap<K, V> implements ConcurrentMap<K, V> {

    private final Redisson redisson;

    RedissonConcurrentMap(Redisson redisson, RedisConnection<Object, Object> connection, String name) {
        super(connection, name);
        this.redisson = redisson;
    }

    @Override
    public V putIfAbsent(K key, V value) {
        while (true) {
            Boolean res = getConnection().hsetnx(getName(), key, value);
            if (!res) {
                V result = get(key);
                if (result != null) {
                    return result;
                }
            } else {
                return null;
            }
        }
    }

    @Override
    public boolean remove(Object key, Object value) {
        // TODO use murmur-hashing as lock key table
        Lock lock = redisson.getLock(getName() + "__" + key);
        lock.lock();
        try {
            if (containsKey(key) && get(key).equals(value)) {
                remove(key);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        // TODO user murmur-hashing as lock key table
        Lock lock = redisson.getLock(getName() + "__" + key);
        lock.lock();
        try {
            if (containsKey(key) && get(key).equals(oldValue)) {
                getConnection().hset(getName(), key, newValue);
                return true;
            } else {
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public V replace(K key, V value) {
        // TODO use murmur-hashing as lock key table
        Lock lock = redisson.getLock(getName() + "__" + key);
        lock.lock();
        try {
            if (containsKey(key)) {
                return put(key, value);
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

}
