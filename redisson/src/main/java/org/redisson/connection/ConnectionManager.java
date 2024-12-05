/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import io.netty.buffer.ByteBuf;
import org.redisson.api.NodeType;
import org.redisson.client.RedisClient;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.config.*;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.misc.RedisURI;
import org.redisson.pubsub.PublishSubscribeService;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface ConnectionManager {

    void connect();

    PublishSubscribeService getSubscribeService();
    
    RedisURI getLastClusterNode();

    int calcSlot(String key);

    int calcSlot(ByteBuf key);

    int calcSlot(byte[] key);

    Collection<MasterSlaveEntry> getEntrySet();

    MasterSlaveEntry getEntry(String name);

    MasterSlaveEntry getEntry(int slot);

    MasterSlaveEntry getWriteEntry(int slot);

    MasterSlaveEntry getReadEntry(int slot);
    
    MasterSlaveEntry getEntry(InetSocketAddress address);

    MasterSlaveEntry getEntry(RedisURI addr);

    RedisClient createClient(NodeType type, InetSocketAddress address, RedisURI uri, String sslHostname);
    
    RedisClient createClient(NodeType type, RedisURI address, String sslHostname);

    MasterSlaveEntry getEntry(RedisClient redisClient);
    
    void shutdown();

    void shutdown(long quietPeriod, long timeout, TimeUnit unit);
    
    ServiceManager getServiceManager();

    CommandAsyncExecutor createCommandExecutor(RedissonObjectBuilder objectBuilder,
                                               RedissonObjectBuilder.ReferenceType referenceType);

    static ConnectionManager create(Config configCopy) {
        BaseConfig<?> cfg = ConfigSupport.getConfig(configCopy);
        ConnectionManager cm = null;
        if (cfg instanceof MasterSlaveServersConfig) {
            cm = new MasterSlaveConnectionManager((MasterSlaveServersConfig) cfg, configCopy);
        } else if (cfg instanceof SingleServerConfig) {
            cm = new SingleConnectionManager((SingleServerConfig) cfg, configCopy);
        } else if (cfg instanceof SentinelServersConfig) {
            cm = new SentinelConnectionManager((SentinelServersConfig) cfg, configCopy);
        } else if (cfg instanceof ClusterServersConfig) {
            cm = new ClusterConnectionManager((ClusterServersConfig) cfg, configCopy);
        } else if (cfg instanceof ReplicatedServersConfig) {
            cm = new ReplicatedConnectionManager((ReplicatedServersConfig) cfg, configCopy);
        }

        if (cm == null) {
            throw new IllegalArgumentException("server(s) address(es) not defined!");
        }
        if (!configCopy.isLazyInitialization()) {
            cm.connect();
        }
        return cm;
    }

}
