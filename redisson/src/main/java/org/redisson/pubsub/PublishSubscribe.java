/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.PubSubEntry;
import org.redisson.api.RFuture;
import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.ChannelName;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.misc.TransferListener;

import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 */
abstract class PublishSubscribe<E extends PubSubEntry<E>> {

    private final ConcurrentMap<String, E> entries = PlatformDependent.newConcurrentHashMap();

    public void unsubscribe(final E entry, final String entryName, final String channelName, final PublishSubscribeService subscribeService) {
        final AsyncSemaphore semaphore = subscribeService.getSemaphore(new ChannelName(channelName));
        semaphore.acquire(new Runnable() {
            @Override
            public void run() {
                if (entry.release() == 0) {
                    // just an assertion
                    boolean removed = entries.remove(entryName) == entry;
                    if (!removed) {
                        throw new IllegalStateException();
                    }
                    subscribeService.unsubscribe(new ChannelName(channelName), semaphore);
                } else {
                    semaphore.release();
                }
            }
        });

    }

    public E getEntry(String entryName) {
        return entries.get(entryName);
    }

    public RFuture<E> subscribe(final String entryName, final String channelName, final PublishSubscribeService subscribeService) {
        final AtomicReference<Runnable> listenerHolder = new AtomicReference<Runnable>();
        final AsyncSemaphore semaphore = subscribeService.getSemaphore(new ChannelName(channelName));
        final RPromise<E> newPromise = new RedissonPromise<E>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return semaphore.remove(listenerHolder.get());
            }
        };

        Runnable listener = new Runnable() {

            @Override
            public void run() {
                E entry = entries.get(entryName);
                if (entry != null) {
                    entry.aquire();
                    semaphore.release();
                    entry.getPromise().addListener(new TransferListener<E>(newPromise));
                    return;
                }
                
                E value = createEntry(newPromise);
                value.aquire();
                
                E oldValue = entries.putIfAbsent(entryName, value);
                if (oldValue != null) {
                    oldValue.aquire();
                    semaphore.release();
                    oldValue.getPromise().addListener(new TransferListener<E>(newPromise));
                    return;
                }
                
                RedisPubSubListener<Object> listener = createListener(channelName, value);
                subscribeService.subscribe(LongCodec.INSTANCE, channelName, semaphore, listener);
            }
        };
        semaphore.acquire(listener);
        listenerHolder.set(listener);
        
        return newPromise;
    }

    protected abstract E createEntry(RPromise<E> newPromise);

    protected abstract void onMessage(E value, Long message);

    private RedisPubSubListener<Object> createListener(final String channelName, final E value) {
        RedisPubSubListener<Object> listener = new BaseRedisPubSubListener() {

            @Override
            public void onMessage(CharSequence channel, Object message) {
                if (!channelName.equals(channel.toString())) {
                    return;
                }

                PublishSubscribe.this.onMessage(value, (Long)message);
            }

            @Override
            public boolean onStatus(PubSubType type, CharSequence channel) {
                if (!channelName.equals(channel.toString())) {
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
