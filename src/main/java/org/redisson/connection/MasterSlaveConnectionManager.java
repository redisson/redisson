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
    protected RedisCodec codec;

    private EventLoopGroup group;

    private final List<ConnectionEntry> slaveConnections = new ArrayList<ConnectionEntry>();
    private final Queue<RedisConnection> masterConnections = new ConcurrentLinkedQueue<RedisConnection>();

    private final Queue<PubSubConnectionEntry> pubSubConnections = new ConcurrentLinkedQueue<PubSubConnectionEntry>();
    private final ConcurrentMap<String, PubSubConnectionEntry> name2PubSubConnection = new ConcurrentHashMap<String, PubSubConnectionEntry>();

    private LoadBalancer balancer;
    private final List<RedisClient> slaveClients = new ArrayList<RedisClient>();
    protected volatile RedisClient masterClient;

    private Semaphore masterConnectionsSemaphore;

    protected MasterSlaveConnectionConfig config;

    MasterSlaveConnectionManager() {
    }

    public MasterSlaveConnectionManager(MasterSlaveConnectionConfig cfg, Config config) {
        init(cfg, config);
    }

    void init(MasterSlaveConnectionConfig config, Config cfg) {
        this.group = new NioEventLoopGroup(cfg.getThreads());
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
        if (!slaveConnections.isEmpty()) {
            balancer = config.getLoadBalancer();
            balancer.init(slaveConnections, codec, config.getPassword());
        }

        masterConnectionsSemaphore = new Semaphore(this.config.getMasterConnectionPoolSize());
    }

    public void changeMaster(String host, int port) {
        // TODO async
        masterClient.shutdown();

        masterClient = new RedisClient(group, host, port);
        // TODO
        // 1. remove slave
        // 2. re-attach listeners
        // 3. remove dead slave
    }

    @Override
    public <T> FutureListener<T> createReleaseWriteListener(final RedisConnection conn) {
        return new FutureListener<T>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<T> future) throws Exception {
                releaseWrite(conn);
            }
        };
    }

    @Override
    public <T> FutureListener<T> createReleaseReadListener(final RedisConnection conn) {
        return new FutureListener<T>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<T> future) throws Exception {
                releaseRead(conn);
            }
        };
    }

    @Override
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

    @Override
    public <K, V> RedisConnection<K, V> connectionReadOp() {
        return balancer.nextConnection();
    }

    @Override
    public PubSubConnectionEntry getEntry(String channelName) {
        return name2PubSubConnection.get(channelName);
    }

    @Override
    public <K, V> PubSubConnectionEntry subscribe(String channelName) {
        // multiple channel names per PubSubConnections allowed
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

        RedisPubSubConnection<K, V> conn = nextPubSubConnection();

        PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
        entry.tryAcquire();
        PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
        if (oldEntry != null) {
            returnSubscribeConnection(entry);
            return oldEntry;
        }
        entry.subscribe(channelName);
        pubSubConnections.add(entry);
        return entry;
    }

    RedisPubSubConnection nextPubSubConnection() {
        return balancer.nextPubSubConnection();
    }

    @Override
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

        RedisPubSubConnection<K, V> conn = nextPubSubConnection();

        PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
        entry.tryAcquire();
        PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
        if (oldEntry != null) {
            returnSubscribeConnection(entry);
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

    @Override
    public void unsubscribe(PubSubConnectionEntry entry, String channelName) {
        if (entry.hasListeners(channelName)) {
            return;
        }
        name2PubSubConnection.remove(channelName);
        entry.unsubscribe(channelName);
        if (entry.tryClose()) {
            pubSubConnections.remove(entry);
            returnSubscribeConnection(entry);
        }
    }

    protected void returnSubscribeConnection(PubSubConnectionEntry entry) {
        balancer.returnSubscribeConnection(entry.getConnection());
    }

    public void releaseWrite(RedisConnection сonnection) {
        masterConnections.add(сonnection);
        releaseMasterConnection();
    }

    public void releaseRead(RedisConnection сonnection) {
        balancer.returnConnection(сonnection);
    }

    @Override
    public void shutdown() {
        masterClient.shutdown();
        for (RedisClient client : slaveClients) {
            client.shutdown();
        }
    }

    @Override
    public EventLoopGroup getGroup() {
        return group;
    }

}
