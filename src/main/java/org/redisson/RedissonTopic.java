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

import io.netty.util.concurrent.Future;

import org.redisson.connection.ConnectionManager;
import org.redisson.connection.PubSubConnectionEntry;
import org.redisson.core.MessageListener;
import org.redisson.core.RTopic;

import com.lambdaworks.redis.RedisConnection;

/**
 * Distributed topic implementation. Messages are delivered to all message listeners across Redis cluster.
 *
 * @author Nikita Koksharov
 *
 * @param <M> message
 */
public class RedissonTopic<M> extends RedissonObject implements RTopic<M> {

    RedissonTopic(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public long publish(M message) {
        return publishAsync(message).awaitUninterruptibly().getNow();
    }

    @Override
    public Future<Long> publishAsync(M message) {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        return conn.getAsync().publish(getName(), message).addListener(connectionManager.createReleaseWriteListener(conn));
    }

    @Override
    public int addListener(MessageListener<M> listener) {
        RedisPubSubTopicListenerWrapper<M> pubSubListener = new RedisPubSubTopicListenerWrapper<M>(listener, getName());
        PubSubConnectionEntry entry = connectionManager.subscribe(getName());
        synchronized (entry) {
            entry.addListener(pubSubListener);
        }
        return pubSubListener.hashCode();
    }

    @Override
    public void removeListener(int listenerId) {
        PubSubConnectionEntry entry = connectionManager.getEntry(getName());
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            entry.removeListener(listenerId);
            connectionManager.unsubscribe(entry, getName());
        }
    }

    @Override
    public void delete() {
        // nothing to delete
    }

}
