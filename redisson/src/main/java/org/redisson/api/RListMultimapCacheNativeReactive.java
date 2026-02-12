/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

/**
 * List based Multimap with ability to set TTL per entry.
 * Stores insertion order and allows duplicates for values mapped to key.
 * Uses Redis native commands for entry expiration and not a scheduled eviction task.
 * <p>
 * Requires <b>Redis 7.4.0 and higher.</b>
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public interface RListMultimapCacheNativeReactive<K, V> extends RListMultimapReactive<K, V>, RMultimapCacheReactive<K, V> {

}
