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
    <V> RBucket<V> getBucket(String name);

    <V> RHyperLogLog<V> getHyperLogLog(String name);

    <V> RList<V> getList(String name);

    <K, V> RMap<K, V> getMap(String name);

    RLock getLock(String name);

    <V> RSet<V> getSet(String name);

    <V> RSortedSet<V> getSortedSet(String name);

    <M> RTopic<M> getTopic(String name);

    <V> RQueue<V> getQueue(String name);

    <V> RDeque<V> getDeque(String name);

    RAtomicLong getAtomicLong(String name);

    RCountDownLatch getCountDownLatch(String name);
}
