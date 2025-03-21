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
package org.redisson.pubsub;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.redisson.PubSubMessageListener;
import org.redisson.PubSubPatternMessageListener;
import org.redisson.client.*;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.pubsub.PubSubStatusMessage;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.ServiceManager;
import org.redisson.misc.AsyncSemaphore;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class PubSubConnectionEntry {

    private final AtomicInteger subscribedChannelsAmount;
    private final RedisPubSubConnection conn;

    private final Map<ChannelName, SubscribeListener> subscribeChannelListeners = new ConcurrentHashMap<>();
    private final Map<ChannelName, Queue<RedisPubSubListener<?>>> channelListeners = new ConcurrentHashMap<>();

    private static final Queue<RedisPubSubListener<?>> EMPTY_QUEUE = new LinkedList<>();

    private final ServiceManager serviceManager;
    private final PublishSubscribeService subscribeService;
    private final MasterSlaveEntry entry;

    private static final Map<PubSubType, PubSubType> SUBSCRIBE2UNSUBSCRIBE = new HashMap<>();

    static {
        SUBSCRIBE2UNSUBSCRIBE.put(PubSubType.SUBSCRIBE, PubSubType.UNSUBSCRIBE);
        SUBSCRIBE2UNSUBSCRIBE.put(PubSubType.SSUBSCRIBE, PubSubType.SUNSUBSCRIBE);
        SUBSCRIBE2UNSUBSCRIBE.put(PubSubType.PSUBSCRIBE, PubSubType.PUNSUBSCRIBE);
    }

    public PubSubConnectionEntry(RedisPubSubConnection conn, ConnectionManager connectionManager, MasterSlaveEntry entry) {
        super();
        this.conn = conn;
        this.entry = entry;
        this.serviceManager = connectionManager.getServiceManager();
        this.subscribeService = connectionManager.getSubscribeService();
        this.subscribedChannelsAmount = new AtomicInteger(serviceManager.getConfig().getSubscriptionsPerConnection());
    }

    public MasterSlaveEntry getEntry() {
        return entry;
    }

    public int countListeners(ChannelName channelName) {
        return channelListeners.getOrDefault(channelName, EMPTY_QUEUE).size();
    }

    public boolean hasListeners(ChannelName channelName) {
        return channelListeners.containsKey(channelName);
    }

    public Queue<RedisPubSubListener<?>> getListeners(ChannelName channelName) {
        return channelListeners.getOrDefault(channelName, EMPTY_QUEUE);
    }

    public void addListener(ChannelName channelName, RedisPubSubListener<?> listener) {
        if (listener == null) {
            return;
        }

        channelListeners.compute(channelName, (k, queue) -> {
            if (queue == null) {
                queue = new ConcurrentLinkedQueue<>();
            }

            queue.add(listener);
            conn.addListener(channelName, listener);
            return queue;
        });
    }

    // TODO optimize
    public boolean removeListener(ChannelName channelName, EventListener msgListener) {
        Queue<RedisPubSubListener<?>> listeners = channelListeners.get(channelName);
        for (RedisPubSubListener<?> listener : listeners) {
            if (listener instanceof PubSubMessageListener) {
                if (((PubSubMessageListener<?>) listener).getListener() == msgListener) {
                    removeListener(channelName, listener);
                    return true;
                }
            }
            if (listener instanceof PubSubPatternMessageListener) {
                if (((PubSubPatternMessageListener<?>) listener).getListener() == msgListener) {
                    removeListener(channelName, listener);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean removeListener(ChannelName channelName, int listenerId) {
        Queue<RedisPubSubListener<?>> listeners = channelListeners.getOrDefault(channelName, EMPTY_QUEUE);
        for (RedisPubSubListener<?> listener : listeners) {
            if (listener instanceof PubSubMessageListener) {
                if (hasId(((PubSubMessageListener<?>) listener).getListener(), listenerId)) {
                    removeListener(channelName, listener);
                    return true;
                }
            }
            if (listener instanceof PubSubPatternMessageListener) {
                if (hasId(((PubSubPatternMessageListener<?>) listener).getListener(), listenerId)) {
                    removeListener(channelName, listener);
                    return true;
                }
            }
            if (hasId(listener, listenerId)) {
                removeListener(channelName, listener);
                return true;
            }
        }
        return false;
    }

    private boolean hasId(EventListener listener, int listenerId) {
        return System.identityHashCode(listener) == listenerId;
    }

    public void removeListener(ChannelName channelName, RedisPubSubListener<?> listener) {
        channelListeners.computeIfPresent(channelName, (k, queue) -> {
            if (queue.remove(listener) && queue.isEmpty()) {
                return null;
            }
            return queue;
        });

        conn.removeListener(channelName, listener);
    }

    public int tryAcquire() {
        while (true) {
            int value = subscribedChannelsAmount.get();
            if (value == 0) {
                return -1;
            }

            if (subscribedChannelsAmount.compareAndSet(value, value - 1)) {
                return value - 1;
            }
        }
    }

    public int release() {
        return subscribedChannelsAmount.incrementAndGet();
    }

    public boolean isFree() {
        return subscribedChannelsAmount.get() == serviceManager.getConfig().getSubscriptionsPerConnection();
    }

    public void subscribe(Codec codec, List<ChannelName> channelNames, CompletableFuture<PubSubConnectionEntry> pm,
                          PubSubType type, AsyncSemaphore lock, RedisPubSubListener<?>[] listeners) {
        CompletableFuture<PubSubConnectionEntry> pp = new CompletableFuture<>();
        pp.whenComplete((r, e) -> {
            if (e != null) {
                PubSubType unsubscribeType = SUBSCRIBE2UNSUBSCRIBE.get(type);

                List<CompletableFuture<?>> futures = new ArrayList<>(channelNames.size());
                for (ChannelName channelName : channelNames) {
                    CompletableFuture<?> f = subscribeService.unsubscribe(channelName, this, unsubscribeType);
                    futures.add(f);
                }
                CompletableFuture<Void> ff = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                ff.whenComplete((rr, ee) -> {
                    pm.completeExceptionally(e);
                });
                return;
            }

            pm.complete(r);
        });

        CompletableFuture<Void> subscribeFuture = addListeners(channelNames, pp, type, lock, listeners);
        CompletableFuture<Void> promise = new CompletableFuture<>();
        promise.whenComplete((r, ex) -> {
            if (ex != null) {
                subscribeFuture.completeExceptionally(ex);
            }
        });

        ChannelFuture future;
        if (PubSubType.SUBSCRIBE == type) {
            future = conn.subscribe(promise, codec, channelNames.toArray(new ChannelName[0]));
        } else if (PubSubType.SSUBSCRIBE == type) {
            future = conn.ssubscribe(promise, codec, channelNames.toArray(new ChannelName[0]));
        } else {
            future = conn.psubscribe(promise, codec, channelNames.toArray(new ChannelName[0]));
        }
        future.addListener((ChannelFutureListener) future1 -> {
            if (!future1.isSuccess()) {
                subscribeFuture.completeExceptionally(future1.cause());
                return;
            }

            serviceManager.newTimeout(t -> {
                subscribeFuture.completeExceptionally(new RedisTimeoutException(
                        "Subscription response timeout after " + serviceManager.getConfig().getTimeout() + "ms. " +
                                "Check network and/or increase 'timeout' parameter."));
            }, serviceManager.getConfig().getTimeout(), TimeUnit.MILLISECONDS);
        });
    }

    private SubscribeListener getSubscribeFuture(ChannelName channel, PubSubType type) {
        return subscribeChannelListeners.computeIfAbsent(channel, k -> {
            SubscribeListener listener = new SubscribeListener(channel, type);
            conn.addListener(channel, listener);
            return listener;
        });
    }

    public void unsubscribe(PubSubType commandType, ChannelName channel, RedisPubSubListener<?> listener) {
        AtomicBoolean executed = new AtomicBoolean();
        conn.addListener(channel, new BaseRedisPubSubListener() {
            @Override
            public void onStatus(PubSubType type, CharSequence ch) {
                if (type == commandType && channel.equals(ch)) {
                    executed.set(true);

                    conn.removeListener(channel, this);
                    removeListeners(channel);
                    if (listener != null) {
                        listener.onStatus(type, ch);
                    }
                }
            }
        });

        ChannelFuture future = conn.unsubscribe(commandType, channel);
        future.addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                return;
            }

            serviceManager.newTimeout(timeout -> {
                if (executed.get()) {
                    return;
                }
                conn.onMessage(new PubSubStatusMessage(commandType, channel));
            }, serviceManager.getConfig().getTimeout(), TimeUnit.MILLISECONDS);
        });
    }

    private void removeListeners(ChannelName channel) {
        conn.removeDisconnectListener(channel);

        SubscribeListener s = subscribeChannelListeners.remove(channel);
        conn.removeListener(channel, s);

        Queue<RedisPubSubListener<?>> queue = channelListeners.remove(channel);
        if (queue == null) {
            return;
        }
        for (RedisPubSubListener<?> listener : queue) {
            conn.removeListener(channel, listener);
        }
    }

    public RedisPubSubConnection getConnection() {
        return conn;
    }

    @Override
    public String toString() {
        return "PubSubConnectionEntry [subscribedChannelsAmount=" + subscribedChannelsAmount + ", conn=" + conn + "]";
    }

    public CompletableFuture<Void> addListeners(List<ChannelName> channelNames,
                                                CompletableFuture<PubSubConnectionEntry> promise,
                                                PubSubType type, AsyncSemaphore lock,
                                                RedisPubSubListener<?>... listeners) {
        List<CompletableFuture<Void>> futures = new ArrayList<>(channelNames.size());
        for (ChannelName channelName : channelNames) {
            for (RedisPubSubListener<?> listener : listeners) {
                addListener(channelName, listener);
            }

            SubscribeListener list = getSubscribeFuture(channelName, type);
            CompletableFuture<Void> subscribeFuture = list.getSuccessFuture();
            futures.add(subscribeFuture);
        }

        CompletableFuture<Void> subscribeFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        subscribeFuture.whenComplete((res, e) -> {
            if (e != null) {
                promise.completeExceptionally(e);
                lock.release();
                return;
            }

            if (!promise.complete(this)) {
                List<CompletableFuture<Void>> ffs = new ArrayList<>();
                for (ChannelName channelName : channelNames) {
                    for (RedisPubSubListener<?> listener : listeners) {
                        removeListener(channelName, listener);
                    }
                    if (!hasListeners(channelName)) {
                        CompletableFuture<Void> f = subscribeService.unsubscribeLocked(type, channelName, this);
                        ffs.add(f);
                    }
                }

                CompletableFuture<Void> ff = CompletableFuture.allOf(ffs.toArray(new CompletableFuture[0]));
                ff.thenAccept(r -> {
                    lock.release();
                });
            } else {
                lock.release();
            }
        });
        return subscribeFuture;
    }

    public CompletableFuture<Void> addListeners(ChannelName channelName,
                                                PubSubType type,
                                                RedisPubSubListener<?>... listeners) {
        for (RedisPubSubListener<?> listener : listeners) {
            addListener(channelName, listener);
        }
        SubscribeListener list = getSubscribeFuture(channelName, type);
        return list.getSuccessFuture();
    }

    public CompletableFuture<Void> release(PubSubType type, ChannelName channelName, RedisPubSubListener<?>... listeners) {
        List<CompletableFuture<Void>> ffs = new ArrayList<>();
        for (RedisPubSubListener<?> listener : listeners) {
            removeListener(channelName, listener);
        }
        if (!hasListeners(channelName)) {
            CompletableFuture<Void> f = subscribeService.unsubscribeLocked(type, channelName, this);
            ffs.add(f);
        }

        return CompletableFuture.allOf(ffs.toArray(new CompletableFuture[0]));
    }

    public CompletableFuture<Void> addListeners(ChannelName channelName,
                                                CompletableFuture<PubSubConnectionEntry> promise,
                                                PubSubType type, AsyncSemaphore lock,
                                                RedisPubSubListener<?>... listeners) {
        for (RedisPubSubListener<?> listener : listeners) {
            addListener(channelName, listener);
        }
        SubscribeListener list = getSubscribeFuture(channelName, type);
        CompletableFuture<Void> subscribeFuture = list.getSuccessFuture();

        subscribeFuture.whenComplete((res, e) -> {
            if (e != null) {
                promise.completeExceptionally(e);
                lock.release();
                return;
            }

            if (!promise.complete(this)) {
                for (RedisPubSubListener<?> listener : listeners) {
                    removeListener(channelName, listener);
                }
                if (!hasListeners(channelName)) {
                    subscribeService.unsubscribeLocked(type, channelName, this)
                            .whenComplete((r, ex) -> {
                                lock.release();
                            });
                } else {
                    lock.release();
                }
            } else {
                lock.release();
            }
        });
        return subscribeFuture;
    }

}
