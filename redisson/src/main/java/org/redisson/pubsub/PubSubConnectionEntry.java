/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
import org.redisson.connection.ServiceManager;

import java.util.EventListener;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;
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

    private final ConcurrentMap<ChannelName, SubscribeListener> subscribeChannelListeners = new ConcurrentHashMap<ChannelName, SubscribeListener>();
    private final ConcurrentMap<ChannelName, Queue<RedisPubSubListener<?>>> channelListeners = new ConcurrentHashMap<ChannelName, Queue<RedisPubSubListener<?>>>();

    private static final Queue<RedisPubSubListener<?>> EMPTY_QUEUE = new LinkedList<>();

    private final ServiceManager serviceManager;

    public PubSubConnectionEntry(RedisPubSubConnection conn, ServiceManager serviceManager) {
        super();
        this.conn = conn;
        this.serviceManager = serviceManager;
        this.subscribedChannelsAmount = new AtomicInteger(serviceManager.getConfig().getSubscriptionsPerConnection());
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
        
        Queue<RedisPubSubListener<?>> queue = channelListeners.get(channelName);
        if (queue == null) {
            queue = new ConcurrentLinkedQueue<RedisPubSubListener<?>>();
            Queue<RedisPubSubListener<?>> oldQueue = channelListeners.putIfAbsent(channelName, queue);
            if (oldQueue != null) {
                queue = oldQueue;
            }
        }

        boolean deleted = false;
        synchronized (queue) {
            if (channelListeners.get(channelName) != queue) {
                deleted = true;
            } else {
                queue.add(listener);
            }
        }
        if (deleted) {
            addListener(channelName, listener);
            return;
        }

        conn.addListener(listener);
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
        Queue<RedisPubSubListener<?>> listeners = channelListeners.get(channelName);
        for (RedisPubSubListener<?> listener : listeners) {
            if (System.identityHashCode(listener) == listenerId) {
                removeListener(channelName, listener);
                return true;
            }
        }
        return false;
    }

    public void removeListener(ChannelName channelName, RedisPubSubListener<?> listener) {
        Queue<RedisPubSubListener<?>> queue = channelListeners.get(channelName);
        synchronized (queue) {
            if (queue.remove(listener) && queue.isEmpty()) {
                channelListeners.remove(channelName);
            }
        }
        conn.removeListener(listener);
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

    public void subscribe(Codec codec, PubSubType type, ChannelName channelName, CompletableFuture<Void> subscribeFuture) {
        ChannelFuture future;
        CompletableFuture<Void> promise = new CompletableFuture<>();
        if (PubSubType.SUBSCRIBE == type) {
            future = conn.subscribe(promise, codec, channelName);
        } else if (PubSubType.SSUBSCRIBE == type) {
            future = conn.ssubscribe(promise, codec, channelName);
        } else {
            future = conn.psubscribe(promise, codec, channelName);
        }

        promise.whenComplete((r, ex) -> {
            if (ex != null) {
                subscribeFuture.completeExceptionally(ex);
            }
        });

        future.addListener((ChannelFutureListener) future1 -> {
            if (!future1.isSuccess()) {
                subscribeFuture.completeExceptionally(future1.cause());
                return;
            }

            serviceManager.newTimeout(t -> {
                subscribeFuture.completeExceptionally(new RedisTimeoutException(
                        "Subscription timeout after " + serviceManager.getConfig().getTimeout() + "ms. " +
                                "Check network and/or increase 'timeout' parameter."));
            }, serviceManager.getConfig().getTimeout(), TimeUnit.MILLISECONDS);
        });
    }

    public SubscribeListener getSubscribeFuture(ChannelName channel, PubSubType type) {
        SubscribeListener listener = subscribeChannelListeners.get(channel);
        if (listener == null) {
            listener = new SubscribeListener(channel, type);
            SubscribeListener oldSubscribeListener = subscribeChannelListeners.putIfAbsent(channel, listener);
            if (oldSubscribeListener != null) {
                listener = oldSubscribeListener;
            } else {
                conn.addListener(listener);
            }
        }
        return listener;
    }
    
    public void unsubscribe(PubSubType commandType, ChannelName channel, RedisPubSubListener<?> listener) {
        AtomicBoolean executed = new AtomicBoolean();
        conn.addListener(new BaseRedisPubSubListener() {
            @Override
            public boolean onStatus(PubSubType type, CharSequence ch) {
                if (type == commandType && channel.equals(ch)) {
                    executed.set(true);

                    conn.removeListener(this);
                    removeListeners(channel);
                    if (listener != null) {
                        listener.onStatus(type, ch);
                    }
                    return true;
                }
                return false;
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
        conn.removeListener(s);
        Queue<RedisPubSubListener<?>> queue = channelListeners.get(channel);
        if (queue != null) {
            synchronized (queue) {
                channelListeners.remove(channel);
            }
            for (RedisPubSubListener<?> listener : queue) {
                conn.removeListener(listener);
            }
        }
    }

    public RedisPubSubConnection getConnection() {
        return conn;
    }

    @Override
    public String toString() {
        return "PubSubConnectionEntry{" +
                "subscribedChannelsAmount=" + subscribedChannelsAmount +
                ", conn=" + conn +
                ", subscribeChannelListeners=" + subscribeChannelListeners +
                ", channelListeners=" + channelListeners +
                '}';
    }
}
