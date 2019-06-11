/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.command.CommandSyncService;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.misc.InfinitySemaphoreLatch;
import org.redisson.misc.RedisURI;
import org.redisson.pubsub.PublishSubscribeService;

import io.netty.channel.EventLoopGroup;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface ConnectionManager {
    
    RedisURI applyNatMap(RedisURI address);
    
    UUID getId();
    
    CommandSyncService getCommandExecutor();
    
    PublishSubscribeService getSubscribeService();
    
    ExecutorService getExecutor();
    
    RedisURI getLastClusterNode();
    
    Config getCfg();

    boolean isClusterMode();

    ConnectionEventsHub getConnectionEventsHub();

    boolean isShutdown();

    boolean isShuttingDown();
    
    IdleConnectionWatcher getConnectionWatcher();

    int calcSlot(String key);
    
    int calcSlot(byte[] key);

    MasterSlaveServersConfig getConfig();

    Codec getCodec();

    Collection<MasterSlaveEntry> getEntrySet();
    
    MasterSlaveEntry getEntry(int slot);
    
    MasterSlaveEntry getEntry(InetSocketAddress address);
    
    void releaseRead(NodeSource source, RedisConnection connection);

    void releaseWrite(NodeSource source, RedisConnection connection);

    RFuture<RedisConnection> connectionReadOp(NodeSource source, RedisCommand<?> command);

    RFuture<RedisConnection> connectionWriteOp(NodeSource source, RedisCommand<?> command);

    RedisClient createClient(NodeType type, RedisURI address, int timeout, int commandTimeout, String sslHostname);

    RedisClient createClient(NodeType type, InetSocketAddress address, RedisURI uri, String sslHostname);
    
    RedisClient createClient(NodeType type, RedisURI address, String sslHostname);

    MasterSlaveEntry getEntry(RedisClient redisClient);
    
    void shutdown();

    void shutdown(long quietPeriod, long timeout, TimeUnit unit);
    
    EventLoopGroup getGroup();

    Timeout newTimeout(TimerTask task, long delay, TimeUnit unit);

    InfinitySemaphoreLatch getShutdownLatch();
    
    Future<Void> getShutdownPromise();

}
