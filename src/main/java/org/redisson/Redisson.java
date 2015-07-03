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

import com.lambdaworks.redis.RedisAsyncConnection;
import io.netty.util.concurrent.Future;
import org.redisson.async.ResultOperation;
import org.redisson.connection.*;
import org.redisson.core.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Main infrastructure class allows to get access
 * to all Redisson objects on top of Redis server.
 *
 * @author Nikita Koksharov
 *
 */
public class Redisson implements RedissonClient {

    private final ConnectionManager connectionManager;
    private final Config config;

    private final UUID id = UUID.randomUUID();

    Redisson(Config config) {
        this.config = config;
        Config configCopy = new Config(config);
        if (configCopy.getMasterSlaveServersConfig() != null) {
            connectionManager = new MasterSlaveConnectionManager(configCopy.getMasterSlaveServersConfig(), configCopy);
        } else if (configCopy.getSingleServerConfig() != null) {
            connectionManager = new SingleConnectionManager(configCopy.getSingleServerConfig(), configCopy);
        } else if (configCopy.getSentinelServersConfig() != null) {
            connectionManager = new SentinelConnectionManager(configCopy.getSentinelServersConfig(), configCopy);
        } else if (configCopy.getClusterServersConfig() != null) {
            connectionManager = new ClusterConnectionManager(configCopy.getClusterServersConfig(), configCopy);
        } else {
            throw new IllegalArgumentException("server(s) address(es) not defined!");
        }
    }

    /**
     * Creates an Redisson instance
     *
     * @return Redisson instance
     */
    public static Redisson create() {
        Config config = new Config();
        config.useSingleServer().setAddress("127.0.0.1:6379");
//        config.useMasterSlaveConnection().setMasterAddress("127.0.0.1:6379").addSlaveAddress("127.0.0.1:6389").addSlaveAddress("127.0.0.1:6399");
//        config.useSentinelConnection().setMasterName("mymaster").addSentinelAddress("127.0.0.1:26389", "127.0.0.1:26379");
//        config.useClusterServers().addNodeAddress("127.0.0.1:7000");
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
     * Returns object holder by name
     *
     * @param name of object
     * @return
     */
    @Override
    public <V> RBucket<V> getBucket(String name) {
        return new RedissonBucket<V>(connectionManager, name);
    }

    /**
     * Returns a list of object holder by a key pattern
     */
    @Override
    public <V> List<RBucket<V>> getBuckets(final String pattern) {
        List<Object> keys = connectionManager.get(connectionManager.readAsync(new ResultOperation<List<Object>, V>() {
            @Override
            public Future<List<Object>> execute(RedisAsyncConnection<Object, V> async) {
                return async.keys(pattern);
            }
        }));
        if (keys == null) {
            return Collections.emptyList();
        }
        List<RBucket<V>> buckets = new ArrayList<RBucket<V>>(keys.size());
        for (Object key : keys) {
            if(key != null) {
                buckets.add(this.<V>getBucket(key.toString()));
            }
        }
        return buckets;
    }


    /**
     * Returns HyperLogLog object
     *
     * @param name of object
     * @return
     */
    @Override
    public <V> RHyperLogLog<V> getHyperLogLog(String name) {
        return new RedissonHyperLogLog<V>(connectionManager, name);
    }

    /**
     * Returns distributed list instance by name.
     *
     * @param name of the distributed list
     * @return distributed list
     */
    @Override
    public <V> RList<V> getList(String name) {
        return new RedissonList<V>(connectionManager, name);
    }

    /**
     * Returns distributed map instance by name.
     *
     * @param name of the distributed map
     * @return distributed map
     */
    @Override
    public <K, V> RMap<K, V> getMap(String name) {
        return new RedissonMap<K, V>(connectionManager, name);
    }

    /**
     * Returns distributed lock instance by name.
     *
     * @param name of the distributed lock
     * @return distributed lock
     */
    @Override
    public RLock getLock(String name) {
        return new RedissonLock(connectionManager, name, id);
    }

    /**
     * Returns distributed set instance by name.
     *
     * @param name of the distributed set
     * @return distributed set
     */
    @Override
    public <V> RSet<V> getSet(String name) {
        return new RedissonSet<V>(connectionManager, name);
    }

    /**
     * Returns script with eval-operations support
     *
     * @return
     */
    public RScript getScript() {
        return new RedissonScript(connectionManager);
    }

    /**
     * Returns distributed sorted set instance by name.
     *
     * @param name of the distributed set
     * @return distributed set
     */
    @Override
    public <V> RSortedSet<V> getSortedSet(String name) {
        return new RedissonSortedSet<V>(connectionManager, name);
    }

    /**
     * Returns topic instance by name.
     *
     * @param name of the distributed topic
     * @return distributed topic
     */
    @Override
    public <M> RTopic<M> getTopic(String name) {
        return new RedissonTopic<M>(connectionManager, name);
    }

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
    @Override
    public <M> RTopic<M> getTopicPattern(String pattern) {
        return new RedissonTopicPattern<M>(connectionManager, pattern);
    }

    /**
     * Returns distributed queue instance by name.
     *
     * @param name of the distributed queue
     * @return distributed queue
     */
    @Override
    public <V> RQueue<V> getQueue(String name) {
        return new RedissonQueue<V>(connectionManager, name);
    }

    /**
     * Returns distributed blocking queue instance by name.
     *
     * @param name of the distributed blocking queue
     * @return distributed queue
     */
    @Override
    public <V> RBlockingQueue<V> getBlockingQueue(String name) {
        return new RedissonBlockingQueue(connectionManager, name);
    }

    /**
     * Returns distributed deque instance by name.
     *
     * @param name of the distributed queue
     * @return distributed queue
     */
    @Override
    public <V> RDeque<V> getDeque(String name) {
        return new RedissonDeque<V>(connectionManager, name);
    }

    /**
     * Returns distributed "atomic long" instance by name.
     *
     * @param name of the distributed "atomic long"
     * @return distributed "atomic long"
     */
    @Override
    public RAtomicLong getAtomicLong(String name) {
        return new RedissonAtomicLong(connectionManager, name);
    }

    /**
     * Returns distributed "count down latch" instance by name.
     *
     * @param name of the distributed "count down latch"
     * @return distributed "count down latch"
     */
    @Override
    public RCountDownLatch getCountDownLatch(String name) {
        return new RedissonCountDownLatch(connectionManager, name, id);
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
        connectionManager.writeAllAsync(new ResultOperation<String, Object>() {
            @Override
            protected Future<String> execute(RedisAsyncConnection<Object, Object> conn) {
                return conn.flushdb();
            }
        }).awaitUninterruptibly();
    }

}

