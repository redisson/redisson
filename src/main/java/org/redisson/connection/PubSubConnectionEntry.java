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

import java.util.Collection;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.SubscribeListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.pubsub.PubSubType;

import io.netty.util.concurrent.Future;

public class PubSubConnectionEntry {

    public enum Status {ACTIVE, INACTIVE}

    private final AtomicInteger subscribedChannelsAmount;
    private final RedisPubSubConnection conn;

    private final ConcurrentMap<String, SubscribeListener> subscribeChannelListeners = new ConcurrentHashMap<String, SubscribeListener>();
    private final ConcurrentMap<String, Queue<RedisPubSubListener>> channelListeners = new ConcurrentHashMap<String, Queue<RedisPubSubListener>>();

    public PubSubConnectionEntry(RedisPubSubConnection conn, int subscriptionsPerConnection) {
        super();
        this.conn = conn;
        this.subscribedChannelsAmount = new AtomicInteger(subscriptionsPerConnection);
    }

    public boolean hasListeners(String channelName) {
        return channelListeners.containsKey(channelName);
    }

    public Collection<RedisPubSubListener> getListeners(String channelName) {
        Collection<RedisPubSubListener> result = channelListeners.get(channelName);
        if (result == null) {
            return Collections.emptyList();
        }
        return result;
    }

    public void addListener(String channelName, RedisPubSubListener<?> listener) {
        if (listener == null) {
            return;
        }
        
        Queue<RedisPubSubListener> queue = channelListeners.get(channelName);
        if (queue == null) {
            queue = new ConcurrentLinkedQueue<RedisPubSubListener>();
            Queue<RedisPubSubListener> oldQueue = channelListeners.putIfAbsent(channelName, queue);
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
    public boolean removeListener(String channelName, int listenerId) {
        Queue<RedisPubSubListener> listeners = channelListeners.get(channelName);
        for (RedisPubSubListener listener : listeners) {
            if (System.identityHashCode(listener) == listenerId) {
                removeListener(channelName, listener);
                return true;
            }
        }
        return false;
    }

    private void removeListener(String channelName, RedisPubSubListener listener) {
        Queue<RedisPubSubListener> queue = channelListeners.get(channelName);
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

    public void subscribe(Codec codec, String channelName) {
        conn.subscribe(codec, channelName);
    }

    public void psubscribe(Codec codec, String pattern) {
        conn.psubscribe(codec, pattern);
    }

    private SubscribeListener addSubscribeListener(String channel, PubSubType type) {
        SubscribeListener subscribeListener = new SubscribeListener(channel, type);
        SubscribeListener oldSubscribeListener = subscribeChannelListeners.putIfAbsent(channel, subscribeListener);
        if (oldSubscribeListener != null) {
            return oldSubscribeListener;
        } else {
            conn.addListener(subscribeListener);
            return subscribeListener;
        }
    }

    public Future<Void> getSubscribeFuture(String channel, PubSubType type) {
        SubscribeListener listener = subscribeChannelListeners.get(channel);
        if (listener == null) {
            listener = addSubscribeListener(channel, type);
        }
        return listener.getSuccessFuture();
    }
    
    public void unsubscribe(final String channel, final RedisPubSubListener listener) {
        conn.addListener(new BaseRedisPubSubListener() {
            @Override
            public boolean onStatus(PubSubType type, String ch) {
                if (type == PubSubType.UNSUBSCRIBE && channel.equals(ch)) {
                    conn.removeListener(this);
                    removeListeners(channel);
                    if (listener != null) {
                        listener.onStatus(type, channel);
                    }
                    return true;
                }
                return false;
            }

        });
        conn.unsubscribe(channel);
    }

    private void removeListeners(String channel) {
        conn.removeDisconnectListener(channel);
        SubscribeListener s = subscribeChannelListeners.remove(channel);
        conn.removeListener(s);
        Queue<RedisPubSubListener> queue = channelListeners.get(channel);
        if (queue != null) {
            synchronized (queue) {
                channelListeners.remove(channel);
            }
            for (RedisPubSubListener listener : queue) {
                conn.removeListener(listener);
            }
        }
    }

    public void punsubscribe(final String channel, final RedisPubSubListener listener) {
        conn.addListener(new BaseRedisPubSubListener() {
            @Override
            public boolean onStatus(PubSubType type, String ch) {
                if (type == PubSubType.PUNSUBSCRIBE && channel.equals(ch)) {
                    conn.removeListener(this);
                    removeListeners(channel);
                    if (listener != null) {
                        listener.onStatus(type, channel);
                    }
                    return true;
                }
                return false;
            }
        });
        conn.punsubscribe(channel);
    }

    public RedisPubSubConnection getConnection() {
        return conn;
    }

}
