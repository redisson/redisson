/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.api;

import io.reactivex.rxjava3.core.Single;

import java.util.concurrent.TimeUnit;

/**
 * Reactive interface of {@link RMultimapCache} object.
 *
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface RMultimapCacheReactive<K, V> {

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
