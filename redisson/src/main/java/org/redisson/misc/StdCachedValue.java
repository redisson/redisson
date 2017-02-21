package org.redisson.misc;

/**
 * Created by jribble on 2/20/17.
 */

public class StdCachedValue<K, V> implements CachedValue<K, V> {

    protected final K key;
    protected final V value;

    long ttl;
    long maxIdleTime;

    long creationTime;
    long lastAccess;

    public StdCachedValue(K key, V value, long ttl, long maxIdleTime) {
        this.value = value;
        this.ttl = ttl;
        this.key = key;
        this.maxIdleTime = maxIdleTime;
        creationTime = System.currentTimeMillis();
        lastAccess = creationTime;
    }

    @Override
    public boolean isExpired() {
        boolean result = false;
        long currentTime = System.currentTimeMillis();
        if (ttl != 0 && creationTime + ttl < currentTime) {
            result = true;
        }
        if (maxIdleTime != 0 && lastAccess + maxIdleTime < currentTime) {
            result = true;
        }
        return result;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        lastAccess = System.currentTimeMillis();
        return value;
    }

    @Override
    public String toString() {
        return "CachedValue [key=" + key + ", value=" + value + "]";
    }

}
