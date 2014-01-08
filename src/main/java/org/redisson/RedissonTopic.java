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
package org.redisson;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.connection.ConnectionManager;
import org.redisson.connection.ConnectionManager.PubSubEntry;
import org.redisson.core.MessageListener;
import org.redisson.core.RTopic;
import org.redisson.core.RedisPubSubTopicListener;

import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;

public class RedissonTopic<M> implements RTopic<M> {

    private final CountDownLatch subscribeLatch = new CountDownLatch(1);
    private final AtomicBoolean subscribeOnce = new AtomicBoolean();

    private final Map<Integer, RedisPubSubTopicListener<String, M>> listeners =
                                new ConcurrentHashMap<Integer, RedisPubSubTopicListener<String, M>>();
    private final ConnectionManager connectionManager;
    private final String name;

    private PubSubEntry pubSubEntry;

    RedissonTopic(ConnectionManager connectionManager, String name) {
        this.connectionManager = connectionManager;
        this.name = name;
    }

    public void subscribe() {
        if (subscribeOnce.compareAndSet(false, true)) {
            RedisPubSubAdapter<String, M> listener = new RedisPubSubAdapter<String, M>() {

                @Override
                public void subscribed(String channel, long count) {
                    if (channel.equals(name)) {
                        subscribeLatch.countDown();
                    }
                }

            };

            pubSubEntry = connectionManager.subscribe(listener, name);
        }

        try {
            subscribeLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void publish(M message) {
        RedisConnection<String, Object> conn = connectionManager.acquireConnection();
        try {
            conn.publish(name, message);
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public int addListener(MessageListener<M> listener) {
        RedisPubSubTopicListener<String, M> pubSubListener = new RedisPubSubTopicListener<String, M>(listener, name);
        listeners.put(pubSubListener.hashCode(), pubSubListener);
        pubSubEntry.addListener(pubSubListener);
        return pubSubListener.hashCode();
    }

    @Override
    public void removeListener(int listenerId) {
        RedisPubSubTopicListener<String, M> pubSubListener = listeners.remove(listenerId);
        pubSubEntry.removeListener(pubSubListener);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void destroy() {
        connectionManager.unsubscribe(pubSubEntry, name);
//        redisson.remove(this);
    }


}
