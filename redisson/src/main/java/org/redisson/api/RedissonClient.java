/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.api.options.*;
import org.redisson.api.redisnode.BaseRedisNodes;
import org.redisson.api.redisnode.RedisNodes;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonCodec;
import org.redisson.config.Config;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Main Redisson interface for access
 * to all redisson objects with sync/async interface.
 * 
 * @see RedissonReactiveClient
 * @see RedissonRxClient
 *
 * @author Nikita Koksharov
 *
 */
public interface RedissonClient {

    /**
     * Returns time-series instance by <code>name</code>
     *
     * @param <V> value type
     * @param <L> label type
     * @param name name of instance
     * @return RTimeSeries object
     */
    <V, L> RTimeSeries<V, L> getTimeSeries(String name);

    /**
     * Returns time-series instance by <code>name</code>
     * using provided <code>codec</code> for values.
     *
     * @param <V> value type
     * @param <L> label type
     * @param name name of instance
     * @param codec codec for values
     * @return RTimeSeries object
     */
    <V, L> RTimeSeries<V, L> getTimeSeries(String name, Codec codec);

    /**
     * Returns time-series instance with specified <code>options</code>.
     *
     * @param <V> value type
     * @param <L> label type
     * @param options instance options
     * @return RTimeSeries object
     */
    <V, L> RTimeSeries<V, L> getTimeSeries(PlainOptions options);

    /**
     * Returns stream instance by <code>name</code>
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name of stream
     * @return RStream object
     */
    <K, V> RStream<K, V> getStream(String name);
    
    /**
     * Returns stream instance by <code>name</code>
     * using provided <code>codec</code> for entries.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of stream
     * @param codec codec for entry
     * @return RStream object
     */
    <K, V> RStream<K, V> getStream(String name, Codec codec);

    /**
     * Returns time-series instance with specified <code>options</code>.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param options instance options
     * @return RStream object
     */
    <K, V> RStream<K, V> getStream(PlainOptions options);

    /**
     * Returns API for RediSearch module
     *
     * @return RSearch object
     */
    RSearch getSearch();

    /**
     * Returns API for RediSearch module using defined codec for attribute values.
     *
     * @param codec codec for entry
     * @return RSearch object
     */
    RSearch getSearch(Codec codec);

    /**
     * Returns API for RediSearch module with specified <code>options</code>.
     *
     * @param options instance options
     * @return RSearch object
     */
    RSearch getSearch(OptionalOptions options);

    /**
     * Returns rate limiter instance by <code>name</code>
     * 
     * @param name of rate limiter
     * @return RateLimiter object
     */
    RRateLimiter getRateLimiter(String name);

    /**
     * Returns rate limiter instance with specified <code>options</code>.
     *
     * @param options instance options
     * @return RateLimiter object
     */
    RRateLimiter getRateLimiter(CommonOptions options);
    
    /**
     * Returns binary stream holder instance by <code>name</code>
     * 
     * @param name of binary stream
     * @return BinaryStream object 
     */
    RBinaryStream getBinaryStream(String name);

    /**
     * Returns binary stream holder instance with specified <code>options</code>.
     *
     * @param options instance options
     * @return BinaryStream object
     */
    RBinaryStream getBinaryStream(CommonOptions options);
    
    /**
     * Returns geospatial items holder instance by <code>name</code>.
     * 
     * @param <V> type of value
     * @param name name of object
     * @return Geo object
     */
    <V> RGeo<V> getGeo(String name);
    
    /**
     * Returns geospatial items holder instance by <code>name</code>
     * using provided codec for geospatial members.
     * 
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for value
     * @return Geo object
     */
    <V> RGeo<V> getGeo(String name, Codec codec);

    /**
     * Returns geospatial items holder instance with specified <code>options</code>.
     *
     * @param <V> type of value
     * @param options instance options
     * @return Geo object
     */
    <V> RGeo<V> getGeo(PlainOptions options);
    
    /**
     * Returns set-based cache instance by <code>name</code>.
     * Supports value eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getSet(String, Codec)}.</p>
     * 
     * @param <V> type of value
     * @param name name of object
     * @return SetCache object
     */
    <V> RSetCache<V> getSetCache(String name);

    /**
     * Returns set-based cache instance by <code>name</code>.
     * Supports value eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getSet(String, Codec)}.</p>
     * 
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for values
     * @return SetCache object
     */
    <V> RSetCache<V> getSetCache(String name, Codec codec);

    /**
     * Returns set-based cache instance with specified <code>options</code>.
     * Supports value eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getSet(PlainOptions)}.</p>
     *
     * @param <V> type of value
     * @param options instance options
     * @return SetCache object
     */
    <V> RSetCache<V> getSetCache(PlainOptions options);

    /**
     * Returns map-based cache instance by <code>name</code>
     * using provided <code>codec</code> for both cache keys and values.
     * Supports entry eviction with a given MaxIdleTime and TTL settings.
     * <p>
     * If eviction is not required then it's better to use regular map {@link #getMap(String, Codec)}.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name object name
     * @param codec codec for keys and values
     * @return MapCache object
     */
    <K, V> RMapCache<K, V> getMapCache(String name, Codec codec);
    
    /**
     * Returns map-based cache instance by <code>name</code>
     * using provided <code>codec</code> for both cache keys and values.
     * Supports entry eviction with a given MaxIdleTime and TTL settings.
     * <p>
     * If eviction is not required then it's better to use regular map {@link #getMap(String, Codec)}.
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name object name
     * @param codec codec for keys and values
     * @param options map options
     * @return MapCache object
     */
    @Deprecated
    <K, V> RMapCache<K, V> getMapCache(String name, Codec codec, MapCacheOptions<K, V> options);

    /**
     * Returns map-based cache instance with specified <code>options</code>.
     * Supports entry eviction with a given MaxIdleTime and TTL settings.
     * <p>
     * If eviction is not required then it's better to use regular map {@link #getMap(org.redisson.api.options.MapOptions)}.</p>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param options instance options
     * @return MapCache object
     */
    <K, V> RMapCache<K, V> getMapCache(org.redisson.api.options.MapCacheOptions<K, V> options);

    /**
     * Returns map-based cache instance by name.
     * Supports entry eviction with a given MaxIdleTime and TTL settings.
     * <p>
     * If eviction is not required then it's better to use regular map {@link #getMap(String)}.</p>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @return MapCache object
     */
    <K, V> RMapCache<K, V> getMapCache(String name);
    
    /**
     * Returns map-based cache instance by name.
     * Supports entry eviction with a given MaxIdleTime and TTL settings.
     * <p>
     * If eviction is not required then it's better to use regular map {@link #getMap(String)}.</p>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @param options map options
     * @return MapCache object
     */
    @Deprecated
    <K, V> RMapCache<K, V> getMapCache(String name, MapCacheOptions<K, V> options);

    /**
     * Returns object holder instance by name.
     *
     * @param <V> type of value
     * @param name name of object
     * @return Bucket object
     */
    <V> RBucket<V> getBucket(String name);

    /**
     * Returns object holder instance by name
     * using provided codec for object.
     *
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for values
     * @return Bucket object
     */
    <V> RBucket<V> getBucket(String name, Codec codec);

    /**
     * Returns object holder instance with specified <code>options</code>.
     *
     * @param <V> type of value
     * @param options instance options
     * @return Bucket object
     */
    <V> RBucket<V> getBucket(PlainOptions options);

    /**
     * Returns interface for mass operations with Bucket objects.
     *
     * @return Buckets object
     */
    RBuckets getBuckets();

    /**
     * Returns interface for mass operations with Bucket objects
     * using provided codec for object.
     *
     * @param codec codec for bucket objects
     * @return Buckets object
     */
    RBuckets getBuckets(Codec codec);

    /**
     * Returns API for mass operations over Bucket objects with specified <code>options</code>.
     *
     * @param options instance options
     * @return Buckets object
     */
    RBuckets getBuckets(OptionalOptions options);

    /**
     * Returns JSON data holder instance by name using provided codec.
     *
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for values
     * @return JsonBucket object
     */
    <V> RJsonBucket<V> getJsonBucket(String name, JsonCodec codec);

    /**
     * Returns JSON data holder instance with specified <code>options</code>.
     *
     * @param <V> type of value
     * @param options instance options
     * @return JsonBucket object
     */
    <V> RJsonBucket<V> getJsonBucket(JsonBucketOptions<V> options);
    
    /**
     * Returns API for mass operations over JsonBucket objects
     * using provided codec for JSON object with default path.
     *
     * @param codec using provided codec for JSON object with default path.
     * @return JsonBuckets
     */
    RJsonBuckets getJsonBuckets(JsonCodec codec);
    
    /**
     * Returns HyperLogLog instance by name.
     *
     * @param <V> type of value
     * @param name name of object
     * @return HyperLogLog object
     */
    <V> RHyperLogLog<V> getHyperLogLog(String name);

    /**
     * Returns HyperLogLog instance by name
     * using provided codec for hll objects.
     * 
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for values
     * @return HyperLogLog object
     */
    <V> RHyperLogLog<V> getHyperLogLog(String name, Codec codec);

    /**
     * Returns HyperLogLog instance with specified <code>options</code>.
     *
     * @param <V> type of value
     * @param options instance options
     * @return HyperLogLog object
     */
    <V> RHyperLogLog<V> getHyperLogLog(PlainOptions options);

    /**
     * Returns list instance by name.
     *
     * @param <V> type of value
     * @param name name of object
     * @return List object
     */
    <V> RList<V> getList(String name);

    /**
     * Returns list instance by name
     * using provided codec for list objects.
     * 
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for values
     * @return List object
     */
    <V> RList<V> getList(String name, Codec codec);

    /**
     * Returns list instance with specified <code>options</code>.
     *
     * @param <V> type of value
     * @param options instance options
     * @return List object
     */
    <V> RList<V> getList(PlainOptions options);

    /**
     * Returns List based Multimap instance by name.
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @return ListMultimap object
     */
    <K, V> RListMultimap<K, V> getListMultimap(String name);

    /**
     * Returns List based Multimap instance by name
     * using provided codec for both map keys and values.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for keys and values
     * @return ListMultimap object
     */
    <K, V> RListMultimap<K, V> getListMultimap(String name, Codec codec);

    /**
     * Returns List based Multimap instance with specified <code>options</code>.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param options instance options
     * @return ListMultimap object
     */
    <K, V> RListMultimap<K, V> getListMultimap(PlainOptions options);

    /**
     * Returns List based Multimap instance by name.
     * Supports key-entry eviction with a given TTL value.
     * 
     * <p>If eviction is not required then it's better to use regular map {@link #getSetMultimap(String)}.</p>
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @return ListMultimapCache object
     */
    <K, V> RListMultimapCache<K, V> getListMultimapCache(String name);
    
    /**
     * Returns List based Multimap instance by name
     * using provided codec for both map keys and values.
     * Supports key-entry eviction with a given TTL value.
     * 
     * <p>If eviction is not required then it's better to use regular map {@link #getSetMultimap(String, Codec)}.</p>
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for keys and values
     * @return ListMultimapCache object
     */
    <K, V> RListMultimapCache<K, V> getListMultimapCache(String name, Codec codec);

    /**
     * Returns List based Multimap instance by name.
     * Supports key-entry eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getSetMultimap(String)}.</p>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param options instance options
     * @return ListMultimapCache object
     */
    <K, V> RListMultimapCache<K, V> getListMultimapCache(PlainOptions options);

    /**
     * Returns local cached map cache instance by name.
     * Configured by parameters of options-object.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param options - local map options
     * @return LocalCachedMapCache object
     */
    <K, V> RLocalCachedMapCache<K, V> getLocalCachedMapCache(String name, LocalCachedMapCacheOptions<K, V> options);

    /**
     * Returns local cached map cache instance by name using provided codec.
     * Configured by parameters of options-object.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for keys and values
     * @param options - local map options
     * @return LocalCachedMap object
     */
    <K, V> RLocalCachedMapCache<K, V> getLocalCachedMapCache(String name, Codec codec, LocalCachedMapCacheOptions<K, V> options);

    /**
     * Returns List based Multimap instance by name.
     * Supports key-entry eviction with a given TTL value.
     * Stores insertion order and allows duplicates for values mapped to key.
     * <p>
     * Uses Redis native commands for entry expiration and not a scheduled eviction task.
     * <p>
     * Requires <b>Redis 7.4.0 and higher.</b>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @return ListMultimapCache object
     */
    <K, V> RListMultimapCacheNative<K, V> getListMultimapCacheNative(String name);

    /**
     * Returns List based Multimap instance by name
     * using provided codec for both map keys and values.
     * Supports key-entry eviction with a given TTL value.
     * Stores insertion order and allows duplicates for values mapped to key.
     * <p>
     * Uses Redis native commands for entry expiration and not a scheduled eviction task.
     * <p>
     * Requires <b>Redis 7.4.0 and higher.</b>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for keys and values
     * @return ListMultimapCache object
     */
    <K, V> RListMultimapCacheNative<K, V> getListMultimapCacheNative(String name, Codec codec);

    /**
     * Returns List based Multimap instance by name.
     * Supports key-entry eviction with a given TTL value.
     * Stores insertion order and allows duplicates for values mapped to key.
     * <p>
     * Uses Redis native commands for entry expiration and not a scheduled eviction task.
     * <p>
     * Requires <b>Redis 7.4.0 and higher.</b>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param options instance options
     * @return ListMultimapCache object
     */
    <K, V> RListMultimapCacheNative<K, V> getListMultimapCacheNative(PlainOptions options);

    /**
     * Returns local cached map instance by name.
     * Configured by parameters of options-object. 
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @param options local map options
     * @return LocalCachedMap object
     */
    @Deprecated
    <K, V> RLocalCachedMap<K, V> getLocalCachedMap(String name, LocalCachedMapOptions<K, V> options);
    
    /**
     * Returns local cached map instance by name
     * using provided codec. Configured by parameters of options-object.
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for keys and values
     * @param options local map options
     * @return LocalCachedMap object
     */
    @Deprecated
    <K, V> RLocalCachedMap<K, V> getLocalCachedMap(String name, Codec codec, LocalCachedMapOptions<K, V> options);

    /**
     * Returns local cached map instance with specified <code>options</code>.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param options instance options
     * @return LocalCachedMap object
     */
    <K, V> RLocalCachedMap<K, V> getLocalCachedMap(org.redisson.api.options.LocalCachedMapOptions<K, V> options);
    
    /**
     * Returns map instance by name.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @return Map object
     */
    <K, V> RMap<K, V> getMap(String name);

    /**
     * Returns map instance by name.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @param options map options
     * @return Map object
     */
    @Deprecated
    <K, V> RMap<K, V> getMap(String name, MapOptions<K, V> options);

    /**
     * Returns map instance by name
     * using provided codec for both map keys and values.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for keys and values
     * @return Map object
     */
    <K, V> RMap<K, V> getMap(String name, Codec codec);

    /**
     * Returns map instance by name
     * using provided codec for both map keys and values.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for keys and values
     * @param options map options
     * @return Map object
     */
    @Deprecated
    <K, V> RMap<K, V> getMap(String name, Codec codec, MapOptions<K, V> options);

    /**
     * Returns map instance by name.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param options instance options
     * @return Map object
     */
    <K, V> RMap<K, V> getMap(org.redisson.api.options.MapOptions<K, V> options);

    /**
     * Returns map instance by name.
     * Supports entry eviction with a given TTL.
     * <p>
     * Requires <b>Redis 7.4.0 and higher.</b>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @return Map object
     */
    <K, V> RMapCacheNative<K, V> getMapCacheNative(String name);

    /**
     * Returns map instance by name
     * using provided codec for both map keys and values.
     * Supports entry eviction with a given TTL.
     * <p>
     * Requires <b>Redis 7.4.0 and higher.</b>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for keys and values
     * @return Map object
     */
    <K, V> RMapCacheNative<K, V> getMapCacheNative(String name, Codec codec);

    /**
     * Returns map instance.
     * Supports entry eviction with a given TTL.
     * Configured by the parameters of the options-object.
     * <p>
     * Requires <b>Redis 7.4.0 and higher.</b>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param options instance options
     * @return Map object
     */
    <K, V> RMapCacheNative<K, V> getMapCacheNative(org.redisson.api.options.MapOptions<K, V> options);

    /**
     * Returns Set based Multimap instance by name.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @return SetMultimap object
     */
    <K, V> RSetMultimap<K, V> getSetMultimap(String name);
    
    /**
     * Returns Set based Multimap instance by name
     * using provided codec for both map keys and values.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for keys and values
     * @return SetMultimap object
     */
    <K, V> RSetMultimap<K, V> getSetMultimap(String name, Codec codec);

    /**
     * Returns Set based Multimap instance with specified <code>options</code>.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param options instance options
     * @return SetMultimap object
     */
    <K, V> RSetMultimap<K, V> getSetMultimap(PlainOptions options);

    /**
     * Returns Set based Multimap instance by name.
     * Supports key-entry eviction with a given TTL value.
     * 
     * <p>If eviction is not required then it's better to use regular map {@link #getSetMultimap(String)}.</p>
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @return SetMultimapCache object
     */
    <K, V> RSetMultimapCache<K, V> getSetMultimapCache(String name);

    /**
     * Returns Set based Multimap instance by name
     * using provided codec for both map keys and values.
     * Supports key-entry eviction with a given TTL value.
     * 
     * <p>If eviction is not required then it's better to use regular map {@link #getSetMultimap(String, Codec)}.</p>
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for keys and values
     * @return SetMultimapCache object
     */
    <K, V> RSetMultimapCache<K, V> getSetMultimapCache(String name, Codec codec);

    /**
     * Returns Set based Multimap instance with specified <code>options</code>.
     * Supports key-entry eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getSetMultimap(PlainOptions)}.</p>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param options instance options
     * @return SetMultimapCache object
     */
    <K, V> RSetMultimapCache<K, V> getSetMultimapCache(PlainOptions options);

    /**
     * Returns Set based Multimap instance by name.
     * Supports key-entry eviction with a given TTL value.
     * Doesn't allow duplications for values mapped to key.
     * <p>
     * Uses Redis native commands for entry expiration and not a scheduled eviction task.
     * <p>
     * Requires <b>Redis 7.4.0 and higher.</b>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @return SetMultimapCache object
     */
    <K, V> RSetMultimapCacheNative<K, V> getSetMultimapCacheNative(String name);

    /**
     * Returns Set based Multimap instance by name
     * using provided codec for both map keys and values.
     * Supports key-entry eviction with a given TTL value.
     * Doesn't allow duplications for values mapped to key.
     * <p>
     * Uses Redis native commands for entry expiration and not a scheduled eviction task.
     * <p>
     * Requires <b>Redis 7.4.0 and higher.</b>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for keys and values
     * @return SetMultimapCache object
     */
    <K, V> RSetMultimapCacheNative<K, V> getSetMultimapCacheNative(String name, Codec codec);

    /**
     * Returns Set based Multimap instance with specified <code>options</code>.
     * Supports key-entry eviction with a given TTL value.
     * Doesn't allow duplications for values mapped to key.
     * <p>
     * Uses Redis native commands for entry expiration and not a scheduled eviction task.
     * <p>
     * Requires <b>Redis 7.4.0 and higher.</b>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param options instance options
     * @return SetMultimapCache object
     */
    <K, V> RSetMultimapCacheNative<K, V> getSetMultimapCacheNative(PlainOptions options);

    /**
     * Returns semaphore instance by name
     *
     * @param name name of object
     * @return Semaphore object
     */
    RSemaphore getSemaphore(String name);

    /**
     * Returns semaphore instance with specified <code>options</code>.
     *
     * @param options instance options
     * @return Semaphore object
     */
    RSemaphore getSemaphore(CommonOptions options);
    
    /**
     * Returns semaphore instance by name.
     * Supports lease time parameter for each acquired permit.
     * 
     * @param name name of object
     * @return PermitExpirableSemaphore object
     */
    RPermitExpirableSemaphore getPermitExpirableSemaphore(String name);

    /**
     * Returns semaphore instance with specified <code>options</code>.
     * Supports lease time parameter for each acquired permit.
     *
     * @param options instance options
     * @return PermitExpirableSemaphore object
     */
    RPermitExpirableSemaphore getPermitExpirableSemaphore(CommonOptions options);

    /**
     * Returns Lock instance by name.
     * <p>
     * Implements a <b>non-fair</b> locking so doesn't guarantees an acquire order by threads.
     * <p>
     * To increase reliability during failover, all operations wait for propagation to all Redis slaves.
     *
     * @param name name of object
     * @return Lock object
     */
    RLock getLock(String name);

    /**
     * Returns Lock instance with specified <code>options</code>.
     * <p>
     * Implements a <b>non-fair</b> locking so doesn't guarantees an acquire order by threads.
     * <p>
     * To increase reliability during failover, all operations wait for propagation to all Redis slaves.
     *
     * @param options instance options
     * @return Lock object
     */
    RLock getLock(CommonOptions options);

    /**
     * Returns Spin lock instance by name.
     * <p>
     * Implements a <b>non-fair</b> locking so doesn't guarantees an acquire order by threads.
     * <p>
     * Lock doesn't use a pub/sub mechanism
     *
     * @param name name of object
     * @return Lock object
     */
    RLock getSpinLock(String name);

    /**
     * Returns Spin lock instance by name with specified back off options.
     * <p>
     * Implements a <b>non-fair</b> locking so doesn't guarantees an acquire order by threads.
     * <p>
     * Lock doesn't use a pub/sub mechanism
     *
     * @param name name of object
     * @return Lock object
     */
    RLock getSpinLock(String name, LockOptions.BackOff backOff);

    /**
     * Returns Fenced Lock instance by name.
     * <p>
     * Implements a <b>non-fair</b> locking so doesn't guarantee an acquire order by threads.
     *
     * @param name name of object
     * @return Lock object
     */
    RFencedLock getFencedLock(String name);

    /**
     * Returns Fenced Lock instance with specified <code>options</code>..
     * <p>
     * Implements a <b>non-fair</b> locking so doesn't guarantee an acquire order by threads.
     *
     * @param options instance options
     * @return Lock object
     */
    RFencedLock getFencedLock(CommonOptions options);

    /**
     * Returns MultiLock instance associated with specified <code>locks</code>
     * 
     * @param locks collection of locks
     * @return MultiLock object
     */
    RLock getMultiLock(RLock... locks);
    /**
     * Returns RedissonFasterMultiLock instance associated with specified <code>group</code> and <code>values</code>
     *
     * @param group the group of values
     * @param values lock values
     * @return BatchLock object
     */
    RLock getMultiLock(String group, Collection<Object> values);
    /*
     * Use getLock() or getFencedLock() method instead.
     */
    @Deprecated
    RLock getRedLock(RLock... locks);
    
    /**
     * Returns Lock instance by name.
     * <p>
     * Implements a <b>fair</b> locking so it guarantees an acquire order by threads.
     * <p>
     * To increase reliability during failover, all operations wait for propagation to all Redis slaves.
     * 
     * @param name name of object
     * @return Lock object
     */
    RLock getFairLock(String name);

    /**
     * Returns Lock instance with specified <code>options</code>.
     * <p>
     * Implements a <b>fair</b> locking so it guarantees an acquire order by threads.
     * <p>
     * To increase reliability during failover, all operations wait for propagation to all Redis slaves.
     *
     * @param options instance options
     * @return Lock object
     */
    RLock getFairLock(CommonOptions options);
    
    /**
     * Returns ReadWriteLock instance by name.
     * <p>
     * To increase reliability during failover, all operations wait for propagation to all Redis slaves.
     *
     * @param name name of object
     * @return Lock object
     */
    RReadWriteLock getReadWriteLock(String name);

    /**
     * Returns ReadWriteLock instance with specified <code>options</code>.
     * <p>
     * To increase reliability during failover, all operations wait for propagation to all Redis slaves.
     *
     * @param options instance options
     * @return Lock object
     */
    RReadWriteLock getReadWriteLock(CommonOptions options);

    /**
     * Returns set instance by name.
     * 
     * @param <V> type of value
     * @param name name of object
     * @return Set object
     */
    <V> RSet<V> getSet(String name);

    /**
     * Returns set instance by name
     * using provided codec for set objects.
     * 
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for values
     * @return Set object
     */
    <V> RSet<V> getSet(String name, Codec codec);

    /**
     * Returns set instance with specified <code>options</code>.
     *
     * @param <V> type of value
     * @param options instance options
     * @return Set object
     */
    <V> RSet<V> getSet(PlainOptions options);

    /**
     * Returns sorted set instance by name.
     * This sorted set uses comparator to sort objects.
     * 
     * @param <V> type of value
     * @param name name of object
     * @return SortedSet object
     */
    <V> RSortedSet<V> getSortedSet(String name);

    /**
     * Returns sorted set instance by name
     * using provided codec for sorted set objects.
     * This sorted set sorts objects using comparator.
     * 
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for values
     * @return SortedSet object
     */
    <V> RSortedSet<V> getSortedSet(String name, Codec codec);

    /**
     * Returns sorted set instance with specified <code>options</code>.
     * This sorted set uses comparator to sort objects.
     *
     * @param <V> type of value
     * @param options instance options
     * @return SortedSet object
     */
    <V> RSortedSet<V> getSortedSet(PlainOptions options);

    /**
     * Returns Redis Sorted Set instance by name.
     * This sorted set sorts objects by object score.
     * 
     * @param <V> type of value
     * @param name name of object
     * @return ScoredSortedSet object
     */
    <V> RScoredSortedSet<V> getScoredSortedSet(String name);

    /**
     * Returns Redis Sorted Set instance by name
     * using provided codec for sorted set objects.
     * This sorted set sorts objects by object score.
     * 
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for values
     * @return ScoredSortedSet object
     */
    <V> RScoredSortedSet<V> getScoredSortedSet(String name, Codec codec);

    /**
     * Returns Redis Sorted Set instance with specified <code>options</code>.
     * This sorted set sorts objects by object score.
     *
     * @param <V> type of value
     * @param options instance options
     * @return ScoredSortedSet object
     */
    <V> RScoredSortedSet<V> getScoredSortedSet(PlainOptions options);

    /**
     * Returns String based Redis Sorted Set instance by name.
     * All elements are inserted with the same score during addition,
     * in order to force lexicographical ordering
     * 
     * @param name name of object
     * @return LexSortedSet object
     */
    RLexSortedSet getLexSortedSet(String name);

    /**
     * Returns String based Redis Sorted Set instance with specified <code>options</code>.
     * All elements are inserted with the same score during addition,
     * in order to force lexicographical ordering
     *
     * @param options instance options
     * @return LexSortedSet object
     */
    RLexSortedSet getLexSortedSet(CommonOptions options);

    /**
     * Returns Sharded Topic instance by name.
     * <p>
     * Messages are delivered to message listeners connected to the same Topic.
     * <p>
     *
     * @param name name of object
     * @return Topic object
     */
    RShardedTopic getShardedTopic(String name);

    /**
     * Returns Sharded Topic instance by name using provided codec for messages.
     * <p>
     * Messages are delivered to message listeners connected to the same Topic.
     * <p>
     *
     * @param name name of object
     * @param codec codec for message
     * @return Topic object
     */
    RShardedTopic getShardedTopic(String name, Codec codec);

    /**
     * Returns Sharded Topic instance with specified <code>options</code>.
     * <p>
     * Messages are delivered to message listeners connected to the same Topic.
     * <p>
     *
     * @param options instance options
     * @return Topic object
     */
    RShardedTopic getShardedTopic(PlainOptions options);

    /**
     * Returns topic instance by name.
     * <p>
     * Messages are delivered to message listeners connected to the same Topic.
     * <p>
     *
     * @param name name of object
     * @return Topic object
     */
    RTopic getTopic(String name);

    /**
     * Returns topic instance by name
     * using provided codec for messages.
     * <p>
     * Messages are delivered to message listeners connected to the same Topic.
     * <p>
     *
     * @param name name of object
     * @param codec codec for message
     * @return Topic object
     */
    RTopic getTopic(String name, Codec codec);

    /**
     * Returns topic instance with specified <code>options</code>.
     * <p>
     * Messages are delivered to message listeners connected to the same Topic.
     * <p>
     *
     * @param options instance options
     * @return Topic object
     */
    RTopic getTopic(PlainOptions options);

    /**
     * Returns reliable topic instance by name.
     * <p>
     * Dedicated Redis connection is allocated per instance (subscriber) of this object.
     * Messages are delivered to all listeners attached to the same Redis setup.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     *
     * @param name name of object
     * @return ReliableTopic object
     */
    RReliableTopic getReliableTopic(String name);

    /**
     * Returns reliable topic instance by name
     * using provided codec for messages.
     * <p>
     * Dedicated Redis connection is allocated per instance (subscriber) of this object.
     * Messages are delivered to all listeners attached to the same Redis setup.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     *
     * @param name name of object
     * @param codec codec for message
     * @return ReliableTopic object
     */
    RReliableTopic getReliableTopic(String name, Codec codec);

    /**
     * Returns reliable topic instance with specified <code>options</code>.
     * <p>
     * Dedicated Redis connection is allocated per instance (subscriber) of this object.
     * Messages are delivered to all listeners attached to the same Redis setup.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     *
     * @param options instance options
     * @return ReliableTopic object
     */
    RReliableTopic getReliableTopic(PlainOptions options);

    /**
     * Returns topic instance satisfies by pattern name.
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     * 
     * @param pattern of the topic
     * @return PatterTopic object
     */
    RPatternTopic getPatternTopic(String pattern);

    /**
     * Returns topic instance satisfies by pattern name
     * using provided codec for messages.
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     * 
     * @param pattern of the topic
     * @param codec codec for message
     * @return PatterTopic object
     */
    RPatternTopic getPatternTopic(String pattern, Codec codec);

    /**
     * Returns topic instance satisfies pattern name and specified <code>options</code>..
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param options instance options
     * @return PatterTopic object
     */
    RPatternTopic getPatternTopic(PatternTopicOptions options);

    /**
     * Returns unbounded queue instance by name.
     *
     * @param <V> type of value
     * @param name of object
     * @return queue object
     */
    <V> RQueue<V> getQueue(String name);

    /**
     * Returns transfer queue instance by name.
     *
     * @param <V> type of values
     * @param name name of object
     * @return TransferQueue object
     */
    <V> RTransferQueue<V> getTransferQueue(String name);

    /**
     * Returns transfer queue instance by name
     * using provided codec for queue objects.
     *
     * @param <V> type of values
     * @param name name of object
     * @param codec code for values
     * @return TransferQueue object
     */
    <V> RTransferQueue<V> getTransferQueue(String name, Codec codec);

    /**
     * Returns transfer queue instance with specified <code>options</code>.
     *
     * @param <V> type of values
     * @param options instance options
     * @return TransferQueue object
     */
    <V> RTransferQueue<V> getTransferQueue(PlainOptions options);

    /**
     * Returns unbounded delayed queue instance by name.
     * <p>
     * Could be attached to destination queue only.
     * All elements are inserted with transfer delay to destination queue.
     * 
     * @param <V> type of value
     * @param destinationQueue destination queue
     * @return Delayed queue object
     */
    <V> RDelayedQueue<V> getDelayedQueue(RQueue<V> destinationQueue);

    /**
     * Returns unbounded queue instance by name
     * using provided codec for queue objects.
     *
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for message
     * @return Queue object
     */
    <V> RQueue<V> getQueue(String name, Codec codec);

    /**
     * Returns unbounded queue instance with specified <code>options</code>.
     *
     * @param <V> type of value
     * @param options instance options
     * @return queue object
     */
    <V> RQueue<V> getQueue(PlainOptions options);

    /**
     * Returns RingBuffer based queue.
     * 
     * @param <V> value type
     * @param name name of object
     * @return RingBuffer object
     */
    <V> RRingBuffer<V> getRingBuffer(String name);
    
    /**
     * Returns RingBuffer based queue.
     * 
     * @param <V> value type
     * @param name name of object
     * @param codec codec for values
     * @return RingBuffer object
     */
    <V> RRingBuffer<V> getRingBuffer(String name, Codec codec);

    /**
     * Returns RingBuffer based queue instance with specified <code>options</code>.
     *
     * @param <V> value type
     * @param options instance options
     * @return RingBuffer object
     */
    <V> RRingBuffer<V> getRingBuffer(PlainOptions options);

    /**
     * Returns priority unbounded queue instance by name.
     * It uses comparator to sort objects.
     *
     * @param <V> type of value
     * @param name of object
     * @return Queue object
     */
    <V> RPriorityQueue<V> getPriorityQueue(String name);
    
    /**
     * Returns priority unbounded queue instance by name
     * using provided codec for queue objects.
     * It uses comparator to sort objects.
     *
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for message
     * @return Queue object
     */
    <V> RPriorityQueue<V> getPriorityQueue(String name, Codec codec);

    <V> RPriorityQueue<V> getPriorityQueue(PlainOptions options);

    /**
     * Returns unbounded priority blocking queue instance by name.
     * It uses comparator to sort objects.
     *
     * @param <V> type of value
     * @param name of object
     * @return Queue object
     */
    <V> RPriorityBlockingQueue<V> getPriorityBlockingQueue(String name);
    
    /**
     * Returns unbounded priority blocking queue instance by name
     * using provided codec for queue objects.
     * It uses comparator to sort objects.
     *
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for message
     * @return Queue object
     */
    <V> RPriorityBlockingQueue<V> getPriorityBlockingQueue(String name, Codec codec);

    /**
     * Returns unbounded priority blocking queue instance with specified <code>options</code>.
     * It uses comparator to sort objects.
     *
     * @param <V> type of value
     * @param options instance options
     * @return Queue object
     */
    <V> RPriorityBlockingQueue<V> getPriorityBlockingQueue(PlainOptions options);

    /**
     * Returns unbounded priority blocking deque instance by name.
     * It uses comparator to sort objects.
     *
     * @param <V> type of value
     * @param name of object
     * @return Queue object
     */
    <V> RPriorityBlockingDeque<V> getPriorityBlockingDeque(String name);
    
    /**
     * Returns unbounded priority blocking deque instance by name
     * using provided codec for queue objects.
     * It uses comparator to sort objects.
     *
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for message
     * @return Queue object
     */
    <V> RPriorityBlockingDeque<V> getPriorityBlockingDeque(String name, Codec codec);

    /**
     * Returns unbounded priority blocking deque instance with specified <code>options</code>.
     * It uses comparator to sort objects.
     *
     * @param <V> type of value
     * @param options instance options
     * @return Queue object
     */
    <V> RPriorityBlockingDeque<V> getPriorityBlockingDeque(PlainOptions options);
    
    /**
     * Returns priority unbounded deque instance by name.
     * It uses comparator to sort objects.
     *
     * @param <V> type of value
     * @param name of object
     * @return Queue object
     */
    <V> RPriorityDeque<V> getPriorityDeque(String name);
    
    /**
     * Returns priority unbounded deque instance by name
     * using provided codec for queue objects.
     * It uses comparator to sort objects.
     *
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for message
     * @return Queue object
     */
    <V> RPriorityDeque<V> getPriorityDeque(String name, Codec codec);

    /**
     * Returns priority unbounded deque instance with specified <code>options</code>.
     * It uses comparator to sort objects.
     *
     * @param <V> type of value
     * @param options instance options
     * @return Queue object
     */
    <V> RPriorityDeque<V> getPriorityDeque(PlainOptions options);
    
    /**
     * Returns unbounded blocking queue instance by name.
     * 
     * @param <V> type of value
     * @param name name of object
     * @return BlockingQueue object
     */
    <V> RBlockingQueue<V> getBlockingQueue(String name);

    /**
     * Returns unbounded blocking queue instance by name
     * using provided codec for queue objects.
     * 
     * @param <V> type of value
     * @param name name of queue
     * @param codec queue objects codec
     * @return BlockingQueue object
     */
    <V> RBlockingQueue<V> getBlockingQueue(String name, Codec codec);

    /**
     * Returns unbounded blocking queue instance with specified <code>options</code>.
     *
     * @param <V> type of value
     * @param options instance options
     * @return BlockingQueue object
     */
    <V> RBlockingQueue<V> getBlockingQueue(PlainOptions options);

    /**
     * Returns bounded blocking queue instance by name.
     *
     * @param <V> type of value
     * @param name of queue
     * @return BoundedBlockingQueue object
     */
    <V> RBoundedBlockingQueue<V> getBoundedBlockingQueue(String name);

    /**
     * Returns bounded blocking queue instance by name
     * using provided codec for queue objects.
     * 
     * @param <V> type of value
     * @param name name of queue
     * @param codec codec for values
     * @return BoundedBlockingQueue object
     */
    <V> RBoundedBlockingQueue<V> getBoundedBlockingQueue(String name, Codec codec);

    /**
     * Returns bounded blocking queue instance with specified <code>options</code>.
     *
     * @param <V> type of value
     * @param options instance options
     * @return BoundedBlockingQueue object
     */
    <V> RBoundedBlockingQueue<V> getBoundedBlockingQueue(PlainOptions options);

    /**
     * Returns unbounded deque instance by name.
     *
     * @param <V> type of value
     * @param name name of object
     * @return Deque object
     */
    <V> RDeque<V> getDeque(String name);

    /**
     * Returns unbounded deque instance by name
     * using provided codec for deque objects.
     * 
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for values
     * @return Deque object
     */
    <V> RDeque<V> getDeque(String name, Codec codec);

    /**
     * Returns unbounded deque instance with specified <code>options</code>.
     *
     * @param <V> type of value
     * @param options instance options
     * @return Deque object
     */
    <V> RDeque<V> getDeque(PlainOptions options);

    /**
     * Returns unbounded blocking deque instance by name.
     *
     * @param <V> type of value
     * @param name name of object
     * @return BlockingDeque object
     */
    <V> RBlockingDeque<V> getBlockingDeque(String name);

    /**
     * Returns unbounded blocking deque instance by name
     * using provided codec for deque objects.
     * 
     * @param <V> type of value
     * @param name name of object
     * @param codec deque objects codec
     * @return BlockingDeque object
     */
    <V> RBlockingDeque<V> getBlockingDeque(String name, Codec codec);

    /**
     * Returns unbounded blocking deque instance with specified <code>options</code>.
     *
     * @param <V> type of value
     * @param options instance options
     * @return BlockingDeque object
     */
    <V> RBlockingDeque<V> getBlockingDeque(PlainOptions options);

    /**
     * Returns atomicLong instance by name.
     *
     * @param name name of object
     * @return AtomicLong object
     */
    RAtomicLong getAtomicLong(String name);

    /**
     * Returns atomicLong instance with specified <code>options</code>.
     *
     * @param options instance options
     * @return AtomicLong object
     */
    RAtomicLong getAtomicLong(CommonOptions options);

    /**
     * Returns atomicDouble instance by name.
     *
     * @param name name of object
     * @return AtomicDouble object
     */
    RAtomicDouble getAtomicDouble(String name);

    /**
     * Returns atomicDouble instance with specified <code>options</code>.
     *
     * @param options instance options
     * @return AtomicDouble object
     */
    RAtomicDouble getAtomicDouble(CommonOptions options);

    /**
     * Returns LongAdder instances by name.
     * 
     * @param name name of object
     * @return LongAdder object
     */
    RLongAdder getLongAdder(String name);

    /**
     * Returns LongAdder instances with specified <code>options</code>.
     *
     * @param options instance options
     * @return LongAdder object
     */
    RLongAdder getLongAdder(CommonOptions options);

    /**
     * Returns DoubleAdder instances by name.
     * 
     * @param name name of object
     * @return LongAdder object
     */
    RDoubleAdder getDoubleAdder(String name);

    /**
     * Returns DoubleAdder instances with specified <code>options</code>.
     *
     * @param options instance options
     * @return LongAdder object
     */
    RDoubleAdder getDoubleAdder(CommonOptions options);

    /**
     * Returns countDownLatch instance by name.
     *
     * @param name name of object
     * @return CountDownLatch object
     */
    RCountDownLatch getCountDownLatch(String name);

    /**
     * Returns countDownLatch instance with specified <code>options</code>.
     *
     * @param options instance options
     * @return CountDownLatch object
     */
    RCountDownLatch getCountDownLatch(CommonOptions options);

    /**
     * Returns bitSet instance by name.
     *
     * @param name name of object
     * @return BitSet object
     */
    RBitSet getBitSet(String name);

    /**
     * Returns bitSet instance with specified <code>options</code>.
     *
     * @param options instance options
     * @return BitSet object
     */
    RBitSet getBitSet(CommonOptions options);

    /**
     * Returns bloom filter instance by name.
     * 
     * @param <V> type of value
     * @param name name of object
     * @return BloomFilter object
     */
    <V> RBloomFilter<V> getBloomFilter(String name);

    /**
     * Returns bloom filter instance by name
     * using provided codec for objects.
     *
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for values
     * @return BloomFilter object
     */
    <V> RBloomFilter<V> getBloomFilter(String name, Codec codec);

    /**
     * Returns bloom filter instance with specified <code>options</code>.
     *
     * @param <V> type of value
     * @param options instance options
     * @return BloomFilter object
     */
    <V> RBloomFilter<V> getBloomFilter(PlainOptions options);

    /**
     * Returns id generator instance by name.
     *
     * @param name name of object
     * @return IdGenerator object
     */
    RIdGenerator getIdGenerator(String name);

    /**
     * Returns id generator instance with specified <code>options</code>.
     *
     * @param options instance options
     * @return IdGenerator object
     */
    RIdGenerator getIdGenerator(CommonOptions options);

    /**
     * Returns API for Redis Function feature
     *
     * @return function object
     */
    RFunction getFunction();

    /**
     * Returns API for Redis Function feature using provided codec
     *
     * @param codec codec for params and result
     * @return function interface
     */
    RFunction getFunction(Codec codec);

    /**
     * Returns interface for Redis Function feature with specified <code>options</code>.
     *
     * @param options instance options
     * @return function object
     */
    RFunction getFunction(OptionalOptions options);

    /**
     * Returns script operations object
     *
     * @return Script object
     */
    RScript getScript();
    
    /**
     * Returns script operations object using provided codec.
     * 
     * @param codec codec for params and result
     * @return Script object
     */
    RScript getScript(Codec codec);

    /**
     * Returns script operations object with specified <code>options</code>.
     *
     * @param options instance options
     * @return Script object
     */
    RScript getScript(OptionalOptions options);

    /**
     * Returns ScheduledExecutorService by name
     * 
     * @param name name of object
     * @return ScheduledExecutorService object
     */
    RScheduledExecutorService getExecutorService(String name);

    /**
     * Use {@link #getExecutorService(org.redisson.api.options.ExecutorOptions)} instead
     * 
     * @param name name of object
     * @param options options for executor
     * @return ScheduledExecutorService object
     */
    @Deprecated
    RScheduledExecutorService getExecutorService(String name, ExecutorOptions options);

    /**
     * Returns ScheduledExecutorService by name 
     * using provided codec for task, response and request serialization
     * 
     * @param name name of object
     * @param codec codec for task, response and request
     * @return ScheduledExecutorService object
     * @since 2.8.2
     */
    RScheduledExecutorService getExecutorService(String name, Codec codec);

    /**
     * Use {@link #getExecutorService(org.redisson.api.options.ExecutorOptions)} instead
     *
     * @param name name of object
     * @param codec codec for task, response and request
     * @param options options for executor
     * @return ScheduledExecutorService object
     */
    @Deprecated
    RScheduledExecutorService getExecutorService(String name, Codec codec, ExecutorOptions options);

    /**
     * Returns ScheduledExecutorService with defined options
     * <p>
     * Usage examples:
     * <pre>
     * RScheduledExecutorService service = redisson.getExecutorService(
     *                                                  ExecutorOptions.name("test")
     *                                                  .taskRetryInterval(Duration.ofSeconds(60)));
     * </pre>
     *
     * @param options options instance
     * @return ScheduledExecutorService object
     */
    RScheduledExecutorService getExecutorService(org.redisson.api.options.ExecutorOptions options);
    
    /**
     * Returns object for remote operations prefixed with the default name (redisson_remote_service)
     * 
     * @return RemoteService object
     */
    @Deprecated
    RRemoteService getRemoteService();
    
    /**
     * Returns object for remote operations prefixed with the default name (redisson_remote_service)
     * and uses provided codec for method arguments and result.
     * 
     * @param codec codec for response and request
     * @return RemoteService object
     */
    @Deprecated
    RRemoteService getRemoteService(Codec codec);

    /**
     * Returns object for remote operations prefixed with the specified name
     *
     * @param name the name used as the Redis key prefix for the services
     * @return RemoteService object
     */
    RRemoteService getRemoteService(String name);
    
    /**
     * Returns object for remote operations prefixed with the specified name
     * and uses provided codec for method arguments and result.
     *
     * @param name the name used as the Redis key prefix for the services
     * @param codec codec for response and request
     * @return RemoteService object
     */
    RRemoteService getRemoteService(String name, Codec codec);

    /**
     * Returns object for remote operations prefixed with specified <code>options</code>.
     *
     * @param options instance options
     * @return RemoteService object
     */
    RRemoteService getRemoteService(PlainOptions options);

    /**
     * Creates transaction with <b>READ_COMMITTED</b> isolation level.
     * 
     * @param options transaction configuration
     * @return Transaction object
     */
    RTransaction createTransaction(TransactionOptions options);

    /**
     * Creates batch object which could be executed later 
     * with collected group of commands in pipeline mode.
     * <p>
     * See <a href="http://redis.io/topics/pipelining">http://redis.io/topics/pipelining</a>
     *
     * @param options batch configuration
     * @return Batch object
     */
    RBatch createBatch(BatchOptions options);

    /**
     * Creates batch object which could be executed later 
     * with collected group of commands in pipeline mode.
     * <p>
     * See <a href="http://redis.io/topics/pipelining">http://redis.io/topics/pipelining</a>
     *
     * @return Batch object
     */
    RBatch createBatch();
    
    /**
     * Returns interface with methods for Redis keys.
     * Each of Redis/Redisson object associated with own key
     *
     * @return Keys object
     */
    RKeys getKeys();

    /**
     * Returns interface for operations over Redis keys with specified <code>options</code>.
     * Each of Redis/Redisson object is associated with own key.
     *
     * @return Keys object
     */
    RKeys getKeys(KeysOptions options);

    /**
     * Returns Live Object Service which is used to store Java objects
     * 
     * @return LiveObjectService object
     */
    RLiveObjectService getLiveObjectService();

    /**
     * Returns Live Object Service which is used to store Java objects
     * with specified <code>options</code>.
     *
     * @return LiveObjectService object
     */
    RLiveObjectService getLiveObjectService(LiveObjectOptions options);

    /**
     * Returns client side caching facade interface with the specified <code>options</code>.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     * <p>
     * <strong>
     * NOTE: client side caching feature invalidates whole Map per entry change which is ineffective.
     * Use local cached <a href="https://redisson.org/docs/data-and-services/collections/#eviction-local-cache-and-data-partitioning">Map</a>, <a href="https://redisson.org/docs/data-and-services/collections/#local-cache">JSON Store</a> instead.
     * </strong>
     *
     * @param options client cache options
     * @return Client side caching instance
     */
    RClientSideCaching getClientSideCaching(ClientSideCachingOptions options);

    /**
     * Returns RxJava Redisson instance
     *
     * @return redisson instance
     */
    RedissonRxClient rxJava();

    /**
     * Returns Reactive Redisson instance
     *
     * @return redisson instance
     */
    RedissonReactiveClient reactive();

    /**
     * Shutdown Redisson instance but <b>NOT</b> Redis server
     * 
     * This equates to invoke shutdown(0, 2, TimeUnit.SECONDS);
     */
    void shutdown();
    
    /**
     * Shuts down Redisson instance but <b>NOT</b> Redis server
     * 
     * Shutdown ensures that no tasks are submitted for <i>'the quiet period'</i>
     * (usually a couple seconds) before it shuts itself down.  If a task is submitted during the quiet period,
     * it is guaranteed to be accepted and the quiet period will start over.
     * 
     * @param quietPeriod the quiet period as described in the documentation
     * @param timeout     the maximum amount of time to wait until the executor is {@linkplain #shutdown()}
     *                    regardless if a task was submitted during the quiet period
     * @param unit        the unit of {@code quietPeriod} and {@code timeout}
     */
    void shutdown(long quietPeriod, long timeout, TimeUnit unit);

    /**
     * Allows to get configuration provided
     * during Redisson instance creation. Further changes on
     * this object not affect Redisson instance.
     *
     * @return Config object
     */
    Config getConfig();

    /**
     * Returns API to manage Redis nodes
     *
     * @see RedisNodes#CLUSTER
     * @see RedisNodes#MASTER_SLAVE
     * @see RedisNodes#SENTINEL_MASTER_SLAVE
     * @see RedisNodes#SINGLE
     *
     * @param nodes Redis nodes API class
     * @param <T> type of Redis nodes API
     * @return Redis nodes API object
     */
    <T extends BaseRedisNodes> T getRedisNodes(RedisNodes<T> nodes);

    /*
     * Use getRedisNodes() method instead
     */
    @Deprecated
    NodesGroup<Node> getNodesGroup();

    /*
     * Use getRedisNodes() method instead
     */
    @Deprecated
    ClusterNodesGroup getClusterNodesGroup();

    /**
     * Returns {@code true} if this Redisson instance has been shut down.
     *
     * @return {@code true} if this Redisson instance has been shut down overwise <code>false</code>
     */
    boolean isShutdown();

    /**
     * Returns {@code true} if this Redisson instance was started to be shutdown
     * or was shutdown {@link #isShutdown()} already.
     *
     * @return {@code true} if this Redisson instance was started to be shutdown
     * or was shutdown {@link #isShutdown()} already.
     */
    boolean isShuttingDown();

    /**
     * Returns id of this Redisson instance
     * 
     * @return id
     */
    String getId();

}
