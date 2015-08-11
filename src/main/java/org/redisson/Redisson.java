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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.ClusterConnectionManager;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveConnectionManager;
import org.redisson.connection.SentinelConnectionManager;
import org.redisson.connection.SingleConnectionManager;
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

/**
 * Main infrastructure class allows to get access
 * to all Redisson objects on top of Redis server.
 *
 * @author Nikita Koksharov
 *
 */
public class Redisson implements RedissonClient {

    private final CommandExecutor commandExecutor;
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
        commandExecutor = new CommandExecutorService(connectionManager);
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
        return new RedissonBucket<V>(commandExecutor, name);
    }

    /**
     * Returns a list of object holder by a key pattern
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     */
    @Override
    public <V> List<RBucket<V>> getBuckets(String pattern) {
        Future<Collection<String>> r = commandExecutor.readAllAsync(RedisCommands.KEYS, pattern);
        Collection<String> keys = commandExecutor.get(r);
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
        return new RedissonHyperLogLog<V>(commandExecutor, name);
    }

    /**
     * Returns distributed list instance by name.
     *
     * @param name of the distributed list
     * @return distributed list
     */
    @Override
    public <V> RList<V> getList(String name) {
        return new RedissonList<V>(commandExecutor, name);
    }

    /**
     * Returns distributed map instance by name.
     *
     * @param name of the distributed map
     * @return distributed map
     */
    @Override
    public <K, V> RMap<K, V> getMap(String name) {
        return new RedissonMap<K, V>(commandExecutor, name);
    }

    /**
     * Returns distributed lock instance by name.
     *
     * @param name of the distributed lock
     * @return distributed lock
     */
    @Override
    public RLock getLock(String name) {
        return new RedissonLock(commandExecutor, name, id);
    }

    /**
     * Returns distributed set instance by name.
     *
     * @param name of the distributed set
     * @return distributed set
     */
    @Override
    public <V> RSet<V> getSet(String name) {
        return new RedissonSet<V>(commandExecutor, name);
    }

    /**
     * Returns script with eval-operations support
     *
     * @return
     */
    public RScript getScript() {
        return new RedissonScript(commandExecutor);
    }

    /**
     * Returns distributed sorted set instance by name.
     *
     * @param name of the distributed set
     * @return distributed set
     */
    @Override
    public <V> RSortedSet<V> getSortedSet(String name) {
        return new RedissonSortedSet<V>(commandExecutor, name);
    }

    /**
     * Returns topic instance by name.
     *
     * @param name of the distributed topic
     * @return distributed topic
     */
    @Override
    public <M> RTopic<M> getTopic(String name) {
        return new RedissonTopic<M>(commandExecutor, name);
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
    public <M> RPatternTopic<M> getPatternTopic(String pattern) {
        return new RedissonPatternTopic<M>(commandExecutor, pattern);
    }

    /**
     * Returns distributed queue instance by name.
     *
     * @param name of the distributed queue
     * @return distributed queue
     */
    @Override
    public <V> RQueue<V> getQueue(String name) {
        return new RedissonQueue<V>(commandExecutor, name);
    }

    /**
     * Returns distributed blocking queue instance by name.
     *
     * @param name of the distributed blocking queue
     * @return distributed queue
     */
    @Override
    public <V> RBlockingQueue<V> getBlockingQueue(String name) {
        return new RedissonBlockingQueue<V>(commandExecutor, name);
    }

    /**
     * Returns distributed deque instance by name.
     *
     * @param name of the distributed queue
     * @return distributed queue
     */
    @Override
    public <V> RDeque<V> getDeque(String name) {
        return new RedissonDeque<V>(commandExecutor, name);
    }

    /**
     * Returns distributed "atomic long" instance by name.
     *
     * @param name of the distributed "atomic long"
     * @return distributed "atomic long"
     */
    @Override
    public RAtomicLong getAtomicLong(String name) {
        return new RedissonAtomicLong(commandExecutor, name);
    }

    /**
     * Returns distributed "count down latch" instance by name.
     *
     * @param name of the distributed "count down latch"
     * @return distributed "count down latch"
     */
    @Override
    public RCountDownLatch getCountDownLatch(String name) {
        return new RedissonCountDownLatch(commandExecutor, name, id);
    }

    /**
     * Returns keys operations.
     * Each of Redis/Redisson object associated with own key
     *
     * @return
     */
    @Override
    public RKeys getKeys() {
        return new RedissonKeys(commandExecutor);
    }

    /**
     * Shuts down Redisson instance <b>NOT</b> Redis server
     */
    @Override
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
    @Override
    public Config getConfig() {
        return config;
    }

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
    @Override
    public Collection<String> findKeysByPattern(String pattern) {
        return commandExecutor.get(findKeysByPatternAsync(pattern));
    }

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
    @Override
    public Future<Collection<String>> findKeysByPatternAsync(String pattern) {
        return commandExecutor.readAllAsync(RedisCommands.KEYS, pattern);
    }

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
    @Override
    public long deleteByPattern(String pattern) {
        return commandExecutor.get(deleteByPatternAsync(pattern));
    }

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
    @Override
    public Future<Long> deleteByPatternAsync(String pattern) {
        return commandExecutor.evalWriteAllAsync(RedisCommands.EVAL_INTEGER, new SlotCallback<Long, Long>() {
            AtomicLong results = new AtomicLong();
            @Override
            public void onSlotResult(Long result) {
                results.addAndGet(result);
            }

            @Override
            public Long onFinish() {
                return results.get();
            }
        }, "local keys = redis.call('keys', ARGV[1]) "
                + "local n = 0 "
                + "for i=1, table.getn(keys),5000 do "
                    + "n = n + redis.call('del', unpack(keys, i, math.min(i+4999, table.getn(keys)))) "
                + "end "
            + "return n;",Collections.emptyList(), pattern);
    }

    /**
     * Delete multiple objects by name
     *
     * @param keys - object names
     * @return
     */
    // use RKeys.delete
    @Deprecated
    @Override
    public long delete(String ... keys) {
        return commandExecutor.get(deleteAsync(keys));
    }

    /**
     * Delete multiple objects by name in async mode
     *
     * @param keys - object names
     * @return
     */
    // use RKeys.deleteAsync
    @Deprecated
    @Override
    public Future<Long> deleteAsync(String ... keys) {
        return commandExecutor.writeAllAsync(RedisCommands.DEL, new SlotCallback<Long, Long>() {
            AtomicLong results = new AtomicLong();
            @Override
            public void onSlotResult(Long result) {
                results.addAndGet(result);
            }

            @Override
            public Long onFinish() {
                return results.get();
            }
        }, (Object[])keys);
    }

    /**
     * Get Redis nodes group for server operations
     *
     * @return
     */
    public NodesGroup<Node> getNodesGroup() {
        return new RedisNodes<Node>(connectionManager);
    }

    /**
     * Get Redis cluster nodes group for server operations
     *
     * @return
     */
    public NodesGroup<ClusterNode> getClusterNodesGroup() {
        if (!config.isClusterConfig()) {
            throw new IllegalStateException("Redisson not in cluster mode!");
        }
        return new RedisNodes<ClusterNode>(connectionManager);
    }

    /**
     * Delete all the keys of the currently selected database
     */
    @Override
    public void flushdb() {
        commandExecutor.get(commandExecutor.writeAllAsync(RedisCommands.FLUSHDB));
    }

    /**
     * Delete all the keys of all the existing databases
     */
    @Override
    public void flushall() {
        commandExecutor.get(commandExecutor.writeAllAsync(RedisCommands.FLUSHALL));
    }

    /**
     * Return batch object which executes group of
     * command in pipeline.
     *
     * See <a href="http://redis.io/topics/pipelining">http://redis.io/topics/pipelining</a>
     *
     * @return
     */
    @Override
    public RBatch createBatch() {
        return new RedissonBatch(connectionManager);
    }

}

