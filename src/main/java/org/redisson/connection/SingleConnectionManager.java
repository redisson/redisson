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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import org.redisson.Config;
import org.redisson.MasterSlaveServersConfig;
import org.redisson.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.RedisConnectionException;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

public class SingleConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Semaphore subscribeConnectionsSemaphore;
    private final Queue<RedisPubSubConnection> subscribeConnections = new ConcurrentLinkedQueue<RedisPubSubConnection>();

    public SingleConnectionManager(SingleServerConfig cfg, Config config) {
        MasterSlaveServersConfig newconfig = new MasterSlaveServersConfig();
        String addr = cfg.getAddress().getHost() + ":" + cfg.getAddress().getPort();
        newconfig.setPassword(cfg.getPassword());
        newconfig.setMasterAddress(addr);
        newconfig.setMasterConnectionPoolSize(cfg.getConnectionPoolSize());
        newconfig.setSubscriptionsPerConnection(cfg.getSubscriptionsPerConnection());

        subscribeConnectionsSemaphore = new Semaphore(cfg.getSubscriptionConnectionPoolSize());
        init(newconfig, config);
    }

    private void acquireSubscribeConnection() {
        if (!subscribeConnectionsSemaphore.tryAcquire()) {
            log.warn("Subscribe connection pool gets exhausted! Trying to acquire connection ...");
            long time = System.currentTimeMillis();
            subscribeConnectionsSemaphore.acquireUninterruptibly();
            long endTime = System.currentTimeMillis() - time;
            log.warn("Subscribe connection acquired, time spended: {} ms", endTime);
        }
    }

    @Override
    RedisPubSubConnection nextPubSubConnection() {
        acquireSubscribeConnection();

        RedisPubSubConnection conn = subscribeConnections.poll();
        if (conn != null) {
            return conn;
        }

        try {
            conn = masterEntry.getClient().connectPubSub(codec);
            if (config.getPassword() != null) {
                conn.auth(config.getPassword());
            }
            return conn;
        } catch (RedisConnectionException e) {
            masterEntry.getConnectionsSemaphore().release();
            throw e;
        }
    }

    @Override
    protected void returnSubscribeConnection(PubSubConnectionEntry entry) {
        subscribeConnections.add(entry.getConnection());
        subscribeConnectionsSemaphore.release();
    }

    @Override
    public <K, V> RedisConnection<K, V> connectionReadOp() {
        return super.connectionWriteOp();
    }

    @Override
    public void releaseRead(RedisConnection сonnection) {
        super.releaseWrite(сonnection);
    }

}
