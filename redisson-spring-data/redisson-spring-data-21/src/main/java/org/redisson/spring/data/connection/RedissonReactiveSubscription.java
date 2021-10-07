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
package org.redisson.spring.data.connection;

import org.redisson.api.RFuture;
import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.ChannelName;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.connection.ConnectionManager;
import org.redisson.misc.CountableListener;
import org.redisson.misc.RedissonPromise;
import org.redisson.pubsub.PubSubConnectionEntry;
import org.redisson.pubsub.PublishSubscribeService;
import org.springframework.data.redis.connection.ReactiveSubscription;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveSubscription implements ReactiveSubscription {

    public static class ListenableCounter {

        private int state;
        private Runnable r;

        public synchronized void acquire() {
            state++;
        }

        public void release() {
            synchronized (this) {
                state--;
                if (state != 0) {
                    return;
                }
            }

            if (r != null) {
                r.run();
                r = null;
            }
        }

        public synchronized void addListener(Runnable r) {
            synchronized (this) {
                if (state != 0) {
                    this.r = r;
                    return;
                }
            }

            r.run();
        }

    }

    private final Map<ChannelName, PubSubConnectionEntry> channels = new ConcurrentHashMap<>();
    private final Map<ChannelName, Collection<PubSubConnectionEntry>> patterns = new ConcurrentHashMap<>();

    private final ListenableCounter monosListener = new ListenableCounter();

    private final PublishSubscribeService subscribeService;
    
    public RedissonReactiveSubscription(ConnectionManager connectionManager) {
        this.subscribeService = connectionManager.getSubscribeService();
    }

    @Override
    public Mono<Void> subscribe(ByteBuffer... channels) {
        monosListener.acquire();
        return Mono.defer(() -> {
            RedissonPromise<Void> result = new RedissonPromise<>();
            result.onComplete((r, ex) -> {
                monosListener.release();
            });
            CountableListener<Void> listener = new CountableListener<>(result, null, channels.length);
            for (ByteBuffer channel : channels) {
                ChannelName cn = toChannelName(channel);
                RFuture<PubSubConnectionEntry> f = subscribeService.subscribe(ByteArrayCodec.INSTANCE, cn);
                f.onComplete((res, e) -> RedissonReactiveSubscription.this.channels.put(cn, res));
                f.onComplete(listener);
            }

            return Mono.fromFuture(result);
        });
    }

    protected ChannelName toChannelName(ByteBuffer channel) {
        return new ChannelName(RedissonBaseReactive.toByteArray(channel));
    }

    @Override
    public Mono<Void> pSubscribe(ByteBuffer... patterns) {
        monosListener.acquire();
        return Mono.defer(() -> {
            RedissonPromise<Void> result = new RedissonPromise<>();
            result.onComplete((r, ex) -> {
                monosListener.release();
            });
            CountableListener<Void> listener = new CountableListener<>(result, null, patterns.length);
            for (ByteBuffer channel : patterns) {
                ChannelName cn = toChannelName(channel);
                RFuture<Collection<PubSubConnectionEntry>> f = subscribeService.psubscribe(cn, ByteArrayCodec.INSTANCE);
                f.onComplete((res, e) -> RedissonReactiveSubscription.this.patterns.put(cn, res));
                f.onComplete(listener);
            }
            return Mono.fromFuture(result);
        });
    }

    @Override
    public Mono<Void> unsubscribe() {
        return unsubscribe(channels.keySet().stream().map(b -> ByteBuffer.wrap(b.getName())).distinct().toArray(ByteBuffer[]::new));
    }

    @Override
    public Mono<Void> unsubscribe(ByteBuffer... channels) {
        monosListener.acquire();
        return Mono.defer(() -> {
            RedissonPromise<Void> result = new RedissonPromise<>();
            result.onComplete((r, ex) -> {
                monosListener.release();
            });
            CountableListener<Void> listener = new CountableListener<>(result, null, channels.length);
            for (ByteBuffer channel : channels) {
                ChannelName cn = toChannelName(channel);
                RFuture<Codec> f = subscribeService.unsubscribe(cn, PubSubType.UNSUBSCRIBE);
                f.onComplete((res, e) -> {
                    synchronized (RedissonReactiveSubscription.this.channels) {
                        PubSubConnectionEntry entry = RedissonReactiveSubscription.this.channels.get(cn);
                        if (!entry.hasListeners(cn)) {
                            RedissonReactiveSubscription.this.channels.remove(cn);
                        }
                    }
                });
                f.onComplete(listener);
            }
            return Mono.fromFuture(result);
        });
    }

    @Override
    public Mono<Void> pUnsubscribe() {
        return unsubscribe(patterns.keySet().stream().map(b -> ByteBuffer.wrap(b.getName())).distinct().toArray(ByteBuffer[]::new));
    }

    @Override
    public Mono<Void> pUnsubscribe(ByteBuffer... patterns) {
        monosListener.acquire();
        return Mono.defer(() -> {
            RedissonPromise<Void> result = new RedissonPromise<>();
            result.onComplete((r, ex) -> {
                monosListener.release();
            });
            CountableListener<Void> listener = new CountableListener<>(result, null, patterns.length);
            for (ByteBuffer channel : patterns) {
                ChannelName cn = toChannelName(channel);
                RFuture<Codec> f = subscribeService.unsubscribe(cn, PubSubType.PUNSUBSCRIBE);
                f.onComplete((res, e) -> {
                    synchronized (RedissonReactiveSubscription.this.patterns) {
                        Collection<PubSubConnectionEntry> entries = RedissonReactiveSubscription.this.patterns.get(cn);
                        entries.stream()
                                .filter(en -> en.hasListeners(cn))
                                .forEach(ee -> RedissonReactiveSubscription.this.patterns.remove(cn));
                    }
                });
                f.onComplete(listener);
            }
            return Mono.fromFuture(result);
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
    
    private final AtomicReference<Flux<Message<ByteBuffer, ByteBuffer>>> flux = new AtomicReference<>();
    private volatile Disposable disposable;

    @Override
    public Flux<Message<ByteBuffer, ByteBuffer>> receive() {
        if (flux.get() != null) {
            return flux.get();
        }

        Flux<Message<ByteBuffer, ByteBuffer>> f = Flux.create(emitter -> {
            emitter.onRequest(n -> {

                monosListener.addListener(() -> {
                    BaseRedisPubSubListener listener = new BaseRedisPubSubListener() {
                        @Override
                        public void onPatternMessage(CharSequence pattern, CharSequence channel, Object message) {
                            if (!patterns.containsKey(new ChannelName(pattern.toString()))) {
                                return;
                            }

                            emitter.next(new PatternMessage<>(ByteBuffer.wrap(pattern.toString().getBytes()),
                                                                ByteBuffer.wrap(channel.toString().getBytes()),
                                                                ByteBuffer.wrap((byte[])message)));
                        }

                        @Override
                        public void onMessage(CharSequence channel, Object msg) {
                            if (!channels.containsKey(new ChannelName(channel.toString()))) {
                                return;
                            }

                            emitter.next(new ChannelMessage<>(ByteBuffer.wrap(channel.toString().getBytes()), ByteBuffer.wrap((byte[])msg)));
                        }
                    };

                    disposable = () -> {
                        for (Entry<ChannelName, PubSubConnectionEntry> entry : channels.entrySet()) {
                            entry.getValue().removeListener(entry.getKey(), listener);
                        }
                        for (Entry<ChannelName, Collection<PubSubConnectionEntry>> entry : patterns.entrySet()) {
                            for (PubSubConnectionEntry pubSubConnectionEntry : entry.getValue()) {
                                pubSubConnectionEntry.removeListener(entry.getKey(), listener);
                            }
                        }
                    };

                    for (Entry<ChannelName, PubSubConnectionEntry> entry : channels.entrySet()) {
                        entry.getValue().addListener(entry.getKey(), listener);
                    }
                    for (Entry<ChannelName, Collection<PubSubConnectionEntry>> entry : patterns.entrySet()) {
                            for (PubSubConnectionEntry pubSubConnectionEntry : entry.getValue()) {
                                pubSubConnectionEntry.addListener(entry.getKey(), listener);
                            }
                    }

                    emitter.onDispose(disposable);
                });
            });
        });
        
        if (flux.compareAndSet(null, f)) {
            return f;
        }
        return flux.get();
    }

    @Override
    public Mono<Void> cancel() {
        return unsubscribe().then(pUnsubscribe()).then(Mono.fromRunnable(() -> {
            if (disposable != null) {
                disposable.dispose();
            }
        }));
    }
}
