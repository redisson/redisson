/**
 * Copyright 2016 Nikita Koksharov
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RedissonReactiveClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.cluster.ClusterConnectionManager;
import org.redisson.command.CommandExecutor;
import org.redisson.command.CommandSyncService;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.ElasticacheConnectionManager;
import org.redisson.connection.MasterSlaveConnectionManager;
import org.redisson.connection.SentinelConnectionManager;
import org.redisson.connection.SingleConnectionManager;
import org.redisson.core.ClusterNodesGroup;
import org.redisson.core.Node;
import org.redisson.core.NodesGroup;
import org.redisson.core.RAtomicDouble;
import org.redisson.core.RAtomicLong;
import org.redisson.core.RBatch;
import org.redisson.core.RBitSet;
import org.redisson.core.RBlockingDeque;
import org.redisson.core.RBlockingQueue;
import org.redisson.core.RBloomFilter;
import org.redisson.core.RBucket;
import org.redisson.core.RBuckets;
import org.redisson.core.RCountDownLatch;
import org.redisson.core.RDeque;
import org.redisson.core.RGeo;
import org.redisson.core.RHyperLogLog;
import org.redisson.core.RKeys;
import org.redisson.core.RLexSortedSet;
import org.redisson.core.RList;
import org.redisson.core.RListMultimap;
import org.redisson.core.RListMultimapCache;
import org.redisson.core.RLock;
import org.redisson.core.RMap;
import org.redisson.core.RMapCache;
import org.redisson.core.RPatternTopic;
import org.redisson.core.RQueue;
import org.redisson.core.RReadWriteLock;
import org.redisson.core.RRemoteService;
import org.redisson.core.RScoredSortedSet;
import org.redisson.core.RScript;
import org.redisson.core.RSemaphore;
import org.redisson.core.RSet;
import org.redisson.core.RSetCache;
import org.redisson.core.RSetMultimap;
import org.redisson.core.RSetMultimapCache;
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

    protected final EvictionScheduler evictionScheduler;
    protected final CommandExecutor commandExecutor;
    protected final ConnectionManager connectionManager;
    protected final Config config;

    protected final UUID id = UUID.randomUUID();

    protected Redisson(Config config) {
        this.config = config;
        Config configCopy = new Config(config);
        
        if (configCopy.getMasterSlaveServersConfig() != null) {
            validate(configCopy.getMasterSlaveServersConfig());
            connectionManager = new MasterSlaveConnectionManager(configCopy.getMasterSlaveServersConfig(), configCopy);
        } else if (configCopy.getSingleServerConfig() != null) {
            validate(configCopy.getSingleServerConfig());
            connectionManager = new SingleConnectionManager(configCopy.getSingleServerConfig(), configCopy);
        } else if (configCopy.getSentinelServersConfig() != null) {
            validate(configCopy.getSentinelServersConfig());
            connectionManager = new SentinelConnectionManager(configCopy.getSentinelServersConfig(), configCopy);
        } else if (configCopy.getClusterServersConfig() != null) {
            validate(configCopy.getClusterServersConfig());
            connectionManager = new ClusterConnectionManager(configCopy.getClusterServersConfig(), configCopy);
        } else if (configCopy.getElasticacheServersConfig() != null) {
            validate(configCopy.getElasticacheServersConfig());
            connectionManager = new ElasticacheConnectionManager(configCopy.getElasticacheServersConfig(), configCopy);
        } else {
            throw new IllegalArgumentException("server(s) address(es) not defined!");
        }
        commandExecutor = new CommandSyncService(connectionManager);
        evictionScheduler = new EvictionScheduler(commandExecutor);
    }

    private void validate(SingleServerConfig config) {
        if (config.getConnectionPoolSize() < config.getConnectionMinimumIdleSize()) {
            throw new IllegalArgumentException("connectionPoolSize can't be lower than connectionMinimumIdleSize");
        }
    }

    private void validate(BaseMasterSlaveServersConfig<?> config) {
        if (config.getSlaveConnectionPoolSize() < config.getSlaveConnectionMinimumIdleSize()) {
            throw new IllegalArgumentException("slaveConnectionPoolSize can't be lower than slaveConnectionMinimumIdleSize");
        }
        if (config.getMasterConnectionPoolSize() < config.getMasterConnectionMinimumIdleSize()) {
            throw new IllegalArgumentException("masterConnectionPoolSize can't be lower than masterConnectionMinimumIdleSize");
        }
        if (config.getSlaveSubscriptionConnectionPoolSize() < config.getSlaveSubscriptionConnectionMinimumIdleSize()) {
            throw new IllegalArgumentException("slaveSubscriptionConnectionMinimumIdleSize can't be lower than slaveSubscriptionConnectionPoolSize");
        }
    }

    /**
     * Create sync/async Redisson instance with default config
     *
     * @return Redisson instance
     */
    public static RedissonClient create() {
        Config config = new Config();
        config.useSingleServer().setAddress("127.0.0.1:6379");
//        config.useMasterSlaveConnection().setMasterAddress("127.0.0.1:6379").addSlaveAddress("127.0.0.1:6389").addSlaveAddress("127.0.0.1:6399");
//        config.useSentinelConnection().setMasterName("mymaster").addSentinelAddress("127.0.0.1:26389", "127.0.0.1:26379");
//        config.useClusterServers().addNodeAddress("127.0.0.1:7000");
        return create(config);
    }

    /**
     * Create sync/async Redisson instance with provided config
     *
     * @param config
     * @return Redisson instance
     */
    public static RedissonClient create(Config config) {
        return new Redisson(config);
    }

    /**
     * Create reactive Redisson instance with default config
     *
     * @return Redisson instance
     */
    public static RedissonReactiveClient createReactive() {
        Config config = new Config();
        config.useSingleServer().setAddress("127.0.0.1:6379");
//        config.useMasterSlaveConnection().setMasterAddress("127.0.0.1:6379").addSlaveAddress("127.0.0.1:6389").addSlaveAddress("127.0.0.1:6399");
//        config.useSentinelConnection().setMasterName("mymaster").addSentinelAddress("127.0.0.1:26389", "127.0.0.1:26379");
//        config.useClusterServers().addNodeAddress("127.0.0.1:7000");
        return createReactive(config);
    }

    /**
     * Create reactive Redisson instance with provided config
     *
     * @return Redisson instance
     */
    public static RedissonReactiveClient createReactive(Config config) {
        return new RedissonReactive(config);
    }
    
    @Override
    public <V> RGeo<V> getGeo(String name) {
        return new RedissonGeo<V>(commandExecutor, name);
    }
    
    @Override
    public <V> RGeo<V> getGeo(String name, Codec codec) {
        return new RedissonGeo<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RBucket<V> getBucket(String name) {
        return new RedissonBucket<V>(commandExecutor, name);
    }

    @Override
    public <V> RBucket<V> getBucket(String name, Codec codec) {
        return new RedissonBucket<V>(codec, commandExecutor, name);
    }

    @Override
    public RBuckets getBuckets() {
        return new RedissonBuckets(this, commandExecutor);
    }
    
    @Override
    public RBuckets getBuckets(Codec codec) {
        return new RedissonBuckets(this, codec, commandExecutor);
    }
    
    @Override
    public <V> List<RBucket<V>> findBuckets(String pattern) {
        Collection<String> keys = commandExecutor.get(commandExecutor.<List<String>, String>readAllAsync(RedisCommands.KEYS, pattern));
        List<RBucket<V>> buckets = new ArrayList<RBucket<V>>(keys.size());
        for (String key : keys) {
            if(key == null) {
                continue;
            }
            buckets.add(this.<V>getBucket(key));
        }
        return buckets;
    }

    @Override
    public <V> Map<String, V> loadBucketValues(Collection<String> keys) {
        return loadBucketValues(keys.toArray(new String[keys.size()]));
    }

    @Override
    public <V> Map<String, V> loadBucketValues(String ... keys) {
        if (keys.length == 0) {
            return Collections.emptyMap();
        }

        Future<List<Object>> future = commandExecutor.readAsync(keys[0], RedisCommands.MGET, keys);
        List<Object> values = commandExecutor.get(future);
        Map<String, V> result = new HashMap<String, V>(values.size());
        int index = 0;
        for (Object value : values) {
            if(value == null) {
                index++;
                continue;
            }
            result.put(keys[index], (V)value);
            index++;
        }
        return result;
    }

    @Override
    public void saveBuckets(Map<String, ?> buckets) {
        if (buckets.isEmpty()) {
            return;
        }

        List<Object> params = new ArrayList<Object>(buckets.size());
        for (Entry<String, ?> entry : buckets.entrySet()) {
            params.add(entry.getKey());
            try {
                params.add(config.getCodec().getValueEncoder().encode(entry.getValue()));
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }

        commandExecutor.write(params.get(0).toString(), RedisCommands.MSET, params.toArray());
    }

    @Override
    public <V> RHyperLogLog<V> getHyperLogLog(String name) {
        return new RedissonHyperLogLog<V>(commandExecutor, name);
    }

    @Override
    public <V> RHyperLogLog<V> getHyperLogLog(String name, Codec codec) {
        return new RedissonHyperLogLog<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RList<V> getList(String name) {
        return new RedissonList<V>(commandExecutor, name);
    }

    @Override
    public <V> RList<V> getList(String name, Codec codec) {
        return new RedissonList<V>(codec, commandExecutor, name);
    }

    @Override
    public <K, V> RListMultimap<K, V> getListMultimap(String name) {
        return new RedissonListMultimap<K, V>(commandExecutor, name);
    }

    @Override
    public <K, V> RListMultimap<K, V> getListMultimap(String name, Codec codec) {
        return new RedissonListMultimap<K, V>(codec, commandExecutor, name);
    }

    @Override
    public <K, V> RMap<K, V> getMap(String name) {
        return new RedissonMap<K, V>(commandExecutor, name);
    }

    @Override
    public <K, V> RSetMultimap<K, V> getSetMultimap(String name) {
        return new RedissonSetMultimap<K, V>(commandExecutor, name);
    }
    
    @Override
    public <K, V> RSetMultimapCache<K, V> getSetMultimapCache(String name) {
        return new RedissonSetMultimapCache<K, V>(evictionScheduler, commandExecutor, name);
    }
    
    @Override
    public <K, V> RSetMultimapCache<K, V> getSetMultimapCache(String name, Codec codec) {
        return new RedissonSetMultimapCache<K, V>(evictionScheduler, codec, commandExecutor, name);
    }

    @Override
    public <K, V> RListMultimapCache<K, V> getListMultimapCache(String name) {
        return new RedissonListMultimapCache<K, V>(evictionScheduler, commandExecutor, name);
    }
    
    @Override
    public <K, V> RListMultimapCache<K, V> getListMultimapCache(String name, Codec codec) {
        return new RedissonListMultimapCache<K, V>(evictionScheduler, codec, commandExecutor, name);
    }

    @Override
    public <K, V> RSetMultimap<K, V> getSetMultimap(String name, Codec codec) {
        return new RedissonSetMultimap<K, V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RSetCache<V> getSetCache(String name) {
        return new RedissonSetCache<V>(evictionScheduler, commandExecutor, name);
    }

    @Override
    public <V> RSetCache<V> getSetCache(String name, Codec codec) {
        return new RedissonSetCache<V>(codec, evictionScheduler, commandExecutor, name);
    }

    @Override
    public <K, V> RMapCache<K, V> getMapCache(String name) {
        return new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name);
    }

    @Override
    public <K, V> RMapCache<K, V> getMapCache(String name, Codec codec) {
        return new RedissonMapCache<K, V>(codec, evictionScheduler, commandExecutor, name);
    }

    @Override
    public <K, V> RMap<K, V> getMap(String name, Codec codec) {
        return new RedissonMap<K, V>(codec, commandExecutor, name);
    }

    @Override
    public RLock getLock(String name) {
        return new RedissonLock(commandExecutor, name, id);
    }

    @Override
    public RLock getFairLock(String name) {
        return new RedissonFairLock(commandExecutor, name, id);
    }
    
    @Override
    public RReadWriteLock getReadWriteLock(String name) {
        return new RedissonReadWriteLock(commandExecutor, name, id);
    }

    @Override
    public <V> RSet<V> getSet(String name) {
        return new RedissonSet<V>(commandExecutor, name);
    }

    @Override
    public <V> RSet<V> getSet(String name, Codec codec) {
        return new RedissonSet<V>(codec, commandExecutor, name);
    }

    @Override
    public RScript getScript() {
        return new RedissonScript(commandExecutor);
    }

    public RRemoteService getRemoteSerivce() {
        return new RedissonRemoteService(this, commandExecutor);
    }

    @Override
    public RRemoteService getRemoteSerivce(String name) {
        return new RedissonRemoteService(this, name, commandExecutor);
    }
    
    @Override
    public RRemoteService getRemoteSerivce(Codec codec) {
        return new RedissonRemoteService(codec, this, commandExecutor);
    }
    
    @Override
    public RRemoteService getRemoteSerivce(String name, Codec codec) {
        return new RedissonRemoteService(codec, this, name, commandExecutor);
    }

    @Override
    public <V> RSortedSet<V> getSortedSet(String name) {
        return new RedissonSortedSet<V>(commandExecutor, name, this);
    }

    @Override
    public <V> RSortedSet<V> getSortedSet(String name, Codec codec) {
        return new RedissonSortedSet<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RScoredSortedSet<V> getScoredSortedSet(String name) {
        return new RedissonScoredSortedSet<V>(commandExecutor, name);
    }

    @Override
    public <V> RScoredSortedSet<V> getScoredSortedSet(String name, Codec codec) {
        return new RedissonScoredSortedSet<V>(codec, commandExecutor, name);
    }

    @Override
    public RLexSortedSet getLexSortedSet(String name) {
        return new RedissonLexSortedSet(commandExecutor, name);
    }

    @Override
    public <M> RTopic<M> getTopic(String name) {
        return new RedissonTopic<M>(commandExecutor, name);
    }

    @Override
    public <M> RTopic<M> getTopic(String name, Codec codec) {
        return new RedissonTopic<M>(codec, commandExecutor, name);
    }

    @Override
    public <M> RPatternTopic<M> getPatternTopic(String pattern) {
        return new RedissonPatternTopic<M>(commandExecutor, pattern);
    }

    @Override
    public <M> RPatternTopic<M> getPatternTopic(String pattern, Codec codec) {
        return new RedissonPatternTopic<M>(codec, commandExecutor, pattern);
    }

    @Override
    public <V> RQueue<V> getQueue(String name) {
        return new RedissonQueue<V>(commandExecutor, name);
    }

    @Override
    public <V> RQueue<V> getQueue(String name, Codec codec) {
        return new RedissonQueue<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RBlockingQueue<V> getBlockingQueue(String name) {
        return new RedissonBlockingQueue<V>(commandExecutor, name);
    }

    @Override
    public <V> RBlockingQueue<V> getBlockingQueue(String name, Codec codec) {
        return new RedissonBlockingQueue<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RDeque<V> getDeque(String name) {
        return new RedissonDeque<V>(commandExecutor, name);
    }

    @Override
    public <V> RDeque<V> getDeque(String name, Codec codec) {
        return new RedissonDeque<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RBlockingDeque<V> getBlockingDeque(String name) {
        return new RedissonBlockingDeque<V>(commandExecutor, name);
    }

    @Override
    public <V> RBlockingDeque<V> getBlockingDeque(String name, Codec codec) {
        return new RedissonBlockingDeque<V>(codec, commandExecutor, name);
    };

    @Override
    public RAtomicLong getAtomicLong(String name) {
        return new RedissonAtomicLong(commandExecutor, name);
    }

    @Override
    public RAtomicDouble getAtomicDouble(String name) {
        return new RedissonAtomicDouble(commandExecutor, name);
    }

    @Override
    public RCountDownLatch getCountDownLatch(String name) {
        return new RedissonCountDownLatch(commandExecutor, name, id);
    }

    @Override
    public RBitSet getBitSet(String name) {
        return new RedissonBitSet(commandExecutor, name);
    }

    @Override
    public RSemaphore getSemaphore(String name) {
        return new RedissonSemaphore(commandExecutor, name, id);
    }

    @Override
    public <V> RBloomFilter<V> getBloomFilter(String name) {
        return new RedissonBloomFilter<V>(commandExecutor, name);
    }

    @Override
    public <V> RBloomFilter<V> getBloomFilter(String name, Codec codec) {
        return new RedissonBloomFilter<V>(codec, commandExecutor, name);
    }

    @Override
    public RKeys getKeys() {
        return new RedissonKeys(commandExecutor);
    }

    @Override
    public RBatch createBatch() {
        return new RedissonBatch(evictionScheduler, connectionManager);
    }

    @Override
    public void shutdown() {
        connectionManager.shutdown();
    }
    
    
    @Override
    public void shutdown(long quietPeriod, long timeout, TimeUnit unit) {
        connectionManager.shutdown(quietPeriod, timeout, unit);
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public NodesGroup<Node> getNodesGroup() {
        return new RedisNodes<Node>(connectionManager);
    }

    @Override
    public ClusterNodesGroup getClusterNodesGroup() {
        if (!connectionManager.isClusterMode()) {
            throw new IllegalStateException("Redisson is not in cluster mode!");
        }
        return new RedisClusterNodes(connectionManager);
    }

    @Override
    public boolean isShutdown() {
        return connectionManager.isShutdown();
    }

    @Override
    public boolean isShuttingDown() {
        return connectionManager.isShuttingDown();
    }

}

