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

import org.redisson.api.bucket.SetArgs;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Operations over multiple Bucket objects.
 * 
 * @author Nikita Koksharov
 *
 */
public interface RBucketsReactive {

    /**
     * Returns Redis object mapped by key. Result Map is not contains
     * key-value entry for null values.
     * 
     * @param <V> type of value
     * @param keys - keys
     * @return Map with name of bucket as key and bucket as value
     */
    <V> Mono<Map<String, V>> get(String... keys);

    /**
     * Try to save objects mapped by Redis key.
     * If at least one of them is already exist then 
     * don't set none of them.
     *
     * @param buckets - map of buckets
     * @return <code>true</code> if object has been set otherwise <code>false</code>
     */
    Mono<Boolean> trySet(Map<String, ?> buckets);

    /**
     * Saves objects mapped by Redis key.
     *
     * @param buckets - map of buckets
     * @return void
     */
    Mono<Void> set(Map<String, ?> buckets);

    /**
     * Saves objects mapped by Redis key.
     * If all of them is already exist
     *
     * @param args - args
     * @return <code>true</code> if object has been set overwise <code>false</code>
     */
    Mono<Boolean> setIfAllKeysExist(SetArgs args);

    /**
     * Saves objects mapped by Redis key.
     * If none of the specified keys exist
     *
     * @param args - args
     * @return <code>true</code> if object has been set overwise <code>false</code>
     */
    Mono<Boolean> setIfAllKeysAbsent(SetArgs args);
    
}
