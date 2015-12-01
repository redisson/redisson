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
import java.util.List;
import java.util.UUID;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.cluster.ClusterConnectionManager;
import org.redisson.command.CommandReactiveService;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.ElasticacheConnectionManager;
import org.redisson.connection.MasterSlaveConnectionManager;
import org.redisson.connection.SentinelConnectionManager;
import org.redisson.connection.SingleConnectionManager;
import org.redisson.core.RAtomicLongReactive;
import org.redisson.core.RBitSetReactive;
import org.redisson.core.RBlockingQueueReactive;
import org.redisson.core.RBucketReactive;
import org.redisson.core.RDequeReactive;
import org.redisson.core.RHyperLogLogReactive;
import org.redisson.core.RLexSortedSetReactive;
import org.redisson.core.RListReactive;
import org.redisson.core.RMapReactive;
import org.redisson.core.RPatternTopicReactive;
import org.redisson.core.RQueueReactive;
import org.redisson.core.RScoredSortedSetReactive;
import org.redisson.core.RScriptReactive;
import org.redisson.core.RSetReactive;
import org.redisson.core.RTopicReactive;

import io.netty.util.concurrent.Future;

/**
 * Main infrastructure class allows to get access
 * to all Redisson objects on top of Redis server.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonReactive implements RedissonReactiveClient {

    private final CommandReactiveService commandExecutor;
    private final ConnectionManager connectionManager;
    private final Config config;

    private final UUID id = UUID.randomUUID();

    RedissonReactive(Config config) {
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
        } else if (configCopy.getElasticacheServersConfig() != null) {
            connectionManager = new ElasticacheConnectionManager(configCopy.getElasticacheServersConfig(), configCopy);
        } else {
            throw new IllegalArgumentException("server(s) address(es) not defined!");
        }
        commandExecutor = new CommandReactiveService(connectionManager);
    }


    /**
     * Returns object holder by name
     *
     * @param name of object
     * @return
     */
    @Override
    public <V> RBucketReactive<V> getBucket(String name) {
        return new RedissonBucketReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RBucketReactive<V> getBucket(String name, Codec codec) {
        return new RedissonBucketReactive<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> List<RBucketReactive<V>> findBuckets(String pattern) {
        Future<Collection<String>> r = commandExecutor.readAllAsync(RedisCommands.KEYS, pattern);
        Collection<String> keys = commandExecutor.get(r);

        List<RBucketReactive<V>> buckets = new ArrayList<RBucketReactive<V>>(keys.size());
        for (Object key : keys) {
            if(key != null) {
                buckets.add(this.<V>getBucket(key.toString()));
            }
        }
        return buckets;
    }

    @Override
    public <V> RHyperLogLogReactive<V> getHyperLogLog(String name) {
        return new RedissonHyperLogLogReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RHyperLogLogReactive<V> getHyperLogLog(String name, Codec codec) {
        return new RedissonHyperLogLogReactive<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RListReactive<V> getList(String name) {
        return new RedissonListReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RListReactive<V> getList(String name, Codec codec) {
        return new RedissonListReactive<V>(codec, commandExecutor, name);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name) {
        return new RedissonMapReactive<K, V>(commandExecutor, name);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, Codec codec) {
        return new RedissonMapReactive<K, V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name) {
        return new RedissonSetReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name, Codec codec) {
        return new RedissonSetReactive<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name) {
        return new RedissonScoredSortedSetReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name, Codec codec) {
        return new RedissonScoredSortedSetReactive<V>(codec, commandExecutor, name);
    }

    @Override
    public RLexSortedSetReactive getLexSortedSet(String name) {
        return new RedissonLexSortedSetReactive(commandExecutor, name);
    }

    @Override
    public <M> RTopicReactive<M> getTopic(String name) {
        return new RedissonTopicReactive<M>(commandExecutor, name);
    }

    @Override
    public <M> RTopicReactive<M> getTopic(String name, Codec codec) {
        return new RedissonTopicReactive<M>(codec, commandExecutor, name);
    }

    @Override
    public <M> RPatternTopicReactive<M> getPatternTopic(String pattern) {
        return new RedissonPatternTopicReactive<M>(commandExecutor, pattern);
    }

    @Override
    public <M> RPatternTopicReactive<M> getPatternTopic(String pattern, Codec codec) {
        return new RedissonPatternTopicReactive<M>(codec, commandExecutor, pattern);
    }

    @Override
    public <V> RQueueReactive<V> getQueue(String name) {
        return new RedissonQueueReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RQueueReactive<V> getQueue(String name, Codec codec) {
        return new RedissonQueueReactive<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name) {
        return new RedissonBlockingQueueReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name, Codec codec) {
        return new RedissonBlockingQueueReactive<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RDequeReactive<V> getDeque(String name) {
        return new RedissonDequeReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RDequeReactive<V> getDeque(String name, Codec codec) {
        return new RedissonDequeReactive<V>(codec, commandExecutor, name);
    }

    @Override
    public RAtomicLongReactive getAtomicLong(String name) {
        return new RedissonAtomicLongReactive(commandExecutor, name);
    }

    @Override
    public RBitSetReactive getBitSet(String name) {
        return new RedissonBitSetReactive(commandExecutor, name);
    }

    @Override
    public RScriptReactive getScript() {
        return new RedissonScriptReactive(commandExecutor);
    }

    public Config getConfig() {
        return config;
    }

    @Override
    public void shutdown() {
        connectionManager.shutdown();
    }

    @Override
    public void flushdb() {
        commandExecutor.get(commandExecutor.writeAllAsync(RedisCommands.FLUSHDB));
    }

    @Override
    public void flushall() {
        commandExecutor.get(commandExecutor.writeAllAsync(RedisCommands.FLUSHALL));
    }
}

