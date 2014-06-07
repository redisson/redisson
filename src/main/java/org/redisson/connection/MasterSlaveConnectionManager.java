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
package org.redisson.connection;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.FutureListener;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

import org.redisson.Config;
import org.redisson.MasterSlaveConnectionConfig;
import org.redisson.codec.RedisCodecWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

/**
 *
 * @author Nikita Koksharov
 *
 */
//TODO ping support
public class MasterSlaveConnectionManager implements ConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private RedisCodec codec;

    private final EventLoopGroup group = new NioEventLoopGroup();

    private final List<ConnectionEntry> slaveConnections = new ArrayList<ConnectionEntry>();
    private final Queue<RedisConnection> masterConnections = new ConcurrentLinkedQueue<RedisConnection>();

    private final Queue<PubSubConnectionEntry> pubSubConnections = new ConcurrentLinkedQueue<PubSubConnectionEntry>();
    private final ConcurrentMap<String, PubSubConnectionEntry> name2PubSubConnection = new ConcurrentHashMap<String, PubSubConnectionEntry>();

    private final List<RedisClient> slaveClients = new ArrayList<RedisClient>();
    private RedisClient masterClient;

    private Semaphore masterConnectionsSemaphore;

    private MasterSlaveConnectionConfig config;
    private LoadBalancer balancer;

    MasterSlaveConnectionManager() {
    }

    public MasterSlaveConnectionManager(MasterSlaveConnectionConfig cfg, Config config) {
        init(cfg, config);
    }

    void init(MasterSlaveConnectionConfig config, Config cfg) {
        this.config = config;
        for (URI address : this.config.getSlaveAddresses()) {
            RedisClient client = new RedisClient(group, address.getHost(), address.getPort());
            slaveClients.add(client);
            slaveConnections.add(new ConnectionEntry(client,
                                        this.config.getSlaveConnectionPoolSize(),
                                        this.config.getSlaveSubscriptionConnectionPoolSize()));
        }
        masterClient = new RedisClient(group, this.config.getMasterAddress().getHost(), this.config.getMasterAddress().getPort());

        codec = new RedisCodecWrapper(cfg.getCodec());
        balancer = config.getLoadBalancer();
        balancer.init(slaveConnections, codec, config.getPassword());

        masterConnectionsSemaphore = new Semaphore(this.config.getMasterConnectionPoolSize());
    }

    public <T> FutureListener<T> createReleaseWriteListener(final RedisConnection conn) {
        return new FutureListener<T>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<T> future) throws Exception {
                releaseWrite(conn);
            }
        };
    }

    public <T> FutureListener<T> createReleaseReadListener(final RedisConnection conn) {
        return new FutureListener<T>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<T> future) throws Exception {
                releaseRead(conn);
            }
        };
    }

    public <K, V> RedisConnection<K, V> connectionWriteOp() {
        acquireMasterConnection();

        RedisConnection<K, V> conn = masterConnections.poll();
        if (conn == null) {
            conn = masterClient.connect(codec);
            if (config.getPassword() != null) {
                conn.auth(config.getPassword());
            }
        }
        return conn;
    }

    public <K, V> RedisConnection<K, V> connectionReadOp() {
        return balancer.nextConnection();
    }

    public PubSubConnectionEntry getEntry(String channelName) {
        return name2PubSubConnection.get(channelName);
    }

    public <K, V> PubSubConnectionEntry subscribe(String channelName) {
        PubSubConnectionEntry сonnEntry = name2PubSubConnection.get(channelName);
        if (сonnEntry != null) {
            return сonnEntry;
        }

        for (PubSubConnectionEntry entry : pubSubConnections) {
            if (entry.tryAcquire()) {
                PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
                if (oldEntry != null) {
                    entry.release();
                    return oldEntry;
                }
                entry.subscribe(channelName);
                return entry;
            }
        }

        acquireMasterConnection();

        RedisPubSubConnection<K, V> conn = balancer.nextPubSubConnection();
        if (config.getPassword() != null) {
            conn.auth(config.getPassword());
        }

        PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
        entry.tryAcquire();
        PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
        if (oldEntry != null) {
            return oldEntry;
        }
        entry.subscribe(channelName);
        pubSubConnections.add(entry);
        return entry;
    }

    public <K, V> PubSubConnectionEntry subscribe(RedisPubSubAdapter<K, V> listener, String channelName) {
        PubSubConnectionEntry сonnEntry = name2PubSubConnection.get(channelName);
        if (сonnEntry != null) {
            return сonnEntry;
        }

        for (PubSubConnectionEntry entry : pubSubConnections) {
            if (entry.tryAcquire()) {
                PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
                if (oldEntry != null) {
                    entry.release();
                    return oldEntry;
                }
                entry.subscribe(listener, channelName);
                return entry;
            }
        }

        acquireMasterConnection();

        RedisPubSubConnection<K, V> conn = balancer.nextPubSubConnection();
        if (config.getPassword() != null) {
            conn.auth(config.getPassword());
        }

        PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
        entry.tryAcquire();
        PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
        if (oldEntry != null) {
            return oldEntry;
        }
        entry.subscribe(listener, channelName);
        pubSubConnections.add(entry);
        return entry;
    }

    void acquireMasterConnection() {
        if (!masterConnectionsSemaphore.tryAcquire()) {
            log.warn("Master connection pool gets exhausted! Trying to acquire connection ...");
            long time = System.currentTimeMillis();
            masterConnectionsSemaphore.acquireUninterruptibly();
            long endTime = System.currentTimeMillis() - time;
            log.warn("Master connection acquired, time spended: {} ms", endTime);
        }
    }

    void releaseMasterConnection() {
        masterConnectionsSemaphore.release();
    }

    public void unsubscribe(PubSubConnectionEntry entry, String channelName) {
        if (entry.hasListeners(channelName)) {
            return;
        }
        name2PubSubConnection.remove(channelName);
        entry.unsubscribe(channelName);
        log.debug("unsubscribed from '{}' channel", channelName);
        if (entry.tryClose()) {
            pubSubConnections.remove(entry);
            balancer.returnSubscribeConnection(entry.getConnection());
        }
    }

    public void releaseWrite(RedisConnection сonnection) {
        masterConnections.add(сonnection);
        releaseMasterConnection();
    }

    public void releaseRead(RedisConnection сonnection) {
        balancer.returnConnection(сonnection);
    }

    public void shutdown() {
        for (RedisClient client : slaveClients) {
            client.shutdown();
        }
    }

    public EventLoopGroup getGroup() {
        return group;
    }

}
