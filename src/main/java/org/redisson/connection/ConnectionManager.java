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

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.core.NodeType;
import org.redisson.misc.InfinitySemaphoreLatch;

import io.netty.channel.EventLoopGroup;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface ConnectionManager {

    boolean isClusterMode();

    <R> Future<R> newSucceededFuture(R value);

    ConnectionEventsHub getConnectionEventsHub();

    boolean isShutdown();

    boolean isShuttingDown();

    Promise<PubSubConnectionEntry> subscribe(Codec codec, String channelName, RedisPubSubListener<?> listener);

    ConnectionInitializer getConnectListener();

    IdleConnectionWatcher getConnectionWatcher();

    <R> Future<R> newFailedFuture(Throwable cause);

    Collection<RedisClientEntry> getClients();

    void shutdownAsync(RedisClient client);

    int calcSlot(String key);

    MasterSlaveServersConfig getConfig();

    Codec getCodec();

    Map<ClusterSlotRange, MasterSlaveEntry> getEntries();

    <R> Promise<R> newPromise();

    void releaseRead(NodeSource source, RedisConnection connection);

    void releaseWrite(NodeSource source, RedisConnection connection);

    Future<RedisConnection> connectionReadOp(NodeSource source, RedisCommand<?> command);

    Future<RedisConnection> connectionWriteOp(NodeSource source, RedisCommand<?> command);

    RedisClient createClient(String host, int port, int timeout);

    RedisClient createClient(NodeType type, String host, int port);

    MasterSlaveEntry getEntry(InetSocketAddress addr);

    PubSubConnectionEntry getPubSubEntry(String channelName);

    Future<PubSubConnectionEntry> psubscribe(String pattern, Codec codec);

    Codec unsubscribe(String channelName);

    Codec punsubscribe(String channelName);

    void shutdown();

    EventLoopGroup getGroup();

    Timeout newTimeout(TimerTask task, long delay, TimeUnit unit);

    InfinitySemaphoreLatch getShutdownLatch();
    
    Future<Boolean> getShutdownPromise();

}
