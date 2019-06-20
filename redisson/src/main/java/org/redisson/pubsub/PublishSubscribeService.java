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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.api.RFuture;
import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.ChannelName;
import org.redisson.client.RedisNodeNotFoundException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.SubscribeListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.pubsub.PubSubStatusMessage;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;

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
    
    private final ConcurrentMap<ChannelName, PubSubConnectionEntry> name2PubSubConnection = new ConcurrentHashMap<>();
    
    private final Queue<PubSubConnectionEntry> freePubSubConnections = new ConcurrentLinkedQueue<>();

    private final SemaphorePubSub semaphorePubSub = new SemaphorePubSub(this);
    
    private final CountDownLatchPubSub countDownLatchPubSub = new CountDownLatchPubSub(this);
    
    private final LockPubSub lockPubSub = new LockPubSub(this);
    
    public PublishSubscribeService(ConnectionManager connectionManager, MasterSlaveServersConfig config) {
        super();
        this.connectionManager = connectionManager;
        this.config = config;
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new AsyncSemaphore(1);
        }
    }
    
    public LockPubSub getLockPubSub() {
        return lockPubSub;
    }
    
    public CountDownLatchPubSub getCountDownLatchPubSub() {
        return countDownLatchPubSub;
    }
    
    public SemaphorePubSub getSemaphorePubSub() {
        return semaphorePubSub;
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

    private RFuture<PubSubConnectionEntry> subscribe(PubSubType type, Codec codec, ChannelName channelName,
            RPromise<PubSubConnectionEntry> promise, RedisPubSubListener<?>... listeners) {
        AsyncSemaphore lock = getSemaphore(channelName);
        lock.acquire(new Runnable() {
            @Override
            public void run() {
                if (promise.isDone()) {
                    lock.release();
                    return;
                }
                
                RPromise<PubSubConnectionEntry> result = new RedissonPromise<PubSubConnectionEntry>();
                promise.onComplete((res, e) -> {
                    if (e != null) {
                        result.tryFailure(e);
                    }
                });
                result.onComplete((res, e) -> {
                    if (e != null) {
                        promise.tryFailure(e);
                        return;
                    }
                    
                    promise.trySuccess(res);
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
    
    private void subscribe(Codec codec, ChannelName channelName, 
            RPromise<PubSubConnectionEntry> promise, PubSubType type, AsyncSemaphore lock, RedisPubSubListener<?>... listeners) {
        PubSubConnectionEntry connEntry = name2PubSubConnection.get(channelName);
        if (connEntry != null) {
            addListeners(channelName, promise, type, lock, connEntry, listeners);
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
                
                PubSubConnectionEntry freeEntry = freePubSubConnections.peek();
                if (freeEntry == null) {
                    connect(codec, channelName, promise, type, lock, listeners);
                    return;
                }
                
                int remainFreeAmount = freeEntry.tryAcquire();
                if (remainFreeAmount == -1) {
                    throw new IllegalStateException();
                }
                
                PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, freeEntry);
                if (oldEntry != null) {
                    freeEntry.release();
                    freePubSubLock.release();
                    
                    addListeners(channelName, promise, type, lock, oldEntry, listeners);
                    return;
                }
                
                if (remainFreeAmount == 0) {
                    freePubSubConnections.poll();
                }
                freePubSubLock.release();
                
                RFuture<Void> subscribeFuture = addListeners(channelName, promise, type, lock, freeEntry, listeners);
                
                ChannelFuture future;
                if (PubSubType.PSUBSCRIBE == type) {
                    future = freeEntry.psubscribe(codec, channelName);
                } else {
                    future = freeEntry.subscribe(codec, channelName);
                }
                
                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            if (!promise.isDone()) {
                                subscribeFuture.cancel(false);
                            }
                            return;
                        }
                        
                        connectionManager.newTimeout(new TimerTask() {
                            @Override
                            public void run(Timeout timeout) throws Exception {
                                subscribeFuture.cancel(false);
                            }
                        }, config.getTimeout(), TimeUnit.MILLISECONDS);
                    }
                });
            }
            
        });
    }

    private RFuture<Void> addListeners(ChannelName channelName, RPromise<PubSubConnectionEntry> promise,
            PubSubType type, AsyncSemaphore lock, PubSubConnectionEntry connEntry,
            RedisPubSubListener<?>... listeners) {
        for (RedisPubSubListener<?> listener : listeners) {
            connEntry.addListener(channelName, listener);
        }
        SubscribeListener list = connEntry.getSubscribeFuture(channelName, type);
        RFuture<Void> subscribeFuture = list.getSuccessFuture();
        
        subscribeFuture.onComplete((res, e) -> {
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
        });

        return subscribeFuture;
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
    
    private void connect(Codec codec, ChannelName channelName,
            RPromise<PubSubConnectionEntry> promise, PubSubType type, AsyncSemaphore lock, RedisPubSubListener<?>... listeners) {
        int slot = connectionManager.calcSlot(channelName.getName());
        RFuture<RedisPubSubConnection> connFuture = nextPubSubConnection(slot);
        promise.onComplete((res, e) -> {
            if (e != null) {
                ((RPromise<RedisPubSubConnection>) connFuture).tryFailure(e);
            }
        });
        connFuture.onComplete((conn, e) -> {
            if (e != null) {
                freePubSubLock.release();
                lock.release();
                promise.tryFailure(e);
                return;
            }
            
            PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
            int remainFreeAmount = entry.tryAcquire();
            
            PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
            if (oldEntry != null) {
                releaseSubscribeConnection(slot, entry);
                
                freePubSubLock.release();

                addListeners(channelName, promise, type, lock, oldEntry, listeners);
                return;
            }
            
            if (remainFreeAmount > 0) {
                freePubSubConnections.add(entry);
            }
            freePubSubLock.release();
            
            RFuture<Void> subscribeFuture = addListeners(channelName, promise, type, lock, entry, listeners);
            
            ChannelFuture future;
            if (PubSubType.PSUBSCRIBE == type) {
                future = entry.psubscribe(codec, channelName);
            } else {
                future = entry.subscribe(codec, channelName);
            }
            
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        if (!promise.isDone()) {
                            subscribeFuture.cancel(false);
                        }
                        return;
                    }
                    
                    connectionManager.newTimeout(new TimerTask() {
                        @Override
                        public void run(Timeout timeout) throws Exception {
                            subscribeFuture.cancel(false);
                        }
                    }, config.getTimeout(), TimeUnit.MILLISECONDS);
                }
            });
        });
    }
    
    public RFuture<Void> unsubscribe(ChannelName channelName, AsyncSemaphore lock) {
        PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null || connectionManager.isShuttingDown()) {
            lock.release();
            return RedissonPromise.newSucceededFuture(null);
        }
        
        AtomicBoolean executed = new AtomicBoolean();
        RedissonPromise<Void> result = new RedissonPromise<Void>();
        ChannelFuture future = entry.unsubscribe(channelName, new BaseRedisPubSubListener() {
            
            @Override
            public boolean onStatus(PubSubType type, CharSequence channel) {
                if (type == PubSubType.UNSUBSCRIBE && channel.equals(channelName)) {
                    executed.set(true);
                    
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
        
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                
                connectionManager.newTimeout(new TimerTask() {
                    @Override
                    public void run(Timeout timeout) throws Exception {
                        if (executed.get()) {
                            return;
                        }
                        entry.getConnection().onMessage(new PubSubStatusMessage(PubSubType.UNSUBSCRIBE, channelName));
                    }
                }, config.getTimeout(), TimeUnit.MILLISECONDS);
            }
        });
        
        return result;
    }
    
    public RFuture<Codec> unsubscribe(ChannelName channelName, PubSubType topicType) {
        if (connectionManager.isShuttingDown()) {
            return RedissonPromise.newSucceededFuture(null);
        }

        RPromise<Codec> result = new RedissonPromise<>();
        AsyncSemaphore lock = getSemaphore(channelName);
        lock.acquire(new Runnable() {
            @Override
            public void run() {
                PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
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
                        
                        Codec entryCodec;
                        if (topicType == PubSubType.PUNSUBSCRIBE) {
                            entryCodec = entry.getConnection().getPatternChannels().get(channelName);
                        } else {
                            entryCodec = entry.getConnection().getChannels().get(channelName);
                        }
                        
                        AtomicBoolean executed = new AtomicBoolean();
                        RedisPubSubListener<Object> listener = new BaseRedisPubSubListener() {

                            @Override
                            public boolean onStatus(PubSubType type, CharSequence channel) {
                                if (type == topicType && channel.equals(channelName)) {
                                    executed.set(true);
                                    
                                    lock.release();
                                    result.trySuccess(entryCodec);
                                    return true;
                                }
                                return false;
                            }

                        };

                        ChannelFuture future;
                        if (topicType == PubSubType.PUNSUBSCRIBE) {
                            future = entry.punsubscribe(channelName, listener);
                        } else {
                            future = entry.unsubscribe(channelName, listener);
                        }
                        
                        future.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if (!future.isSuccess()) {
                                    return;
                                }
                                
                                connectionManager.newTimeout(new TimerTask() {
                                    @Override
                                    public void run(Timeout timeout) throws Exception {
                                        if (executed.get()) {
                                            return;
                                        }
                                        entry.getConnection().onMessage(new PubSubStatusMessage(topicType, channelName));
                                    }
                                }, config.getTimeout(), TimeUnit.MILLISECONDS);
                            }
                        });
                    }
                });
            }
        });
        
        return result;
    }
    
    public void punsubscribe(ChannelName channelName, AsyncSemaphore lock) {
        PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null || connectionManager.isShuttingDown()) {
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

    private void reattachPubSubListeners(ChannelName channelName, Collection<RedisPubSubListener<?>> listeners, PubSubType topicType) {
        RFuture<Codec> subscribeCodecFuture = unsubscribe(channelName, topicType);
        if (listeners.isEmpty()) {
            return;
        }
        
        subscribeCodecFuture.onComplete((subscribeCodec, e) -> {
            if (subscribeCodec == null) {
                return;
            }
            
            if (topicType == PubSubType.PUNSUBSCRIBE) {
                psubscribe(channelName, listeners, subscribeCodec);
            } else {
                subscribe(channelName, listeners, subscribeCodec);
            }
        });
    }

    private void subscribe(ChannelName channelName, Collection<RedisPubSubListener<?>> listeners,
            Codec subscribeCodec) {
        RFuture<PubSubConnectionEntry> subscribeFuture = subscribe(subscribeCodec, channelName, listeners.toArray(new RedisPubSubListener[listeners.size()]));
        subscribeFuture.onComplete((res, e) -> {
            if (e != null) {
                subscribe(channelName, listeners, subscribeCodec);
                return;
            }
            
            log.info("listeners of '{}' channel to '{}' have been resubscribed", channelName, res.getConnection().getRedisClient());
        });
    }

    private void psubscribe(ChannelName channelName, Collection<RedisPubSubListener<?>> listeners,
            Codec subscribeCodec) {
        RFuture<PubSubConnectionEntry> subscribeFuture = psubscribe(channelName, subscribeCodec, listeners.toArray(new RedisPubSubListener[listeners.size()]));
        subscribeFuture.onComplete((res, e) -> {
            if (e != null) {
                psubscribe(channelName, listeners, subscribeCodec);
                return;
            }
            
            log.info("listeners of '{}' channel-pattern to '{}' have been resubscribed", channelName, res.getConnection().getRedisClient());
        });
    }
    
    
}
