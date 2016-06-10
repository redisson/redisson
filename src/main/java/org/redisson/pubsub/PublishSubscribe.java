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
package org.redisson.pubsub;

import java.util.concurrent.ConcurrentMap;

import org.redisson.PubSubEntry;
import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.connection.ConnectionManager;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;

abstract class PublishSubscribe<E extends PubSubEntry<E>> {

    private final ConcurrentMap<String, E> entries = PlatformDependent.newConcurrentHashMap();

    public void unsubscribe(E entry, String entryName, String channelName, ConnectionManager connectionManager) {
        synchronized (this) {
            if (entry.release() == 0) {
                // just an assertion
                boolean removed = entries.remove(entryName) == entry;
                if (removed) {
                    connectionManager.unsubscribe(channelName).syncUninterruptibly();
                }
            }
        }
    }

    public E getEntry(String entryName) {
        return entries.get(entryName);
    }

    public Future<E> subscribe(String entryName, String channelName, ConnectionManager connectionManager) {
        synchronized (this) {
            E entry = entries.get(entryName);
            if (entry != null) {
                entry.aquire();
                return entry.getPromise();
            }

            Promise<E> newPromise = connectionManager.newPromise();
            E value = createEntry(newPromise);
            value.aquire();

            E oldValue = entries.putIfAbsent(entryName, value);
            if (oldValue != null) {
                oldValue.aquire();
                return oldValue.getPromise();
            }

            RedisPubSubListener<Long> listener = createListener(channelName, value);
            connectionManager.subscribe(LongCodec.INSTANCE, channelName, listener);
            return newPromise;
        }
    }

    protected abstract E createEntry(Promise<E> newPromise);

    protected abstract void onMessage(E value, Long message);

    private RedisPubSubListener<Long> createListener(final String channelName, final E value) {
        RedisPubSubListener<Long> listener = new BaseRedisPubSubListener<Long>() {

            @Override
            public void onMessage(String channel, Long message) {
                if (!channelName.equals(channel)) {
                    return;
                }

                PublishSubscribe.this.onMessage(value, message);
            }

            @Override
            public boolean onStatus(PubSubType type, String channel) {
                if (!channelName.equals(channel)) {
                    return false;
                }

                if (type == PubSubType.SUBSCRIBE) {
                    value.getPromise().trySuccess(value);
                    return true;
                }
                return false;
            }

        };
        return listener;
    }

}
