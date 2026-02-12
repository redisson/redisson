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

import org.redisson.codec.JsonCodec;

import java.util.Map;

public interface RJsonBuckets extends RJsonBucketsAsync {
    
    /**
     * Returns Redis json object mapped by key with default path
     *
     * @param keys keys
     * @param <V>  type of object with specific json-path
     * @return Map with name as key and bucket as value
     */
    <V> Map<String, V> get(String... keys);
    
    /**
     * Returns Redis json object mapped by key with specific path
     *
     * @param codec codec for specific path
     * @param path  json path
     * @param keys  keys
     * @param <V>   type of value at specific json-path
     * @return Map with name as key and bucket as value
     */
    <V> Map<String, V> get(JsonCodec codec, String path, String... keys);
    
    /**
     * Saves json objects with default path mapped by Redis key.
     *
     * @param buckets map of json buckets
     */
    void set(Map<String, ?> buckets);
    
    /**
     * Saves json objects with specific path mapped by Redis key.
     *
     * @param codec   codec for specific path
     * @param path    json path
     * @param buckets map of json buckets
     */
    void set(JsonCodec codec, String path, Map<String, ?> buckets);
}
