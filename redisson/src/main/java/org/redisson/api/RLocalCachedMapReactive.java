package org.redisson.api;

import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Map object with local entry cache support.
 * <p>
 * Each instance maintains local cache to achieve fast read operations.
 * Suitable for maps which used mostly for read operations and network roundtrip delays are undesirable.
 *
 * @author Nikita Koksharov
 *
 * @param <K> map key
 * @param <V> map value
 */
public interface RLocalCachedMapReactive<K, V> extends RMapReactive<K, V> {

    /**
     * Clears local cache across all instances
     *
     * @return void
     */
    Mono<Void> clearLocalCache();

    /**
     * Returns all keys stored in local cache
     *
     * @return keys
     */
    Set<K> cachedKeySet();

    /**
     * Returns all values stored in local cache
     *
     * @return values
     */
    Collection<V> cachedValues();

    /**
     * Returns all map entries stored in local cache
     *
     * @return entries
     */
    Set<Map.Entry<K, V>> cachedEntrySet();

    /**
     * Returns state of local cache
     *
     * @return map
     */
    Map<K, V> getCachedMap();

}
