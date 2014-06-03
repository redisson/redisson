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
import org.redisson.core.RBucket;
import org.redisson.core.RCountDownLatch;
import org.redisson.core.RDeque;
import org.redisson.core.RHyperLogLog;
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

    private final ConcurrentMap<String, RedissonCountDownLatch> latchesMap = new ReferenceMap<String, RedissonCountDownLatch>(ReferenceType.STRONG, ReferenceType.WEAK, listener);
    private final ConcurrentMap<String, RedissonLock> locksMap = new ReferenceMap<String, RedissonLock>(ReferenceType.STRONG, ReferenceType.WEAK, listener);

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

    public <V> RBucket<V> getBucket(String name) {
        return new RedissonBucket<V>(connectionManager, name);
    }

    public <V> RHyperLogLog<V> getHyperLogLog(String name) {
        return new RedissonHyperLogLog<V>(connectionManager, name);
    }

    /**
     * Returns distributed list instance by name.
     *
     * @param name of the distributed list
     * @return distributed list
     */
    public <V> RList<V> getList(String name) {
        return new RedissonList<V>(connectionManager, name);
    }

    /**
     * Returns distributed map instance by name.
     *
     * @param name of the distributed map
     * @return distributed map
     */
    public <K, V> RMap<K, V> getMap(String name) {
        return new RedissonMap<K, V>(connectionManager, name);
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

        return lock;
    }

    /**
     * Returns distributed set instance by name.
     *
     * @param name of the distributed set
     * @return distributed set
     */
    public <V> RSet<V> getSet(String name) {
        return new RedissonSet<V>(connectionManager, name);
    }

    /**
     * Returns distributed sorted set instance by name.
     *
     * @param name of the distributed set
     * @return distributed set
     */
    public <V> RSortedSet<V> getSortedSet(String name) {
        return new RedissonSortedSet<V>(connectionManager, name);
    }

    /**
     * Returns distributed topic instance by name.
     *
     * @param name of the distributed topic
     * @return distributed topic
     */
    public <M> RTopic<M> getTopic(String name) {
        return new RedissonTopic<M>(connectionManager, name);
    }

    /**
     * Returns distributed queue instance by name.
     *
     * @param name of the distributed queue
     * @return distributed queue
     */
    public <V> RQueue<V> getQueue(String name) {
        return new RedissonQueue<V>(connectionManager, name);
    }

    /**
     * Returns distributed deque instance by name.
     *
     * @param name of the distributed queue
     * @return distributed queue
     */
    public <V> RDeque<V> getDeque(String name) {
        return new RedissonDeque<V>(connectionManager, name);
    }

    /**
     * Returns distributed "atomic long" instance by name.
     *
     * @param name of the distributed "atomic long"
     * @return distributed "atomic long"
     */
    public RAtomicLong getAtomicLong(String name) {
        return new RedissonAtomicLong(connectionManager, name);
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

