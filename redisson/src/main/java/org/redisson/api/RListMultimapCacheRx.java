package org.redisson.api;

/**
 * Rx-ified version of {@link RListMultimapCache}.
 *
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface RListMultimapCacheRx<K, V> extends RListMultimapRx<K, V>, RMultimapCacheRx<K, V> {

}
