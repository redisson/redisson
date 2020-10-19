package org.redisson.api;

import io.reactivex.Single;

import java.util.concurrent.TimeUnit;

/**
 * Rx-ified version of {@link RMultimapCache}.
 *
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface RMultimapCacheRx<K, V> {

    /**
     * Set a timeout for key. After the timeout has expired, the key and its values will automatically be deleted.
     *
     * @param key - map key
     * @param timeToLive - timeout before key will be deleted
     * @param timeUnit - timeout time unit
     * @return A Single that will emit <code>true</code> if key exists and the timeout was set and <code>false</code>
     * if key not exists
     */
    Single<Boolean> expireKey(K key, long timeToLive, TimeUnit timeUnit);
}
