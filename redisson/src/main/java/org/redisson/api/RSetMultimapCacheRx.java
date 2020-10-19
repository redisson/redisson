package org.redisson.api;

/**
 * Rx-ified version of {@link RSetMultimapCache}.
 *
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface RSetMultimapCacheRx<K, V> extends RSetMultimapRx<K, V>, RMultimapCacheRx<K, V> {

}
