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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.redisson.Config;
import org.redisson.MasterSlaveServersConfig;
import org.redisson.codec.RedisCodecWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubListener;

/**
 *
 * @author Nikita Koksharov
 *
 */
//TODO ping support
public class MasterSlaveConnectionManager implements ConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());
    protected RedisCodec codec;

    protected EventLoopGroup group;

    protected LoadBalancer balancer;
    private final ConcurrentMap<String, PubSubConnectionEntry> name2PubSubConnection = new ConcurrentHashMap<String, PubSubConnectionEntry>();

    protected volatile ConnectionEntry masterEntry;

    protected MasterSlaveServersConfig config;

    MasterSlaveConnectionManager() {
    }

    public MasterSlaveConnectionManager(MasterSlaveServersConfig cfg, Config config) {
        init(cfg, config);
    }

    protected void init(MasterSlaveServersConfig config, Config cfg) {
        init(cfg);

        init(config);
    }

    protected void init(MasterSlaveServersConfig config) {
        this.config = config;
        balancer = config.getLoadBalancer();
        balancer.init(codec, config.getPassword());
        for (URI address : this.config.getSlaveAddresses()) {
            RedisClient client = new RedisClient(group, address.getHost(), address.getPort());
            SlaveConnectionEntry entry = new SlaveConnectionEntry(client,
                                        this.config.getSlaveConnectionPoolSize(),
                                        this.config.getSlaveSubscriptionConnectionPoolSize());
            balancer.add(entry);
        }
        if (this.config.getSlaveAddresses().size() > 1) {
            slaveDown(this.config.getMasterAddress().getHost(), this.config.getMasterAddress().getPort());
        }

        RedisClient masterClient = new RedisClient(group, this.config.getMasterAddress().getHost(), this.config.getMasterAddress().getPort());
        masterEntry = new ConnectionEntry(masterClient, this.config.getMasterConnectionPoolSize());
    }

    protected void init(Config cfg) {
        this.group = new NioEventLoopGroup(cfg.getThreads());
        this.codec = new RedisCodecWrapper(cfg.getCodec());
    }

    protected void slaveDown(String host, int port) {
        Collection<RedisPubSubConnection> allPubSubConnections = balancer.freeze(host, port);

        // reattach listeners to other channels
        for (Entry<String, PubSubConnectionEntry> mapEntry : name2PubSubConnection.entrySet()) {
            for (RedisPubSubConnection redisPubSubConnection : allPubSubConnections) {
                PubSubConnectionEntry entry = mapEntry.getValue();
                String channelName = mapEntry.getKey();

                if (!entry.getConnection().equals(redisPubSubConnection)) {
                    continue;
                }

                synchronized (entry) {
                    entry.close();
                    unsubscribe(channelName);

                    Collection<RedisPubSubListener> listeners = entry.getListeners(channelName);
                    if (!listeners.isEmpty()) {
                        PubSubConnectionEntry newEntry = subscribe(channelName);
                        for (RedisPubSubListener redisPubSubListener : listeners) {
                            newEntry.addListener(channelName, redisPubSubListener);
                        }
                    }
                }
            }
        }
    }

    protected void addSlave(String host, int port) {
        slaveDown(masterEntry.getClient().getAddr().getHostName(), masterEntry.getClient().getAddr().getPort());
        
        RedisClient client = new RedisClient(group, host, port);
        balancer.add(new SlaveConnectionEntry(client,
                this.config.getSlaveConnectionPoolSize(),
                this.config.getSlaveSubscriptionConnectionPoolSize()));
    }
    
    protected void slaveUp(String host, int port) {
        balancer.unfreeze(host, port);
    }

    /**
     * Freeze slave with <code>host:port</code> from slaves list.
     * Re-attach pub/sub listeners from it to other slave.
     * Shutdown old master client.
     * 
     */
    protected void changeMaster(String host, int port) {
        ConnectionEntry oldMaster = masterEntry;
        RedisClient client = new RedisClient(group, host, port);
        masterEntry = new ConnectionEntry(client, this.config.getMasterConnectionPoolSize());
        slaveDown(host, port);
        oldMaster.getClient().shutdown();
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

        RedisConnection<K, V> conn = masterEntry.getConnections().poll();
        if (conn != null) {
            return conn;
        }

        conn = masterEntry.getClient().connect(codec);
        if (config.getPassword() != null) {
            conn.auth(config.getPassword());
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

        Set<PubSubConnectionEntry> entries = new HashSet<PubSubConnectionEntry>(name2PubSubConnection.values());
        for (PubSubConnectionEntry entry : entries) {
            if (entry.tryAcquire()) {
                PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
                if (oldEntry != null) {
                    entry.release();
                    return oldEntry;
                }
                
                synchronized (entry) {
                    if (!entry.isActive()) {
                        entry.release();
                        return subscribe(channelName);
                    }
                    entry.subscribe(channelName);
                    return entry;
                }
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

        synchronized (entry) {
            if (!entry.isActive()) {
                entry.release();
                return subscribe(channelName);
            }
            entry.subscribe(channelName);
            return entry;
        }
    }

    RedisPubSubConnection nextPubSubConnection() {
        return balancer.nextPubSubConnection();
    }

    @Override
    public <K, V> PubSubConnectionEntry subscribe(RedisPubSubAdapter<V> listener, String channelName) {
        PubSubConnectionEntry сonnEntry = name2PubSubConnection.get(channelName);
        if (сonnEntry != null) {
            return сonnEntry;
        }

            Set<PubSubConnectionEntry> entries = new HashSet<PubSubConnectionEntry>(name2PubSubConnection.values());
            for (PubSubConnectionEntry entry : entries) {
                if (entry.tryAcquire()) {
                    PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
                    if (oldEntry != null) {
                        entry.release();
                        return oldEntry;
                    }
                    synchronized (entry) {
                        if (!entry.isActive()) {
                            entry.release();
                            return subscribe(listener, channelName);
                        }
                        entry.subscribe(listener, channelName);
                        return entry;
                    }
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
            synchronized (entry) {
                if (!entry.isActive()) {
                    entry.release();
                    return subscribe(listener, channelName);
                }
                entry.subscribe(listener, channelName);
                return entry;
            }
    }

    void acquireMasterConnection() {
        if (!masterEntry.getConnectionsSemaphore().tryAcquire()) {
            log.warn("Master connection pool gets exhausted! Trying to acquire connection ...");
            long time = System.currentTimeMillis();
            masterEntry.getConnectionsSemaphore().acquireUninterruptibly();
            long endTime = System.currentTimeMillis() - time;
            log.warn("Master connection acquired, time spended: {} ms", endTime);
        }
    }

    @Override
    public Future unsubscribe(String channelName) {
        final PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null) {
            return group.next().newSucceededFuture(null);
        }
        
        Future future = entry.unsubscribe(channelName);
        future.addListener(new FutureListener() {
            @Override
            public void operationComplete(Future future) throws Exception {
                synchronized (entry) {
                    if (entry.tryClose()) {
                        returnSubscribeConnection(entry);
                    }
                }
            }
        });
        return future;
    }

    protected void returnSubscribeConnection(PubSubConnectionEntry entry) {
        balancer.returnSubscribeConnection(entry.getConnection());
    }

    public void releaseWrite(RedisConnection connection) {
        // may changed during changeMaster call
        if (!masterEntry.getClient().equals(connection.getRedisClient())) {
            connection.close();
            return;
        }

        masterEntry.getConnections().add(connection);
        masterEntry.getConnectionsSemaphore().release();
    }

    public void releaseRead(RedisConnection сonnection) {
        balancer.returnConnection(сonnection);
    }

    @Override
    public void shutdown() {
        masterEntry.getClient().shutdown();
        balancer.shutdown();

        group.shutdownGracefully().syncUninterruptibly();
    }

    @Override
    public EventLoopGroup getGroup() {
        return group;
    }

}
