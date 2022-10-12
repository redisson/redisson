/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.spring.data.connection;

import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.ChannelName;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.pubsub.PubSubConnectionEntry;
import org.redisson.pubsub.PublishSubscribeService;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.data.redis.connection.util.AbstractSubscription;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonSubscription extends AbstractSubscription {

    private final CommandAsyncExecutor commandExecutor;
    private final PublishSubscribeService subscribeService;

    public RedissonSubscription(CommandAsyncExecutor commandExecutor, PublishSubscribeService subscribeService, MessageListener listener) {
        super(listener, null, null);
        this.commandExecutor = commandExecutor;
        this.subscribeService = subscribeService;
    }

    @Override
    protected void doSubscribe(byte[]... channels) {
        List<CompletableFuture<?>> list = new ArrayList<>();
        Queue<byte[]> subscribed = new ConcurrentLinkedQueue<>();
        for (byte[] channel : channels) {
            CompletableFuture<PubSubConnectionEntry> f = subscribeService.subscribe(ByteArrayCodec.INSTANCE, new ChannelName(channel), new BaseRedisPubSubListener() {
                @Override
                public void onMessage(CharSequence ch, Object message) {
                    if (!Arrays.equals(((ChannelName) ch).getName(), channel)) {
                        return;
                    }

                    byte[] m = toBytes(message);
                    DefaultMessage msg = new DefaultMessage(((ChannelName) ch).getName(), m);
                    getListener().onMessage(msg, null);
                }

                @Override
                public boolean onStatus(PubSubType type, CharSequence ch) {
                    if (!Arrays.equals(((ChannelName) ch).getName(), channel)) {
                        return false;
                    }

                    if (getListener() instanceof SubscriptionListener) {
                        subscribed.add(channel);
                    }
                    return super.onStatus(type, ch);
                }

            });
            list.add(f);
        }
        for (CompletableFuture<?> future : list) {
            commandExecutor.get(future);
        }
        for (byte[] channel : subscribed) {
            ((SubscriptionListener) getListener()).onChannelSubscribed(channel, 1);
        }
    }

    @Override
    protected void doUnsubscribe(boolean all, byte[]... channels) {
        for (byte[] channel : channels) {
            CompletableFuture<Codec> f = subscribeService.unsubscribe(new ChannelName(channel), PubSubType.UNSUBSCRIBE);
            if (getListener() instanceof SubscriptionListener) {
                f.whenComplete((r, e) -> {
                    if (r != null) {
                        ((SubscriptionListener) getListener()).onChannelUnsubscribed(channel, 1);
                    }
                });
            }
        }
    }

    @Override
    protected void doPsubscribe(byte[]... patterns) {
        List<CompletableFuture<?>> list = new ArrayList<>();
        Queue<byte[]> subscribed = new ConcurrentLinkedQueue<>();
        for (byte[] channel : patterns) {
            CompletableFuture<Collection<PubSubConnectionEntry>> f = subscribeService.psubscribe(new ChannelName(channel), ByteArrayCodec.INSTANCE, new BaseRedisPubSubListener() {
                @Override
                public void onPatternMessage(CharSequence pattern, CharSequence ch, Object message) {
                    if (!Arrays.equals(((ChannelName) pattern).getName(), channel)) {
                        return;
                    }

                    byte[] m = toBytes(message);
                    DefaultMessage msg = new DefaultMessage(((ChannelName)ch).getName(), m);
                    getListener().onMessage(msg, ((ChannelName)pattern).getName());
                }

                @Override
                public boolean onStatus(PubSubType type, CharSequence pattern) {
                    if (!Arrays.equals(((ChannelName) pattern).getName(), channel)) {
                        return false;
                    }

                    if (getListener() instanceof SubscriptionListener) {
                        subscribed.add(channel);
                    }
                    return super.onStatus(type, pattern);
                }
            });
            list.add(f);
        }
        for (CompletableFuture<?> future : list) {
            commandExecutor.get(future);
        }
        for (byte[] channel : subscribed) {
            ((SubscriptionListener) getListener()).onPatternSubscribed(channel, 1);
        }
    }

    private byte[] toBytes(Object message) {
        if (message instanceof String) {
            return  ((String) message).getBytes();
        }
        return (byte[]) message;
    }

    @Override
    protected void doPUnsubscribe(boolean all, byte[]... patterns) {
        for (byte[] pattern : patterns) {
            CompletableFuture<Codec> f = subscribeService.unsubscribe(new ChannelName(pattern), PubSubType.PUNSUBSCRIBE);
            if (getListener() instanceof SubscriptionListener) {
                f.whenComplete((r, e) -> {
                    if (r != null) {
                        ((SubscriptionListener) getListener()).onPatternUnsubscribed(pattern, 1);
                    }
                });
            }
        }
    }

    @Override
    protected void doClose() {
        doUnsubscribe(false, getChannels().toArray(new byte[getChannels().size()][]));
        doPUnsubscribe(false, getPatterns().toArray(new byte[getPatterns().size()][]));
    }

}
