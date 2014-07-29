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

import java.net.URI;
import java.util.Collection;

import org.redisson.MasterSlaveServersConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.RedisConnectionException;
import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

/**
 *
 * @author Nikita Koksharov
 *
 */
//TODO ping support
public class MasterSlaveEntry {

    private final Logger log = LoggerFactory.getLogger(getClass());

    LoadBalancer slaveBalancer;
    volatile ConnectionEntry masterEntry;

    final MasterSlaveServersConfig config;
    final RedisCodec codec;
    final EventLoopGroup group;

    public MasterSlaveEntry(RedisCodec codec, EventLoopGroup group, MasterSlaveServersConfig config) {
        this.codec = codec;
        this.group = group;
        this.config = config;

        slaveBalancer = config.getLoadBalancer();
        slaveBalancer.init(codec, config.getPassword());
        for (URI address : config.getSlaveAddresses()) {
            RedisClient client = new RedisClient(group, address.getHost(), address.getPort());
            SubscribesConnectionEntry entry = new SubscribesConnectionEntry(client,
                    config.getSlaveConnectionPoolSize(),
                    config.getSlaveSubscriptionConnectionPoolSize());
            slaveBalancer.add(entry);
        }
        if (config.getSlaveAddresses().size() > 1) {
            slaveDown(config.getMasterAddress().getHost(), config.getMasterAddress().getPort());
        }

        setupMasterEntry(config.getMasterAddress().getHost(), config.getMasterAddress().getPort());
    }

    public void setupMasterEntry(String host, int port) {
        RedisClient masterClient = new RedisClient(group, host, port);
        masterEntry = new ConnectionEntry(masterClient, config.getMasterConnectionPoolSize());
    }

    public Collection<RedisPubSubConnection> slaveDown(String host, int port) {
        return slaveBalancer.freeze(host, port);
    }

    public void addSlave(String host, int port) {
        slaveDown(masterEntry.getClient().getAddr().getHostName(), masterEntry.getClient().getAddr().getPort());

        RedisClient client = new RedisClient(group, host, port);
        slaveBalancer.add(new SubscribesConnectionEntry(client,
                this.config.getSlaveConnectionPoolSize(),
                this.config.getSlaveSubscriptionConnectionPoolSize()));
    }

    public void slaveUp(String host, int port) {
        slaveBalancer.unfreeze(host, port);
    }

    /**
     * Freeze slave with <code>host:port</code> from slaves list.
     * Re-attach pub/sub listeners from it to other slave.
     * Shutdown old master client.
     *
     */
    public void changeMaster(String host, int port) {
        ConnectionEntry oldMaster = masterEntry;
        setupMasterEntry(host, port);
        slaveDown(host, port);
        oldMaster.getClient().shutdown();
    }

    public <K, V> RedisConnection<K, V> connectionWriteOp() {
        acquireMasterConnection();

        RedisConnection<K, V> conn = masterEntry.getConnections().poll();
        if (conn != null) {
            return conn;
        }

        try {
            conn = masterEntry.getClient().connect(codec);
            if (config.getPassword() != null) {
                conn.auth(config.getPassword());
            }
            return conn;
        } catch (RedisConnectionException e) {
            masterEntry.getConnectionsSemaphore().release();
            throw e;
        }
    }

    public <K, V> RedisConnection<K, V> connectionReadOp() {
        return slaveBalancer.nextConnection();
    }

    RedisPubSubConnection nextPubSubConnection() {
        return slaveBalancer.nextPubSubConnection();
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

    public void returnSubscribeConnection(PubSubConnectionEntry entry) {
        slaveBalancer.returnSubscribeConnection(entry.getConnection());
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
        slaveBalancer.returnConnection(сonnection);
    }

    public void shutdown() {
        masterEntry.getClient().shutdown();
        slaveBalancer.shutdown();
    }

}
