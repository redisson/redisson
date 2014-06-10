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

import java.util.Queue;
import java.util.concurrent.Semaphore;

import org.redisson.RedisPubSubTopicListenerWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubListener;

public class PubSubConnectionEntry {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Semaphore subscribedChannelsAmount;
    private final RedisPubSubConnection conn;
    private final int subscriptionsPerConnection;

    public PubSubConnectionEntry(RedisPubSubConnection conn, int subscriptionsPerConnection) {
        super();
        this.conn = conn;
        this.subscriptionsPerConnection = subscriptionsPerConnection;
        this.subscribedChannelsAmount = new Semaphore(subscriptionsPerConnection);
    }

    public void addListener(RedisPubSubListener listener) {
        conn.addListener(listener);
    }

    // TODO optimize
    public boolean hasListeners(String channelName) {
        Queue<RedisPubSubListener> queue = conn.getListeners();
        for (RedisPubSubListener listener : queue) {
            if (!(listener instanceof RedisPubSubTopicListenerWrapper)) {
                continue;
            }

            RedisPubSubTopicListenerWrapper entry = (RedisPubSubTopicListenerWrapper) listener;
            if (entry.getName().equals(channelName)) {
                return true;
            }
        }
        return false;
    }

    // TODO optimize
    public void removeListener(int listenerId) {
        Queue<RedisPubSubListener> queue = conn.getListeners();
        for (RedisPubSubListener listener : queue) {
            if (!(listener instanceof RedisPubSubTopicListenerWrapper)) {
                continue;
            }

            RedisPubSubTopicListenerWrapper entry = (RedisPubSubTopicListenerWrapper) listener;
            if (entry.hashCode() == listenerId) {
                removeListener(entry);
                break;
            }
        }
    }

    public void removeListener(RedisPubSubListener listener) {
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
            public void subscribed(Object channel, long count) {
                log.debug("subscribed to '{}' channel", channelName);
            }

            @Override
            public void unsubscribed(Object channel, long count) {
                log.debug("unsubscribed from '{}' channel", channelName);
            }
        });
        conn.subscribe(channelName);
    }


    public void subscribe(RedisPubSubAdapter listener, Object channel) {
        conn.addListener(listener);
        conn.subscribe(channel);
    }

    public void unsubscribe(String channel) {
        conn.unsubscribe(channel);
        subscribedChannelsAmount.release();
    }

    public boolean tryClose() {
        if (subscribedChannelsAmount.tryAcquire(subscriptionsPerConnection)) {
            conn.close();
            return true;
        }
        return false;
    }

    public RedisPubSubConnection getConnection() {
        return conn;
    }

}
