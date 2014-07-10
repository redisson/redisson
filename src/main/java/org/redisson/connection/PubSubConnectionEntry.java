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

import java.util.Collection;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubListener;

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

    public Collection<RedisPubSubListener> getListeners(String channelName) {
        Collection<RedisPubSubListener> result = channelListeners.get(channelName);
        if (result == null) {
            return Collections.emptyList();
        }
        return result;
    }
    
    public void addListener(String channelName, RedisPubSubListener listener) {
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

    public void subscribe(final String channelName) {
        conn.addListener(new RedisPubSubAdapter() {
            @Override
            public void subscribed(String channel, long count) {
                log.debug("subscribed to '{}' channel", channelName);
            }

            @Override
            public void unsubscribed(String channel, long count) {
                log.debug("unsubscribed from '{}' channel", channelName);
            }
        });
        conn.subscribe(channelName);
    }


    public void subscribe(RedisPubSubAdapter listener, String channel) {
        addListener(channel, listener);
        conn.subscribe(channel);
    }

    public Future unsubscribe(final String channel) {
        Queue<RedisPubSubListener> listeners = channelListeners.get(channel);
        if (listeners != null) {
            for (RedisPubSubListener listener : listeners) {
                removeListener(channel, listener);
            }
        }

        Future future = conn.unsubscribe(channel);
        future.addListener(new FutureListener() {
            @Override
            public void operationComplete(Future future) throws Exception {
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
