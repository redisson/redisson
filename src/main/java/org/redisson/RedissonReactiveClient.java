/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson;

import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.core.RBucketReactive;
import org.redisson.core.RHyperLogLogReactive;
import org.redisson.core.RListReactive;
import org.redisson.core.RMap;
import org.redisson.core.RMapReactive;
import org.redisson.core.RScoredSortedSetReactive;
import org.redisson.core.RSetReactive;

public interface RedissonReactiveClient {

    /**
     * Returns object holder by name
     *
     * @param name of object
     * @return
     */
    <V> RBucketReactive<V> getBucket(String name);

    <V> RBucketReactive<V> getBucket(String name, Codec codec);

    /**
     * Returns a list of object holder by a key pattern
     */
    <V> List<RBucketReactive<V>> findBuckets(String pattern);

    /**
     * Returns HyperLogLog object
     *
     * @param name of object
     * @return
     */
    <V> RHyperLogLogReactive<V> getHyperLogLog(String name);

    <V> RHyperLogLogReactive<V> getHyperLogLog(String name, Codec codec);

    /**
     * Returns list instance by name.
     *
     * @param name of list
     * @return
     */
    <V> RListReactive<V> getList(String name);

    <V> RListReactive<V> getList(String name, Codec codec);

    /**
     * Returns map instance by name.
     *
     * @param name of map
     * @return
     */
    <K, V> RMapReactive<K, V> getMap(String name);

    <K, V> RMapReactive<K, V> getMap(String name, Codec codec);

    /**
     * Returns set instance by name.
     *
     * @param name of set
     * @return
     */
    <V> RSetReactive<V> getSet(String name);

    <V> RSetReactive<V> getSet(String name, Codec codec);

    /**
     * Returns Redis Sorted Set instance by name
     *
     * @param name
     * @return
     */
    <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name);

    <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name, Codec codec);

//    /**
//     * Returns String based Redis Sorted Set instance by name
//     * All elements are inserted with the same score during addition,
//     * in order to force lexicographical ordering
//     *
//     * @param name
//     * @return
//     */
//    RLexSortedSet getLexSortedSet(String name);
//
//    /**
//     * Returns topic instance by name.
//     *
//     * @param name of topic
//     * @return
//     */
//    <M> RTopic<M> getTopic(String name);
//
//    <M> RTopic<M> getTopic(String name, Codec codec);
//
//    /**
//     * Returns topic instance satisfies by pattern name.
//     *
//     *  Supported glob-style patterns:
//     *    h?llo subscribes to hello, hallo and hxllo
//     *    h*llo subscribes to hllo and heeeello
//     *    h[ae]llo subscribes to hello and hallo, but not hillo
//     *
//     * @param pattern of the topic
//     * @return
//     */
//    <M> RPatternTopic<M> getPatternTopic(String pattern);
//
//    <M> RPatternTopic<M> getPatternTopic(String pattern, Codec codec);
//
//    /**
//     * Returns queue instance by name.
//     *
//     * @param name of queue
//     * @return
//     */
//    <V> RQueue<V> getQueue(String name);
//
//    <V> RQueue<V> getQueue(String name, Codec codec);
//
//    /**
//     * Returns blocking queue instance by name.
//     *
//     * @param name of queue
//     * @return
//     */
//    <V> RBlockingQueue<V> getBlockingQueue(String name);
//
//    <V> RBlockingQueue<V> getBlockingQueue(String name, Codec codec);
//
//    /**
//     * Returns deque instance by name.
//     *
//     * @param name of deque
//     * @return
//     */
//    <V> RDeque<V> getDeque(String name);
//
//    <V> RDeque<V> getDeque(String name, Codec codec);
//
//    /**
//     * Returns "atomic long" instance by name.
//     *
//     * @param name of the "atomic long"
//     * @return
//     */
//    RAtomicLong getAtomicLong(String name);
//
//    /**
//     * Returns "count down latch" instance by name.
//     *
//     * @param name of the "count down latch"
//     * @return
//     */
//    RCountDownLatch getCountDownLatch(String name);
//
//    RBitSet getBitSet(String name);
//
//    /**
//     * Returns script operations object
//     *
//     * @return
//     */
//    RScript getScript();
//
//    /**
//     * Return batch object which executes group of
//     * command in pipeline.
//     *
//     * See <a href="http://redis.io/topics/pipelining">http://redis.io/topics/pipelining</a>
//     *
//     * @return
//     */
//    RBatch createBatch();
//
//    /**
//     * Returns keys operations.
//     * Each of Redis/Redisson object associated with own key
//     *
//     * @return
//     */
//    RKeys getKeys();

    /**
     * Shuts down Redisson instance <b>NOT</b> Redis server
     */
    void shutdown();

//    /**
//     * Allows to get configuration provided
//     * during Redisson instance creation. Further changes on
//     * this object not affect Redisson instance.
//     *
//     * @return Config object
//     */
//    Config getConfig();
//
//    /**
//     * Find keys by key search pattern
//     *
//     *  Supported glob-style patterns:
//     *    h?llo subscribes to hello, hallo and hxllo
//     *    h*llo subscribes to hllo and heeeello
//     *    h[ae]llo subscribes to hello and hallo, but not hillo
//     *
//     * @param pattern
//     * @return
//     */
//    // use RKeys.findKeysByPattern
//    @Deprecated
//    Collection<String> findKeysByPattern(String pattern);
//
//    /**
//     * Find keys by key search pattern in async mode
//     *
//     *  Supported glob-style patterns:
//     *    h?llo subscribes to hello, hallo and hxllo
//     *    h*llo subscribes to hllo and heeeello
//     *    h[ae]llo subscribes to hello and hallo, but not hillo
//     *
//     * @param pattern
//     * @return
//     */
//    // use RKeys.findKeysByPatternAsync
//    @Deprecated
//    Future<Collection<String>> findKeysByPatternAsync(String pattern);
//
//    /**
//     * Delete multiple objects by a key pattern
//     *
//     *  Supported glob-style patterns:
//     *    h?llo subscribes to hello, hallo and hxllo
//     *    h*llo subscribes to hllo and heeeello
//     *    h[ae]llo subscribes to hello and hallo, but not hillo
//     *
//     * @param pattern
//     * @return
//     */
//    // use RKeys.deleteByPattern
//    @Deprecated
//    long deleteByPattern(String pattern);
//
//    /**
//     * Delete multiple objects by a key pattern in async mode
//     *
//     *  Supported glob-style patterns:
//     *    h?llo subscribes to hello, hallo and hxllo
//     *    h*llo subscribes to hllo and heeeello
//     *    h[ae]llo subscribes to hello and hallo, but not hillo
//     *
//     * @param pattern
//     * @return
//     */
//    // use RKeys.deleteByPatternAsync
//    @Deprecated
//    Future<Long> deleteByPatternAsync(String pattern);
//
//    /**
//     * Delete multiple objects by name
//     *
//     * @param keys - object names
//     * @return
//     */
//    // use RKeys.delete
//    @Deprecated
//    long delete(String ... keys);
//
//    /**
//     * Delete multiple objects by name in async mode
//     *
//     * @param keys - object names
//     * @return
//     */
//    // use RKeys.deleteAsync
//    @Deprecated
//    Future<Long> deleteAsync(String ... keys);
//
//    /**
//     * Get Redis nodes group for server operations
//     *
//     * @return
//     */
//    NodesGroup<Node> getNodesGroup();
//
//    /**
//     * Get Redis cluster nodes group for server operations
//     *
//     * @return
//     */
//    NodesGroup<ClusterNode> getClusterNodesGroup();
//
    /**
     * Delete all the keys of the currently selected database
     */
    void flushdb();

    /**
     * Delete all the keys of all the existing databases
     */
    void flushall();

}
