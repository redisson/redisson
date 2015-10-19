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
package org.redisson.misc;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.connection.LoadBalancer;
import org.redisson.connection.SubscribesConnectionEntry;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;

public class PubSubConnectionPoll extends ConnectionPool<RedisPubSubConnection> {

    public PubSubConnectionPoll(MasterSlaveServersConfig config,
            LoadBalancer loadBalancer, EventLoopGroup eventLoopGroup) {
        super(config, loadBalancer, eventLoopGroup);
    }

    @Override
    protected RedisPubSubConnection poll(SubscribesConnectionEntry entry) {
        return entry.pollFreeSubscribeConnection();
    }

    @Override
    protected Future<RedisPubSubConnection> connect(SubscribesConnectionEntry entry) {
        return entry.connectPubSub(config);
    }

    @Override
    protected boolean tryAcquireConnection(SubscribesConnectionEntry entry) {
        return entry.tryAcquireSubscribeConnection();
    }

    @Override
    protected void releaseConnection(SubscribesConnectionEntry entry) {
        entry.releaseSubscribeConnection();
    }

    @Override
    protected void releaseConnection(SubscribesConnectionEntry entry, RedisPubSubConnection conn) {
        entry.releaseSubscribeConnection(conn);
    }

}
