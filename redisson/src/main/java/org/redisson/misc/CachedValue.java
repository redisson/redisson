package org.redisson.misc;

/**
 * Created by jribble on 2/20/17.
 */
public interface CachedValue<K, V> {
    boolean isExpired();

    K getKey();

    V getValue();
}
