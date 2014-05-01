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
import org.redisson.core.RDeque;
import org.redisson.core.RList;
import org.redisson.core.RLock;
import org.redisson.core.RMap;
import org.redisson.core.RQueue;
import org.redisson.core.RSet;
import org.redisson.core.RSortedSet;
import org.redisson.core.RTopic;
import org.redisson.misc.ReferenceMap;
import org.redisson.misc.ReferenceMap.ReferenceType;
import org.redisson.misc.ReferenceMap.RemoveValueListener;

import com.lambdaworks.redis.RedisConnection;

/**
 * Main infrastructure class allows to get access
 * to all Redisson objects on top of Redis server.
 *
 * @author Nikita Koksharov
 *
 */
public class Redisson {

    private final RemoveValueListener listener = new RemoveValueListener() {

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
    private final ConcurrentMap<String, RedissonDeque> dequeMap = new ReferenceMap<String, RedissonDeque>(ReferenceType.STRONG, ReferenceType.SOFT);
    private final ConcurrentMap<String, RedissonSet> setsMap = new ReferenceMap<String, RedissonSet>(ReferenceType.STRONG, ReferenceType.SOFT);
    private final ConcurrentMap<String, RedissonSortedSet> sortedSetMap = new ReferenceMap<String, RedissonSortedSet>(ReferenceType.STRONG, ReferenceType.SOFT);
    private final ConcurrentMap<String, RedissonList> listsMap = new ReferenceMap<String, RedissonList>(ReferenceType.STRONG, ReferenceType.SOFT);
    private final ConcurrentMap<String, RedissonMap> mapsMap = new ReferenceMap<String, RedissonMap>(ReferenceType.STRONG, ReferenceType.SOFT);

    private final ConnectionManager connectionManager;
    private final Config config;

    private final UUID id = UUID.randomUUID();

    Redisson(Config config) {
        this.config = config;
        Config configCopy = new Config(config);
        connectionManager = new ConnectionManager(configCopy);
    }

    /**
     * Creates an Redisson instance
     *
     * @return Redisson instance
     */
    public static Redisson create() {
        Config config = new Config();
        config.addAddress("127.0.0.1:6379");
        return create(config);
    }

    /**
     * Creates an Redisson instance with configuration
     *
     * @param config
     * @return Redisson instance
     */
    public static Redisson create(Config config) {
        return new Redisson(config);
    }

    /**
     * Returns distributed list instance by name.
     *
     * @param name of the distributed list
     * @return distributed list
     */
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

    /**
     * Returns distributed map instance by name.
     *
     * @param name of the distributed map
     * @return distributed map
     */
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

    /**
     * Returns distributed lock instance by name.
     *
     * @param name of the distributed lock
     * @return distributed lock
     */
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

    /**
     * Returns distributed set instance by name.
     *
     * @param name of the distributed set
     * @return distributed set
     */
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

    /**
     * Returns distributed sorted set instance by name.
     *
     * @param name of the distributed set
     * @return distributed set
     */
    public <V> RSortedSet<V> getSortedSet(String name) {
        RedissonSortedSet<V> set = sortedSetMap.get(name);
        if (set == null) {
            set = new RedissonSortedSet<V>(connectionManager, name);
            RedissonSortedSet<V> oldSet = sortedSetMap.putIfAbsent(name, set);
            if (oldSet != null) {
                set = oldSet;
            }
        }

        return set;
    }

    /**
     * Returns distributed topic instance by name.
     *
     * @param name of the distributed topic
     * @return distributed topic
     */
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

    /**
     * Returns distributed queue instance by name.
     *
     * @param name of the distributed queue
     * @return distributed queue
     */
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

    /**
     * Returns distributed deque instance by name.
     *
     * @param name of the distributed queue
     * @return distributed queue
     */
    public <V> RDeque<V> getDeque(String name) {
        RedissonDeque<V> queue = dequeMap.get(name);
        if (queue == null) {
            queue = new RedissonDeque<V>(connectionManager, name);
            RedissonDeque<V> oldQueue = dequeMap.putIfAbsent(name, queue);
            if (oldQueue != null) {
                queue = oldQueue;
            }
        }

        return queue;
    }

    /**
     * Returns distributed "atomic long" instance by name.
     *
     * @param name of the distributed "atomic long"
     * @return distributed "atomic long"
     */
    public RAtomicLong getAtomicLong(String name) {
        RedissonAtomicLong atomicLong = atomicLongsMap.get(name);
        if (atomicLong == null) {
            atomicLong = new RedissonAtomicLong(connectionManager, name);
            RedissonAtomicLong oldAtomicLong = atomicLongsMap.putIfAbsent(name, atomicLong);
            if (oldAtomicLong != null) {
                atomicLong = oldAtomicLong;
            }
        }

        atomicLong.init();
        return atomicLong;

    }

    /**
     * Returns distributed "count down latch" instance by name.
     *
     * @param name of the distributed "count down latch"
     * @return distributed "count down latch"
     */
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

    /**
     * Shuts down Redisson instance <b>NOT</b> Redis server
     */
    public void shutdown() {
        connectionManager.shutdown();
    }

    /**
     * Allows to get configuration provided
     * during Redisson instance creation. Further changes on
     * this object not affect Redisson instance.
     *
     * @return Config object
     */
    public Config getConfig() {
        return config;
    }

    public void flushdb() {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            connection.flushdb();
        } finally {
            connectionManager.release(connection);
        }
    }

}

