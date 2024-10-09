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

import org.redisson.client.codec.Codec;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface RClientSideCaching extends RDestroyable {

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
     * Returns map instance by name.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name name of object
     * @return Map object
     */
    <K, V> RMap<K, V> getMap(String name);

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
     * Returns unbounded queue instance by name.
     *
     * @param <V> type of value
     * @param name of object
     * @return queue object
     */
    <V> RQueue<V> getQueue(String name);

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

}
