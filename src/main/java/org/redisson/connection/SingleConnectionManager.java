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

import org.redisson.Config;
import org.redisson.MasterSlaveServersConfig;
import org.redisson.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.RedisConnectionException;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

public class SingleConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public SingleConnectionManager(SingleServerConfig cfg, Config config) {
        MasterSlaveServersConfig newconfig = new MasterSlaveServersConfig();
        String addr = cfg.getAddress().getHost() + ":" + cfg.getAddress().getPort();
        newconfig.setPassword(cfg.getPassword());
        newconfig.setMasterAddress(addr);
        newconfig.setMasterConnectionPoolSize(cfg.getConnectionPoolSize());
        newconfig.setSubscriptionsPerConnection(cfg.getSubscriptionsPerConnection());
        newconfig.setSlaveSubscriptionConnectionPoolSize(cfg.getSubscriptionConnectionPoolSize());

        init(newconfig, config);
    }

    @Override
    protected void setupMasterEntry(String host, int port) {
        RedisClient masterClient = new RedisClient(group, host, port);
        masterEntry = new SubscribesConnectionEntry(masterClient, config.getMasterConnectionPoolSize(), config.getSlaveSubscriptionConnectionPoolSize());
    }

    private void acquireSubscribeConnection() {
        if (!((SubscribesConnectionEntry)masterEntry).getSubscribeConnectionsSemaphore().tryAcquire()) {
            log.warn("Subscribe connection pool gets exhausted! Trying to acquire connection ...");
            long time = System.currentTimeMillis();
            ((SubscribesConnectionEntry)masterEntry).getSubscribeConnectionsSemaphore().acquireUninterruptibly();
            long endTime = System.currentTimeMillis() - time;
            log.warn("Subscribe connection acquired, time spended: {} ms", endTime);
        }
    }

    @Override
    RedisPubSubConnection nextPubSubConnection() {
        acquireSubscribeConnection();

        RedisPubSubConnection conn = ((SubscribesConnectionEntry)masterEntry).pollFreeSubscribeConnection();
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
            ((SubscribesConnectionEntry)masterEntry).getSubscribeConnectionsSemaphore().release();
            throw e;
        }
    }

    @Override
    protected void returnSubscribeConnection(PubSubConnectionEntry entry) {
        ((SubscribesConnectionEntry)masterEntry).offerFreeSubscribeConnection(entry.getConnection());
        ((SubscribesConnectionEntry)masterEntry).getSubscribeConnectionsSemaphore().release();
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
