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

import org.redisson.core.*;

public interface RedissonClient {

    /**
     * Returns object holder by name
     *
     * @param name of object
     * @return
     */
    <V> RBucket<V> getBucket(String name);

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
     * @param name of the distributed topic
     * @return
     */
    <M> RTopic<M> getTopic(String name);

    /**
     * Returns queue instance by name.
     *
     * @param name of queue
     * @return
     */
    <V> RQueue<V> getQueue(String name);

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

}
