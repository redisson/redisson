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

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.protocol.Codec;
import org.redisson.client.protocol.pubsub.PubSubStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PubSubConnectionEntry {

    public enum Status {ACTIVE, INACTIVE}

    private final Logger log = LoggerFactory.getLogger(getClass());

    private volatile Status status = Status.ACTIVE;
    private final Semaphore subscribedChannelsAmount;
    private final RedisPubSubConnection conn;
    private final int subscriptionsPerConnection;
    private final ConcurrentMap<String, Queue<RedisPubSubListener>> channelListeners = new ConcurrentHashMap<String, Queue<RedisPubSubListener>>();

    public PubSubConnectionEntry(RedisPubSubConnection conn, int subscriptionsPerConnection) {
        super();
        this.conn = conn;
        this.subscriptionsPerConnection = subscriptionsPerConnection;
        this.subscribedChannelsAmount = new Semaphore(subscriptionsPerConnection);
    }

    public boolean hasListeners(String channelName) {
        return channelListeners.containsKey(channelName);
    }

    public Collection<RedisPubSubListener> getListeners(String channelName) {
        Collection<RedisPubSubListener> result = channelListeners.get(channelName);
        if (result == null) {
            return Collections.emptyList();
        }
        return new ArrayList<RedisPubSubListener>(result);
    }

    public void addListener(String channelName, RedisPubSubListener<?> listener) {
        Queue<RedisPubSubListener> queue = channelListeners.get(channelName);
        if (queue == null) {
            queue = new ConcurrentLinkedQueue<RedisPubSubListener>();
            Queue<RedisPubSubListener> oldQueue = channelListeners.putIfAbsent(channelName, queue);
            if (oldQueue != null) {
                queue = oldQueue;
            }
        }

        synchronized (queue) {
            if (channelListeners.get(channelName) != queue) {
                addListener(channelName, listener);
                return;
            }
            queue.add(listener);
        }

        conn.addListener(listener);
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public void close() {
        status = Status.INACTIVE;
    }

    // TODO optimize
    public void removeListener(String channelName, int listenerId) {
        Queue<RedisPubSubListener> listeners = channelListeners.get(channelName);
        for (RedisPubSubListener listener : listeners) {
            if (listener.hashCode() == listenerId) {
                removeListener(channelName, listener);
                break;
            }
        }
    }

    public void removeListener(String channelName, RedisPubSubListener listener) {
        Queue<RedisPubSubListener> queue = channelListeners.get(channelName);
        synchronized (queue) {
            if (queue.remove(listener)) {
                channelListeners.remove(channelName, new ConcurrentLinkedQueue<RedisPubSubListener>());
            }
        }
        conn.removeListener(listener);
    }

    public boolean tryAcquire() {
        return subscribedChannelsAmount.tryAcquire();
    }

    public void release() {
        subscribedChannelsAmount.release();
    }

    public void subscribe(Codec codec, final String channelName) {
        Future<PubSubStatusMessage> result = conn.subscribe(codec, channelName);
        result.addListener(new FutureListener<PubSubStatusMessage>() {
            @Override
            public void operationComplete(Future<PubSubStatusMessage> future) throws Exception {
                log.debug("subscribed to '{}' channel on server '{}'", channelName, conn.getRedisClient().getAddr());
            }
        });
    }

    public void psubscribe(Codec codec, final String pattern) {
        Future<PubSubStatusMessage> result = conn.psubscribe(codec, pattern);
        result.addListener(new FutureListener<PubSubStatusMessage>() {
            @Override
            public void operationComplete(Future<PubSubStatusMessage> future) throws Exception {
                log.debug("punsubscribed from '{}' pattern on server '{}'", pattern, conn.getRedisClient().getAddr());
            }
        });
    }

    public Future<PubSubStatusMessage> subscribe(Codec codec, RedisPubSubListener listener, String channel) {
        addListener(channel, listener);
        return conn.subscribe(codec, channel);
    }

    public Future<PubSubStatusMessage> unsubscribe(final String channel) {
        Queue<RedisPubSubListener> listeners = channelListeners.get(channel);
        if (listeners != null) {
            for (RedisPubSubListener listener : listeners) {
                removeListener(channel, listener);
            }
        }

        Future<PubSubStatusMessage> future = conn.unsubscribe(channel);
        future.addListener(new FutureListener<PubSubStatusMessage>() {
            @Override
            public void operationComplete(Future<PubSubStatusMessage> future) throws Exception {
                subscribedChannelsAmount.release();
            }
        });
        return future;
    }

    public Future<PubSubStatusMessage> punsubscribe(final String channel) {
        Queue<RedisPubSubListener> listeners = channelListeners.get(channel);
        if (listeners != null) {
            for (RedisPubSubListener listener : listeners) {
                removeListener(channel, listener);
            }
        }

        Future<PubSubStatusMessage> future = conn.punsubscribe(channel);
        future.addListener(new FutureListener<PubSubStatusMessage>() {
            @Override
            public void operationComplete(Future<PubSubStatusMessage> future) throws Exception {
                subscribedChannelsAmount.release();
            }
        });
        return future;
    }


    public boolean tryClose() {
        if (subscribedChannelsAmount.tryAcquire(subscriptionsPerConnection)) {
            close();
            return true;
        }
        return false;
    }

    public RedisPubSubConnection getConnection() {
        return conn;
    }

}
