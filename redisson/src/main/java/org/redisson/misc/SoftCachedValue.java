package org.redisson.misc;

import java.lang.ref.SoftReference;

/**
 * Created by jribble on 2/20/17.
 */

public class SoftCachedValue<K, V> implements CachedValue<K, V> {
    StdCachedValue<K, SoftReference<V>> value;

    public SoftCachedValue(K key, V value, long ttl, long maxIdleTime) {
        this.value = new StdCachedValue<>(key, new SoftReference<>(value), ttl, maxIdleTime);
    }

    @Override
    public boolean isExpired() {
        return value.isExpired();
    }

    @Override
    public K getKey() {
        return value.getKey();
    }

    @Override
    public V getValue() {
        return value.getValue().get();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
