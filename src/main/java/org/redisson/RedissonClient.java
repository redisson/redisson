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

import java.util.Collection;
import java.util.List;

import org.redisson.core.ClusterNode;
import org.redisson.core.Node;
import org.redisson.core.NodesGroup;
import org.redisson.core.RAtomicLong;
import org.redisson.core.RBatch;
import org.redisson.core.RBlockingQueue;
import org.redisson.core.RBucket;
import org.redisson.core.RCountDownLatch;
import org.redisson.core.RDeque;
import org.redisson.core.RHyperLogLog;
import org.redisson.core.RKeys;
import org.redisson.core.RList;
import org.redisson.core.RLock;
import org.redisson.core.RMap;
import org.redisson.core.RPatternTopic;
import org.redisson.core.RQueue;
import org.redisson.core.RScript;
import org.redisson.core.RSet;
import org.redisson.core.RSortedSet;
import org.redisson.core.RTopic;

import io.netty.util.concurrent.Future;

public interface RedissonClient {

    /**
     * Returns object holder by name
     *
     * @param name of object
     * @return
     */
    <V> RBucket<V> getBucket(String name);

    /**
     * Returns a list of object holder by a key pattern
     */
    <V> List<RBucket<V>> getBuckets(String pattern);

    /**
     * Returns HyperLogLog object
     *
     * @param name of object
     * @return
     */
    <V> RHyperLogLog<V> getHyperLogLog(String name);

    /**
     * Returns list instance by name.
     *
     * @param name of list
     * @return
     */
    <V> RList<V> getList(String name);

    /**
     * Returns map instance by name.
     *
     * @param name of map
     * @return
     */
    <K, V> RMap<K, V> getMap(String name);

    /**
     * Returns lock instance by name.
     *
     * @param name of lock
     * @return
     */
    RLock getLock(String name);

    /**
     * Returns set instance by name.
     *
     * @param name of set
     * @return
     */
    <V> RSet<V> getSet(String name);

    /**
     * Returns sorted set instance by name.
     *
     * @param name of sorted set
     * @return
     */
    <V> RSortedSet<V> getSortedSet(String name);

    /**
     * Returns topic instance by name.
     *
     * @param name of topic
     * @return
     */
    <M> RTopic<M> getTopic(String name);

    /**
     * Returns topic instance satisfies by pattern name.
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern of the topic
     * @return
     */
    <M> RPatternTopic<M> getPatternTopic(String pattern);

    /**
     * Returns queue instance by name.
     *
     * @param name of queue
     * @return
     */
    <V> RQueue<V> getQueue(String name);

    /**
     * Returns blocking queue instance by name.
     *
     * @param name of queue
     * @return
     */
    <V> RBlockingQueue<V> getBlockingQueue(String name);

    /**
     * Returns deque instance by name.
     *
     * @param name of deque
     * @return
     */
    <V> RDeque<V> getDeque(String name);

    /**
     * Returns "atomic long" instance by name.
     *
     * @param name of the "atomic long"
     * @return
     */
    RAtomicLong getAtomicLong(String name);

    /**
     * Returns "count down latch" instance by name.
     *
     * @param name of the "count down latch"
     * @return
     */
    RCountDownLatch getCountDownLatch(String name);

    /**
     * Returns script operations object
     *
     * @return
     */
    RScript getScript();

    /**
     * Return batch object which executes group of
     * command in pipeline.
     *
     * See <a href="http://redis.io/topics/pipelining">http://redis.io/topics/pipelining</a>
     *
     * @return
     */
    RBatch createBatch();

    /**
     * Returns keys operations.
     * Each of Redis/Redisson object associated with own key
     *
     * @return
     */
    RKeys getKeys();

    /**
     * Shuts down Redisson instance <b>NOT</b> Redis server
     */
    void shutdown();

    /**
     * Allows to get configuration provided
     * during Redisson instance creation. Further changes on
     * this object not affect Redisson instance.
     *
     * @return Config object
     */
    Config getConfig();

    /**
     * Find keys by key search pattern
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern
     * @return
     */
    // use RKeys.findKeysByPattern
    @Deprecated
    Collection<String> findKeysByPattern(String pattern);

    /**
     * Find keys by key search pattern in async mode
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern
     * @return
     */
    // use RKeys.findKeysByPatternAsync
    @Deprecated
    Future<Collection<String>> findKeysByPatternAsync(String pattern);

    /**
     * Delete multiple objects by a key pattern
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern
     * @return
     */
    // use RKeys.deleteByPattern
    @Deprecated
    long deleteByPattern(String pattern);

    /**
     * Delete multiple objects by a key pattern in async mode
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern
     * @return
     */
    // use RKeys.deleteByPatternAsync
    @Deprecated
    Future<Long> deleteByPatternAsync(String pattern);

    /**
     * Delete multiple objects by name
     *
     * @param keys - object names
     * @return
     */
    // use RKeys.delete
    @Deprecated
    long delete(String ... keys);

    /**
     * Delete multiple objects by name in async mode
     *
     * @param keys - object names
     * @return
     */
    // use RKeys.deleteAsync
    @Deprecated
    Future<Long> deleteAsync(String ... keys);

    /**
     * Get Redis nodes group for server operations
     *
     * @return
     */
    NodesGroup<Node> getNodesGroup();

    /**
     * Get Redis cluster nodes group for server operations
     *
     * @return
     */
    NodesGroup<ClusterNode> getClusterNodesGroup();

    /**
     * Delete all the keys of the currently selected database
     */
    void flushdb();

    /**
     * Delete all the keys of all the existing databases
     */
    void flushall();

}
