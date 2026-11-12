/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import org.redisson.client.ChannelName;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.connection.ConnectionManager;
import org.redisson.pubsub.PubSubConnectionEntry;
import org.redisson.pubsub.PublishSubscribeService;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.connection.SubscriptionListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveSubscription implements ReactiveSubscription {

    private final Map<ChannelName, Collection<PubSubConnectionEntry>> channels = new ConcurrentHashMap<>();
    private final Map<ChannelName, Collection<PubSubConnectionEntry>> patterns = new ConcurrentHashMap<>();

    private final Set<FluxSink<Message<ByteBuffer, ByteBuffer>>> emitters = ConcurrentHashMap.newKeySet();

    private final RedisPubSubListener subscriptionListener;
    private final PublishSubscribeService subscribeService;
    
    public RedissonReactiveSubscription(ConnectionManager connectionManager, SubscriptionListener subscriptionListener) {
        this.subscribeService = connectionManager.getSubscribeService();
        this.subscriptionListener = new RedisPubSubListener() {

            @Override
            public void onStatus(PubSubType type, CharSequence channel) {
                if (type == PubSubType.SUBSCRIBE) {
                    subscriptionListener.onChannelSubscribed(((ChannelName) channel).getName(), 1L);
                } else if (type == PubSubType.PSUBSCRIBE) {
                    subscriptionListener.onPatternSubscribed(((ChannelName) channel).getName(), 1L);
                } else if (type == PubSubType.UNSUBSCRIBE) {
                    subscriptionListener.onChannelUnsubscribed(((ChannelName) channel).getName(), 1L);
                } else if (type == PubSubType.PUNSUBSCRIBE) {
                    subscriptionListener.onPatternUnsubscribed(((ChannelName) channel).getName(), 1L);
                }
            }

            @Override
            public void onMessage(CharSequence channel, Object msg) {
                ChannelName name = (ChannelName) channel;
                if (!channels.containsKey(name)) {
                    return;
                }

                for (FluxSink<Message<ByteBuffer, ByteBuffer>> emitter : emitters) {
                    emitter.next(new ChannelMessage<>(ByteBuffer.wrap(name.getName()), ByteBuffer.wrap((byte[]) msg)));
                }
            }

            @Override
            public void onPatternMessage(CharSequence pattern, CharSequence channel, Object message) {
                ChannelName name = (ChannelName) pattern;
                if (!patterns.containsKey(name)) {
                    return;
                }

                for (FluxSink<Message<ByteBuffer, ByteBuffer>> emitter : emitters) {
                    emitter.next(new PatternMessage<>(ByteBuffer.wrap(name.getName()),
                                                        ByteBuffer.wrap(((ChannelName) channel).getName()),
                                                        ByteBuffer.wrap((byte[]) message)));
                }
            }
        };
    }

    @Override
    public Mono<Void> subscribe(ByteBuffer... channels) {
        return Mono.defer(() -> {
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (ByteBuffer channel : channels) {
                ChannelName cn = toChannelName(channel);
                CompletableFuture<List<PubSubConnectionEntry>> f = subscribeService.subscribe(ByteArrayCodec.INSTANCE, cn, subscriptionListener);
                f = f.whenComplete((res, e) -> RedissonReactiveSubscription.this.channels.put(cn, res));
                futures.add(f);
            }

            CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return Mono.fromFuture(future);
        });
    }

    protected ChannelName toChannelName(ByteBuffer channel) {
        return new ChannelName(RedissonBaseReactive.toByteArray(channel));
    }

    @Override
    public Mono<Void> pSubscribe(ByteBuffer... patterns) {
        return Mono.defer(() -> {
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (ByteBuffer channel : patterns) {
                ChannelName cn = toChannelName(channel);
                CompletableFuture<Collection<PubSubConnectionEntry>> f = subscribeService.psubscribe(cn, ByteArrayCodec.INSTANCE, subscriptionListener);
                f = f.whenComplete((res, e) -> RedissonReactiveSubscription.this.patterns.put(cn, res));
                futures.add(f);
            }

            CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return Mono.fromFuture(future);
        });
    }

    @Override
    public Mono<Void> unsubscribe() {
        return unsubscribe(channels.keySet().stream().map(b -> ByteBuffer.wrap(b.getName())).distinct().toArray(ByteBuffer[]::new));
    }

    @Override
    public Mono<Void> unsubscribe(ByteBuffer... channels) {
        return Mono.defer(() -> {
            List<CompletableFuture<?>> futures = new ArrayList<>(channels.length);
            for (ByteBuffer channel : channels) {
                ChannelName cn = toChannelName(channel);
                if (RedissonReactiveSubscription.this.channels.remove(cn) == null) {
                    continue;
                }

                CompletableFuture<Void> f = subscribeService.removeListenerAsync(PubSubType.UNSUBSCRIBE,
                                    ChannelName.newList(cn), System.identityHashCode(subscriptionListener));
                f = f.whenComplete((res, e) -> {
                    if (e == null) {
                        subscriptionListener.onStatus(PubSubType.UNSUBSCRIBE, cn);
                    }
                });
                futures.add(f);
            }

            CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return Mono.fromFuture(future);
        });
    }

    @Override
    public Mono<Void> pUnsubscribe() {
        return pUnsubscribe(patterns.keySet().stream().map(b -> ByteBuffer.wrap(b.getName())).distinct().toArray(ByteBuffer[]::new));
    }

    @Override
    public Mono<Void> pUnsubscribe(ByteBuffer... patterns) {
        return Mono.defer(() -> {
            List<CompletableFuture<?>> futures = new ArrayList<>(patterns.length);
            for (ByteBuffer channel : patterns) {
                ChannelName cn = toChannelName(channel);
                if (RedissonReactiveSubscription.this.patterns.remove(cn) == null) {
                    continue;
                }

                CompletableFuture<Void> f = subscribeService.removeListenerAsync(PubSubType.PUNSUBSCRIBE,
                                    ChannelName.newList(cn), System.identityHashCode(subscriptionListener));
                f = f.whenComplete((res, e) -> {
                    if (e == null) {
                        subscriptionListener.onStatus(PubSubType.PUNSUBSCRIBE, cn);
                    }
                });
                futures.add(f);
            }

            CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return Mono.fromFuture(future);
        });
    }

    @Override
    public Set<ByteBuffer> getChannels() {
        return channels.keySet().stream().map(b -> ByteBuffer.wrap(b.getName())).collect(Collectors.toSet());
    }

    @Override
    public Set<ByteBuffer> getPatterns() {
        return patterns.keySet().stream().map(b -> ByteBuffer.wrap(b.getName())).collect(Collectors.toSet());
    }
    
    @Override
    public Flux<Message<ByteBuffer, ByteBuffer>> receive() {
        return Flux.create(emitter -> {
            emitters.add(emitter);
            emitter.onDispose(() -> emitters.remove(emitter));
        });
    }

    @Override
    public Mono<Void> cancel() {
        return unsubscribe().then(pUnsubscribe()).then(Mono.fromRunnable(emitters::clear));
    }
}
