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
package org.redisson.core;

import java.util.List;

import io.netty.util.concurrent.Future;

/**
 * Interface for using pipeline feature.
 *
 * All methods invocations via async objects
 * which have gotten from this interface are batched
 * to separate queue and could be executed later
 * with <code>execute()</code> or <code>executeAsync()</code> methods.
 *
 *
 * @author Nikita Koksharov
 *
 */
public interface RBatch {

    /**
     * Returns object holder by name
     *
     * @param name of object
     * @return
     */
    <V> RBucketAsync<V> getBucket(String name);

    /**
     * Returns HyperLogLog object
     *
     * @param name of object
     * @return
     */
    <V> RHyperLogLogAsync<V> getHyperLogLog(String name);

    /**
     * Returns list instance by name.
     *
     * @param name of list
     * @return
     */
    <V> RListAsync<V> getList(String name);

    /**
     * Returns map instance by name.
     *
     * @param name of map
     * @return
     */
    <K, V> RMapAsync<K, V> getMap(String name);

    /**
     * Returns set instance by name.
     *
     * @param name of set
     * @return
     */
    <V> RSetAsync<V> getSet(String name);

    /**
     * Returns topic instance by name.
     *
     * @param name of topic
     * @return
     */
    <M> RTopicAsync<M> getTopic(String name);

    /**
     * Returns queue instance by name.
     *
     * @param name of queue
     * @return
     */
    <V> RQueueAsync<V> getQueue(String name);

    /**
     * Returns blocking queue instance by name.
     *
     * @param name of queue
     * @return
     */
    <V> RBlockingQueueAsync<V> getBlockingQueue(String name);

    /**
     * Returns deque instance by name.
     *
     * @param name of deque
     * @return
     */
    <V> RDequeAsync<V> getDequeAsync(String name);

    /**
     * Returns "atomic long" instance by name.
     *
     * @param name of the "atomic long"
     * @return
     */
    RAtomicLongAsync getAtomicLongAsync(String name);

    /**
     * Returns script operations object
     *
     * @return
     */
    RScriptAsync getScript();

    /**
     * Returns keys operations.
     * Each of Redis/Redisson object associated with own key
     *
     * @return
     */
    RKeysAsync getKeys();

    /**
     * Executes all operations accumulated during async methods invocations.
     *
     * In cluster configurations operations grouped by slot ids
     * so may be executed on different servers. Thus command execution order could be changed
     *
     * @return
     */
    List<?> execute();

    /**
     * Executes all operations accumulated during async methods invocations asynchronously.
     *
     * In cluster configurations operations grouped by slot ids
     * so may be executed on different servers. Thus command execution order could be changed
     *
     * @return
     */
    Future<List<?>> executeAsync();

}
