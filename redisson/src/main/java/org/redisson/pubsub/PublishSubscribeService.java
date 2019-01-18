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
package org.redisson.pubsub;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RFuture;
import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.ChannelName;
import org.redisson.client.RedisNodeNotFoundException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.SubscribeListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class PublishSubscribeService {

    private static final Logger log = LoggerFactory.getLogger(PublishSubscribeService.class);
    
    private final ConnectionManager connectionManager;
    
    private final MasterSlaveServersConfig config;
    
    private final AsyncSemaphore[] locks = new AsyncSemaphore[50];
    
    private final AsyncSemaphore freePubSubLock = new AsyncSemaphore(1);
    
    protected final ConcurrentMap<ChannelName, PubSubConnectionEntry> name2PubSubConnection = PlatformDependent.newConcurrentHashMap();
    
    protected final Queue<PubSubConnectionEntry> freePubSubConnections = new ConcurrentLinkedQueue<PubSubConnectionEntry>();

    public PublishSubscribeService(ConnectionManager connectionManager, MasterSlaveServersConfig config) {
        super();
        this.connectionManager = connectionManager;
        this.config = config;
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new AsyncSemaphore(1);
        }
    }

    public PubSubConnectionEntry getPubSubEntry(ChannelName channelName) {
        return name2PubSubConnection.get(channelName);
    }

    public RFuture<PubSubConnectionEntry> psubscribe(ChannelName channelName, Codec codec, RedisPubSubListener<?>... listeners) {
        return subscribe(PubSubType.PSUBSCRIBE, codec, channelName, new RedissonPromise<PubSubConnectionEntry>(), listeners);
    }
    
    public RFuture<PubSubConnectionEntry> psubscribe(String channelName, Codec codec, AsyncSemaphore semaphore, RedisPubSubListener<?>... listeners) {
        RPromise<PubSubConnectionEntry> promise = new RedissonPromise<PubSubConnectionEntry>();
        subscribe(codec, new ChannelName(channelName), promise, PubSubType.PSUBSCRIBE, semaphore, listeners);
        return promise;
    }

    public RFuture<PubSubConnectionEntry> subscribe(Codec codec, ChannelName channelName, RedisPubSubListener<?>... listeners) {
        return subscribe(PubSubType.SUBSCRIBE, codec, channelName, new RedissonPromise<PubSubConnectionEntry>(), listeners);
    }

    private RFuture<PubSubConnectionEntry> subscribe(final PubSubType type, final Codec codec, final ChannelName channelName,
            final RPromise<PubSubConnectionEntry> promise, final RedisPubSubListener<?>... listeners) {
        final AsyncSemaphore lock = getSemaphore(channelName);
        lock.acquire(new Runnable() {
            @Override
            public void run() {
                if (promise.isDone()) {
                    lock.release();
                    return;
                }
                
                final RPromise<PubSubConnectionEntry> result = new RedissonPromise<PubSubConnectionEntry>();
                promise.addListener(new FutureListener<PubSubConnectionEntry>() {
                    @Override
                    public void operationComplete(Future<PubSubConnectionEntry> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                        }
                    }
                });
                result.addListener(new FutureListener<PubSubConnectionEntry>() {
                    @Override
                    public void operationComplete(Future<PubSubConnectionEntry> future) throws Exception {
                        if (!future.isSuccess()) {
                            promise.tryFailure(future.cause());
                            return;
                        }
                        
                        promise.trySuccess(result.getNow());
                    }
                });
                subscribe(codec, channelName, result, type, lock, listeners);
            }
        });
        return promise;
    }
    
    public RFuture<PubSubConnectionEntry> subscribe(Codec codec, String channelName, AsyncSemaphore semaphore, RedisPubSubListener<?>... listeners) {
        RPromise<PubSubConnectionEntry> promise = new RedissonPromise<PubSubConnectionEntry>();
        subscribe(codec, new ChannelName(channelName), promise, PubSubType.SUBSCRIBE, semaphore, listeners);
        return promise;
    }

    public AsyncSemaphore getSemaphore(ChannelName channelName) {
        return locks[Math.abs(channelName.hashCode() % locks.length)];
    }
    
    private void subscribe(final Codec codec, final ChannelName channelName, 
            final RPromise<PubSubConnectionEntry> promise, final PubSubType type, final AsyncSemaphore lock, final RedisPubSubListener<?>... listeners) {
        final PubSubConnectionEntry connEntry = name2PubSubConnection.get(channelName);
        if (connEntry != null) {
            subscribe(channelName, promise, type, lock, connEntry, listeners);
            return;
        }

        freePubSubLock.acquire(new Runnable() {

            @Override
            public void run() {
                if (promise.isDone()) {
                    lock.release();
                    freePubSubLock.release();
                    return;
                }
                
                final PubSubConnectionEntry freeEntry = freePubSubConnections.peek();
                if (freeEntry == null) {
                    connect(codec, channelName, promise, type, lock, listeners);
                    return;
                }
                
                int remainFreeAmount = freeEntry.tryAcquire();
                if (remainFreeAmount == -1) {
                    throw new IllegalStateException();
                }
                
                final PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, freeEntry);
                if (oldEntry != null) {
                    freeEntry.release();
                    freePubSubLock.release();
                    
                    subscribe(channelName, promise, type, lock, oldEntry, listeners);
                    return;
                }
                
                if (remainFreeAmount == 0) {
                    freePubSubConnections.poll();
                }
                freePubSubLock.release();
                
                subscribe(channelName, promise, type, lock, freeEntry, listeners);
                
                if (PubSubType.PSUBSCRIBE == type) {
                    freeEntry.psubscribe(codec, channelName);
                } else {
                    freeEntry.subscribe(codec, channelName);
                }
            }
            
        });
    }

    private void subscribe(final ChannelName channelName, final RPromise<PubSubConnectionEntry> promise,
            final PubSubType type, final AsyncSemaphore lock, final PubSubConnectionEntry connEntry,
            final RedisPubSubListener<?>... listeners) {
        for (RedisPubSubListener<?> listener : listeners) {
            connEntry.addListener(channelName, listener);
        }
        SubscribeListener listener = connEntry.getSubscribeFuture(channelName, type);
        final Future<Void> subscribeFuture = listener.getSuccessFuture();
        
        subscribeFuture.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!promise.trySuccess(connEntry)) {
                    for (RedisPubSubListener<?> listener : listeners) {
                        connEntry.removeListener(channelName, listener);
                    }
                    if (!connEntry.hasListeners(channelName)) {
                        unsubscribe(channelName, lock);
                    } else {
                        lock.release();
                    }
                } else {
                    lock.release();
                }
            }
        });

        connectionManager.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (promise.tryFailure(new RedisTimeoutException())) {
                    subscribeFuture.cancel(false);
                }
            }
        }, config.getRetryInterval(), TimeUnit.MILLISECONDS);
    }

    private void releaseSubscribeConnection(int slot, PubSubConnectionEntry pubSubEntry) {
        MasterSlaveEntry entry = connectionManager.getEntry(slot);
        if (entry == null) {
            log.error("Node for slot: " + slot + " can't be found");
        } else {
            entry.returnPubSubConnection(pubSubEntry);
        }
    }
    
    private RFuture<RedisPubSubConnection> nextPubSubConnection(int slot) {
        MasterSlaveEntry entry = connectionManager.getEntry(slot);
        if (entry == null) {
            RedisNodeNotFoundException ex = new RedisNodeNotFoundException("Node for slot: " + slot + " hasn't been discovered yet");
            return RedissonPromise.newFailedFuture(ex);
        }
        return entry.nextPubSubConnection();
    }
    
    private void connect(final Codec codec, final ChannelName channelName,
            final RPromise<PubSubConnectionEntry> promise, final PubSubType type, final AsyncSemaphore lock, final RedisPubSubListener<?>... listeners) {
        final int slot = connectionManager.calcSlot(channelName.getName());
        final RFuture<RedisPubSubConnection> connFuture = nextPubSubConnection(slot);
        promise.addListener(new FutureListener<PubSubConnectionEntry>() {
            @Override
            public void operationComplete(Future<PubSubConnectionEntry> future) throws Exception {
                if (!future.isSuccess()) {
                    ((RPromise<RedisPubSubConnection>)connFuture).tryFailure(future.cause());
                }
            }
        });
        connFuture.addListener(new FutureListener<RedisPubSubConnection>() {

            @Override
            public void operationComplete(Future<RedisPubSubConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    freePubSubLock.release();
                    lock.release();
                    promise.tryFailure(future.cause());
                    return;
                }

                RedisPubSubConnection conn = future.getNow();
                
                final PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
                entry.tryAcquire();
                
                final PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
                if (oldEntry != null) {
                    releaseSubscribeConnection(slot, entry);
                    
                    freePubSubLock.release();

                    subscribe(channelName, promise, type, lock, oldEntry, listeners);
                    return;
                }
                
                freePubSubConnections.add(entry);
                freePubSubLock.release();
                
                subscribe(channelName, promise, type, lock, entry, listeners);
                
                if (PubSubType.PSUBSCRIBE == type) {
                    entry.psubscribe(codec, channelName);
                } else {
                    entry.subscribe(codec, channelName);
                }
                
            }
        });
    }
    
    public RFuture<Void> unsubscribe(final ChannelName channelName, final AsyncSemaphore lock) {
        final PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null || connectionManager.isShuttingDown()) {
            lock.release();
            return RedissonPromise.newSucceededFuture(null);
        }
        
        final RedissonPromise<Void> result = new RedissonPromise<Void>();
        entry.unsubscribe(channelName, new BaseRedisPubSubListener() {
            
            @Override
            public boolean onStatus(PubSubType type, CharSequence channel) {
                if (type == PubSubType.UNSUBSCRIBE && channel.equals(channelName)) {
                    
                    if (entry.release() == 1) {
                        freePubSubConnections.add(entry);
                    }
                    
                    lock.release();
                    result.trySuccess(null);
                    return true;
                }
                return false;
            }
            
        });
        return result;
    }
    
    public RFuture<Codec> unsubscribe(final ChannelName channelName, final PubSubType topicType) {
        if (connectionManager.isShuttingDown()) {
            return RedissonPromise.newSucceededFuture(null);
        }

        final RPromise<Codec> result = new RedissonPromise<Codec>();
        final AsyncSemaphore lock = getSemaphore(channelName);
        lock.acquire(new Runnable() {
            @Override
            public void run() {
                final PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
                if (entry == null) {
                    lock.release();
                    result.trySuccess(null);
                    return;
                }

                freePubSubLock.acquire(new Runnable() {
                    @Override
                    public void run() {
                        freePubSubConnections.remove(entry);
                        freePubSubLock.release();
                        
                        final Codec entryCodec = entry.getConnection().getChannels().get(channelName);
                        RedisPubSubListener<Object> listener = new BaseRedisPubSubListener() {

                            @Override
                            public boolean onStatus(PubSubType type, CharSequence channel) {
                                if (type == topicType && channel.equals(channelName)) {
                                    lock.release();
                                    result.trySuccess(entryCodec);
                                    return true;
                                }
                                return false;
                            }

                        };

                        if (topicType == PubSubType.PUNSUBSCRIBE) {
                            entry.punsubscribe(channelName, listener);
                        } else {
                            entry.unsubscribe(channelName, listener);
                        }
                    }
                });
            }
        });
        
        return result;
    }
    
    public void punsubscribe(final ChannelName channelName, final AsyncSemaphore lock) {
        final PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null) {
            lock.release();
            return;
        }
        
        entry.punsubscribe(channelName, new BaseRedisPubSubListener() {
            
            @Override
            public boolean onStatus(PubSubType type, CharSequence channel) {
                if (type == PubSubType.PUNSUBSCRIBE && channel.equals(channelName)) {
                    
                    if (entry.release() == 1) {
                        freePubSubConnections.add(entry);
                    }
                    
                    lock.release();
                    return true;
                }
                return false;
            }
            
        });
    }
    
    public void reattachPubSub(RedisPubSubConnection redisPubSubConnection) {
        for (ChannelName channelName : redisPubSubConnection.getChannels().keySet()) {
            PubSubConnectionEntry pubSubEntry = getPubSubEntry(channelName);
            Collection<RedisPubSubListener<?>> listeners = pubSubEntry.getListeners(channelName);
            reattachPubSubListeners(channelName, listeners, PubSubType.UNSUBSCRIBE);
        }

        for (ChannelName channelName : redisPubSubConnection.getPatternChannels().keySet()) {
            PubSubConnectionEntry pubSubEntry = getPubSubEntry(channelName);
            Collection<RedisPubSubListener<?>> listeners = pubSubEntry.getListeners(channelName);
            reattachPubSubListeners(channelName, listeners, PubSubType.PUNSUBSCRIBE);
        }
    }

    private void reattachPubSubListeners(final ChannelName channelName, final Collection<RedisPubSubListener<?>> listeners, final PubSubType topicType) {
        RFuture<Codec> subscribeCodec = unsubscribe(channelName, topicType);
        if (listeners.isEmpty()) {
            return;
        }
        
        subscribeCodec.addListener(new FutureListener<Codec>() {
            @Override
            public void operationComplete(Future<Codec> future) throws Exception {
                if (future.get() == null) {
                    return;
                }
                
                Codec subscribeCodec = future.get();
                if (topicType == PubSubType.PUNSUBSCRIBE) {
                    psubscribe(channelName, listeners, subscribeCodec);
                } else {
                    subscribe(channelName, listeners, subscribeCodec);
                }
            }

        });
    }

    private void subscribe(final ChannelName channelName, final Collection<RedisPubSubListener<?>> listeners,
            final Codec subscribeCodec) {
        RFuture<PubSubConnectionEntry> subscribeFuture = subscribe(subscribeCodec, channelName, listeners.toArray(new RedisPubSubListener[listeners.size()]));
        subscribeFuture.addListener(new FutureListener<PubSubConnectionEntry>() {
            
            @Override
            public void operationComplete(Future<PubSubConnectionEntry> future)
                    throws Exception {
                if (!future.isSuccess()) {
                    subscribe(channelName, listeners, subscribeCodec);
                    return;
                }
                
                log.info("listeners of '{}' channel to '{}' have been resubscribed", channelName, future.getNow().getConnection().getRedisClient());
            }
        });
    }

    private void psubscribe(final ChannelName channelName, final Collection<RedisPubSubListener<?>> listeners,
            final Codec subscribeCodec) {
        RFuture<PubSubConnectionEntry> subscribeFuture = psubscribe(channelName, subscribeCodec, listeners.toArray(new RedisPubSubListener[listeners.size()]));
        subscribeFuture.addListener(new FutureListener<PubSubConnectionEntry>() {
            @Override
            public void operationComplete(Future<PubSubConnectionEntry> future)
                    throws Exception {
                if (!future.isSuccess()) {
                    psubscribe(channelName, listeners, subscribeCodec);
                    return;
                }
                
                log.debug("resubscribed listeners for '{}' channel-pattern to '{}'", channelName, future.getNow().getConnection().getRedisClient());
            }
        });
    }
    
    
}
