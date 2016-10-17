/**
 * Copyright 2016 Nikita Koksharov
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
import java.net.URI;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.core.NodeType;
import org.redisson.misc.InfinitySemaphoreLatch;
import org.redisson.pubsub.AsyncSemaphore;

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
    
    URI getLastClusterNode();

    boolean isClusterMode();

    AsyncSemaphore getSemaphore(String channelName);
    
    <R> Future<R> newSucceededFuture(R value);

    ConnectionEventsHub getConnectionEventsHub();

    boolean isShutdown();

    boolean isShuttingDown();

    Future<PubSubConnectionEntry> subscribe(Codec codec, String channelName, RedisPubSubListener<?> listener);

    Future<PubSubConnectionEntry> subscribe(Codec codec, String channelName, RedisPubSubListener<?> listener, AsyncSemaphore semaphore);
    
    ConnectionInitializer getConnectListener();

    IdleConnectionWatcher getConnectionWatcher();

    <R> Future<R> newFailedFuture(Throwable cause);

    Collection<RedisClientEntry> getClients();

    void shutdownAsync(RedisClient client);

    int calcSlot(String key);

    MasterSlaveServersConfig getConfig();

    Codec getCodec();

    Set<MasterSlaveEntry> getEntrySet();
    
    MasterSlaveEntry getEntry(int slot);
    
    <R> Promise<R> newPromise();

    void releaseRead(NodeSource source, RedisConnection connection);

    void releaseWrite(NodeSource source, RedisConnection connection);

    Future<RedisConnection> connectionReadOp(NodeSource source, RedisCommand<?> command);

    Future<RedisConnection> connectionWriteOp(NodeSource source, RedisCommand<?> command);

    RedisClient createClient(String host, int port, int timeout, int commandTimeout);

    RedisClient createClient(NodeType type, String host, int port);

    MasterSlaveEntry getEntry(InetSocketAddress addr);

    PubSubConnectionEntry getPubSubEntry(String channelName);

    Future<PubSubConnectionEntry> psubscribe(String pattern, Codec codec, RedisPubSubListener<?> listener);
    
    Future<PubSubConnectionEntry> psubscribe(String pattern, Codec codec, RedisPubSubListener<?> listener, AsyncSemaphore semaphore);

    Codec unsubscribe(String channelName, AsyncSemaphore lock);
    
    Future<Codec> unsubscribe(String channelName, boolean temporaryDown);

    Future<Codec> punsubscribe(String channelName, boolean temporaryDown);

    Codec punsubscribe(String channelName, AsyncSemaphore lock);
    
    void shutdown();

    void shutdown(long quietPeriod, long timeout, TimeUnit unit);
    
    EventLoopGroup getGroup();

    Timeout newTimeout(TimerTask task, long delay, TimeUnit unit);

    InfinitySemaphoreLatch getShutdownLatch();
    
    Future<Boolean> getShutdownPromise();

}
