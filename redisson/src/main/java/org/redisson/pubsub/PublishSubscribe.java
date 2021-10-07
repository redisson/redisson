/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 
 * @author Nikita Koksharov
 *
 */
abstract class PublishSubscribe<E extends PubSubEntry<E>> {

    private final ConcurrentMap<String, E> entries = new ConcurrentHashMap<>();
    private final PublishSubscribeService service;

    PublishSubscribe(PublishSubscribeService service) {
        super();
        this.service = service;
    }

    public void unsubscribe(E entry, String entryName, String channelName) {
        AsyncSemaphore semaphore = service.getSemaphore(new ChannelName(channelName));
        semaphore.acquire(() -> {
            if (entry.release() == 0) {
                // just an assertion
                boolean removed = entries.remove(entryName) == entry;
                if (!removed) {
                    throw new IllegalStateException();
                }
                service.unsubscribe(PubSubType.UNSUBSCRIBE, new ChannelName(channelName))
                        .onComplete((r, e) -> {
                            semaphore.release();
                        });
            } else {
                semaphore.release();
            }
        });

    }

    public RFuture<E> subscribe(String entryName, String channelName) {
        AsyncSemaphore semaphore = service.getSemaphore(new ChannelName(channelName));
        RPromise<E> newPromise = new RedissonPromise<>();
        semaphore.acquire(() -> {
            if (!newPromise.setUncancellable()) {
                semaphore.release();
                return;
            }

            E entry = entries.get(entryName);
            if (entry != null) {
                entry.acquire();
                semaphore.release();
                entry.getPromise().onComplete(new TransferListener<E>(newPromise));
                return;
            }

            E value = createEntry(newPromise);
            value.acquire();

            E oldValue = entries.putIfAbsent(entryName, value);
            if (oldValue != null) {
                oldValue.acquire();
                semaphore.release();
                oldValue.getPromise().onComplete(new TransferListener<E>(newPromise));
                return;
            }

            RedisPubSubListener<Object> listener = createListener(channelName, value);
            service.subscribe(LongCodec.INSTANCE, channelName, semaphore, listener);
        });

        return newPromise;
    }

    protected abstract E createEntry(RPromise<E> newPromise);

    protected abstract void onMessage(E value, Long message);

    private RedisPubSubListener<Object> createListener(String channelName, E value) {
        RedisPubSubListener<Object> listener = new BaseRedisPubSubListener() {

            @Override
            public void onMessage(CharSequence channel, Object message) {
                if (!channelName.equals(channel.toString())) {
                    return;
                }

                PublishSubscribe.this.onMessage(value, (Long) message);
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
