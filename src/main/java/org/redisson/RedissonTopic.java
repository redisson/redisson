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

import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.connection.ConnectionManager;
import org.redisson.connection.PubSubConnectionEntry;
import org.redisson.core.MessageListener;
import org.redisson.core.RTopic;

import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubListener;

/**
 * Distributed topic implementation. Messages are delivered to all message listeners across Redis cluster.
 *
 * @author Nikita Koksharov
 *
 * @param <M> message
 */
public class RedissonTopic<M> extends RedissonObject implements RTopic<M> {

    private final AtomicReference<Promise<Boolean>> promise = new AtomicReference<Promise<Boolean>>();

    private final Map<Integer, RedisPubSubTopicListenerWrapper<String, M>> listeners =
                                new ConcurrentHashMap<Integer, RedisPubSubTopicListenerWrapper<String, M>>();
    private final ConnectionManager connectionManager;

    private PubSubConnectionEntry pubSubEntry;

    RedissonTopic(ConnectionManager connectionManager, String name) {
        super(name);
        this.connectionManager = connectionManager;
    }

    private void lazySubscribe() {
        if (promise.get() != null) {
            return;
        }

        final Promise<Boolean> newPromise = connectionManager.getGroup().next().<Boolean>newPromise();
        if (!promise.compareAndSet(null, newPromise)) {
            return;
        }

        RedisPubSubAdapter<String, M> listener = new RedisPubSubAdapter<String, M>() {
            @Override
            public void subscribed(String channel, long count) {
                if (channel.equals(getName())) {
                    newPromise.setSuccess(true);
                }
            }
        };

        pubSubEntry = connectionManager.subscribe(listener, getName());
    }

    @Override
    public long publish(M message) {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        try {
            return conn.publish(getName(), message);
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public Future<Long> publishAsync(M message) {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        try {
            return conn.getAsync().publish(getName(), message);
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public int addListener(final MessageListener<M> listener) {
        final RedisPubSubTopicListenerWrapper<String, M> pubSubListener = new RedisPubSubTopicListenerWrapper<String, M>(listener, getName());
        listeners.put(pubSubListener.hashCode(), pubSubListener);

        return addListener(pubSubListener);
    }

    private int addListener(final RedisPubSubListener<String, M> pubSubListener) {
        lazySubscribe();
        promise.get().addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<Boolean> future) throws Exception {
                pubSubEntry.addListener(pubSubListener);
            }
        });
        return pubSubListener.hashCode();
    }

    @Override
    public void removeListener(int listenerId) {
        final RedisPubSubTopicListenerWrapper<String, M> pubSubListener = listeners.remove(listenerId);
        promise.get().addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<Boolean> future) throws Exception {
                pubSubEntry.removeListener(pubSubListener);
            }
        });
        lazyUnsubscribe();
    }

    private void lazyUnsubscribe() {
        Promise<Boolean> oldPromise = promise.get();
        final PubSubConnectionEntry oldPubSubEntry = pubSubEntry;
        if (oldPromise == null || !promise.compareAndSet(oldPromise, null)) {
            return;
        }

        oldPromise.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<Boolean> future) throws Exception {
                connectionManager.unsubscribe(oldPubSubEntry, getName());

                // reattach eventually added listeners
                for (RedisPubSubListener listener : oldPubSubEntry.getListeners()) {
                    addListener(listener);
                }
            }
        });
    }

}
