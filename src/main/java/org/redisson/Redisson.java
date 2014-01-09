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

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import org.redisson.connection.ConnectionManager;
import org.redisson.core.RAtomicLong;
import org.redisson.core.RCountDownLatch;
import org.redisson.core.RList;
import org.redisson.core.RLock;
import org.redisson.core.RMap;
import org.redisson.core.RQueue;
import org.redisson.core.RSet;
import org.redisson.core.RTopic;
import org.redisson.misc.ReferenceMap;
import org.redisson.misc.ReferenceMap.ReferenceType;
import org.redisson.misc.ReferenceMap.RemoveValueListener;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class Redisson {

    RemoveValueListener listener = new RemoveValueListener() {

        @Override
        public void onRemove(Object value) {
            if (value instanceof RedissonObject) {
                ((RedissonObject)value).close();
            }
        }

    };

    private final ConcurrentMap<String, RedissonCountDownLatch> latchesMap = new ReferenceMap<String, RedissonCountDownLatch>(ReferenceType.STRONG, ReferenceType.SOFT, listener);
    private final ConcurrentMap<String, RedissonTopic> topicsMap = new ReferenceMap<String, RedissonTopic>(ReferenceType.STRONG, ReferenceType.SOFT, listener);
    private final ConcurrentMap<String, RedissonLock> locksMap = new ReferenceMap<String, RedissonLock>(ReferenceType.STRONG, ReferenceType.SOFT, listener);

    private final ConcurrentMap<String, RedissonAtomicLong> atomicLongsMap = new ReferenceMap<String, RedissonAtomicLong>(ReferenceType.STRONG, ReferenceType.SOFT);
    private final ConcurrentMap<String, RedissonQueue> queuesMap = new ReferenceMap<String, RedissonQueue>(ReferenceType.STRONG, ReferenceType.SOFT);
    private final ConcurrentMap<String, RedissonSet> setsMap = new ReferenceMap<String, RedissonSet>(ReferenceType.STRONG, ReferenceType.SOFT);
    private final ConcurrentMap<String, RedissonList> listsMap = new ReferenceMap<String, RedissonList>(ReferenceType.STRONG, ReferenceType.SOFT);
    private final ConcurrentMap<String, RedissonMap> mapsMap = new ReferenceMap<String, RedissonMap>(ReferenceType.STRONG, ReferenceType.SOFT);

    private final ConnectionManager connectionManager;

    private final UUID id = UUID.randomUUID();

    Redisson(Config config) {
        connectionManager = new ConnectionManager(config);
    }

    public static Redisson create() {
        Config config = new Config();
        config.addAddress("127.0.0.1:6379");
        return create(config);
    }

    public static Redisson create(Config config) {
        Config configCopy = new Config(config);
        return new Redisson(configCopy);
    }

    public <V> RList<V> getList(String name) {
        RedissonList<V> list = listsMap.get(name);
        if (list == null) {
            list = new RedissonList<V>(connectionManager, name);
            RedissonList<V> oldList = listsMap.putIfAbsent(name, list);
            if (oldList != null) {
                list = oldList;
            }
        }

        return list;
    }

    public <K, V> RMap<K, V> getMap(String name) {
        RedissonMap<K, V> map = mapsMap.get(name);
        if (map == null) {
            map = new RedissonMap<K, V>(connectionManager, name);
            RedissonMap<K, V> oldMap = mapsMap.putIfAbsent(name, map);
            if (oldMap != null) {
                map = oldMap;
            }
        }

        return map;
    }

    public RLock getLock(String name) {
        RedissonLock lock = locksMap.get(name);
        if (lock == null) {
            lock = new RedissonLock(connectionManager, name, id);
            RedissonLock oldLock = locksMap.putIfAbsent(name, lock);
            if (oldLock != null) {
                lock = oldLock;
            }
        }

        lock.subscribe();
        return lock;
    }

    public <V> RSet<V> getSet(String name) {
        RedissonSet<V> set = setsMap.get(name);
        if (set == null) {
            set = new RedissonSet<V>(connectionManager, name);
            RedissonSet<V> oldSet = setsMap.putIfAbsent(name, set);
            if (oldSet != null) {
                set = oldSet;
            }
        }

        return set;
    }

    public <M> RTopic<M> getTopic(String name) {
        RedissonTopic<M> topic = topicsMap.get(name);
        if (topic == null) {
            topic = new RedissonTopic<M>(connectionManager, name);
            RedissonTopic<M> oldTopic = topicsMap.putIfAbsent(name, topic);
            if (oldTopic != null) {
                topic = oldTopic;
            }
        }

        topic.subscribe();
        return topic;

    }

    public <V> RQueue<V> getQueue(String name) {
        RedissonQueue<V> queue = queuesMap.get(name);
        if (queue == null) {
            queue = new RedissonQueue<V>(connectionManager, name);
            RedissonQueue<V> oldQueue = queuesMap.putIfAbsent(name, queue);
            if (oldQueue != null) {
                queue = oldQueue;
            }
        }

        return queue;
    }

    public RAtomicLong getAtomicLong(String name) {
        RedissonAtomicLong atomicLong = atomicLongsMap.get(name);
        if (atomicLong == null) {
            atomicLong = new RedissonAtomicLong(connectionManager, name);
            RedissonAtomicLong oldAtomicLong = atomicLongsMap.putIfAbsent(name, atomicLong);
            if (oldAtomicLong != null) {
                atomicLong = oldAtomicLong;
            }
        }

        return atomicLong;

    }

    public RCountDownLatch getCountDownLatch(String name) {
        RedissonCountDownLatch latch = latchesMap.get(name);
        if (latch == null) {
            latch = new RedissonCountDownLatch(connectionManager, name);
            RedissonCountDownLatch oldLatch = latchesMap.putIfAbsent(name, latch);
            if (oldLatch != null) {
                latch = oldLatch;
            }
        }

        latch.subscribe();
        return latch;
    }

    // TODO implement
//    public void getSemaphore() {
//    }

    public void shutdown() {
        connectionManager.shutdown();
    }

}

