/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonSubscription extends AbstractSubscription {

    private static final CompletableFuture<Void> COMPLETED = new CompletableFuture<>();

    private final Map<ChannelName, CompletableFuture<Void>> subscribed = new ConcurrentHashMap<>();
    private final Map<ChannelName, CompletableFuture<Void>> psubscribed = new ConcurrentHashMap<>();

    private final CommandAsyncExecutor commandExecutor;
    private final PublishSubscribeService subscribeService;
    
    public RedissonSubscription(CommandAsyncExecutor commandExecutor, MessageListener listener) {
        super(listener, null, null);
        this.commandExecutor = commandExecutor;
        this.subscribeService = commandExecutor.getConnectionManager().getSubscribeService();
    }

    @Override
    protected void doSubscribe(byte[]... channels) {
        Map<ChannelName, CompletableFuture<Void>> tosubscribe = getNonSubscribed(channels, subscribed, (l, ch) -> {
            ((SubscriptionListener) getListener()).onChannelSubscribed(ch, 1);
        });
        if (tosubscribe.isEmpty()) {
            return;
        }

        List<CompletableFuture<?>> list = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        for (ChannelName channel : tosubscribe.keySet()) {
            CompletableFuture<List<PubSubConnectionEntry>> f = subscribeService.subscribe(ByteArrayCodec.INSTANCE, channel, new BaseRedisPubSubListener() {
                @Override
                public void onMessage(CharSequence ch, Object message) {
                    if (!Arrays.equals(((ChannelName) ch).getName(), channel.getName())) {
                        return;
                    }

                    byte[] m = toBytes(message);
                    DefaultMessage msg = new DefaultMessage(((ChannelName) ch).getName(), m);
                    getListener().onMessage(msg, null);
                }

                @Override
                public void onStatus(PubSubType type, CharSequence ch) {
                    if (!Arrays.equals(((ChannelName) ch).getName(), channel.getName())) {
                        return;
                    }

                    if (getListener() instanceof SubscriptionListener
                            && type == PubSubType.SUBSCRIBE) {
                        CompletableFuture<Void> callback = subscribed.getOrDefault(channel, COMPLETED);
                        callback.complete(null);
                    }
                    super.onStatus(type, ch);

                    if (type == PubSubType.UNSUBSCRIBE) {
                        subscribed.remove(channel);
                        latch.countDown();
                    }
                }

            });
            list.add(f);
        }
        for (CompletableFuture<?> future : list) {
            commandExecutor.get(future);
        }
        if (getListener() instanceof SubscriptionListener) {
            for (ChannelName channel : tosubscribe.keySet()) {
                ((SubscriptionListener) getListener()).onChannelSubscribed(channel.getName(), 1);
            }
        }

        // hack for RedisMessageListenerContainer
        if (getListener().getClass().getName().equals("org.springframework.data.redis.listener.SynchronizingMessageListener")) {
            StringWriter sw = new StringWriter();
            new Exception().printStackTrace(new PrintWriter(sw));
            String[] r = sw.toString().split("\n");
            if (r.length != 7) {
                return;
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Map<ChannelName, CompletableFuture<Void>> getNonSubscribed(byte[][] channels,
                                                                       Map<ChannelName, CompletableFuture<Void>> subscribed,
                                                                       BiConsumer<SubscriptionListener, byte[]> consumer) {
        Map<ChannelName, CompletableFuture<Void>> tosubscribe = new HashMap<>();
        for (byte[] ch : channels) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            ChannelName n = new ChannelName(ch);
            CompletableFuture<Void> cf = subscribed.putIfAbsent(n, f);
            if (cf == null) {
                tosubscribe.put(n, f);
            } else {
                if (getListener() instanceof SubscriptionListener) {
                    if (cf.isDone()) {
                        commandExecutor.getServiceManager().getExecutor().submit(() -> {
                            consumer.accept((SubscriptionListener) getListener(), ch);
                        });
                    } else {
                        cf.thenAccept(r -> {
                            consumer.accept((SubscriptionListener) getListener(), ch);
                        });
                    }
                }
            }
        }

        return tosubscribe;
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
        Map<ChannelName, CompletableFuture<Void>> tosubscribe = getNonSubscribed(patterns, psubscribed, (l, ch) -> {
            ((SubscriptionListener) getListener()).onPatternSubscribed(ch, 1);
        });

        if (tosubscribe.isEmpty()) {
            return;
        }

        List<CompletableFuture<?>> list = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        for (ChannelName channel : tosubscribe.keySet()) {
            CompletableFuture<Collection<PubSubConnectionEntry>> f = subscribeService.psubscribe(channel, ByteArrayCodec.INSTANCE, new BaseRedisPubSubListener() {
                @Override
                public void onPatternMessage(CharSequence pattern, CharSequence ch, Object message) {
                    if (!Arrays.equals(((ChannelName) pattern).getName(), channel.getName())) {
                        return;
                    }

                    byte[] m = toBytes(message);
                    DefaultMessage msg = new DefaultMessage(((ChannelName)ch).getName(), m);
                    getListener().onMessage(msg, ((ChannelName)pattern).getName());
                }

                @Override
                public void onStatus(PubSubType type, CharSequence pattern) {
                    if (!Arrays.equals(((ChannelName) pattern).getName(), channel.getName())) {
                        return;
                    }

                    if (getListener() instanceof SubscriptionListener
                            && type == PubSubType.PSUBSCRIBE) {
                        CompletableFuture<Void> callback = psubscribed.getOrDefault(channel, COMPLETED);
                        callback.complete(null);
                    }
                    super.onStatus(type, pattern);
                    if (type == PubSubType.PUNSUBSCRIBE) {
                        psubscribed.remove(channel);
                        latch.countDown();
                    }
                }
            });
            list.add(f);
        }
        for (CompletableFuture<?> future : list) {
            commandExecutor.get(future);
        }
        if (getListener() instanceof SubscriptionListener) {
            for (ChannelName channel : tosubscribe.keySet()) {
                ((SubscriptionListener) getListener()).onPatternSubscribed(channel.getName(), 1);
            }
        }

        // hack for RedisMessageListenerContainer
        if (getListener().getClass().getName().equals("org.springframework.data.redis.listener.SynchronizingMessageListener")) {
            StringWriter sw = new StringWriter();
            new Exception().printStackTrace(new PrintWriter(sw));
            String[] r = sw.toString().split("\n");
            if (r.length != 7) {
                return;
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
