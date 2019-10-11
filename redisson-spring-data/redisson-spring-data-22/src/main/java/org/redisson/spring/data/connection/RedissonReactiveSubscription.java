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
package org.redisson.spring.data.connection;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveSubscription implements ReactiveSubscription {

    private final Map<ByteBuffer, PubSubConnectionEntry> channels = new ConcurrentHashMap<ByteBuffer, PubSubConnectionEntry>();
    private final Map<ByteBuffer, PubSubConnectionEntry> patterns = new ConcurrentHashMap<ByteBuffer, PubSubConnectionEntry>();
    
    private final PublishSubscribeService subscribeService;
    
    public RedissonReactiveSubscription(ConnectionManager connectionManager) {
        this.subscribeService = connectionManager.getSubscribeService();
    }

    @Override
    public Mono<Void> subscribe(ByteBuffer... channels) {
        RedissonPromise<Void> result = new RedissonPromise<Void>();
        CountableListener<Void> listener = new CountableListener<Void>(result, null, channels.length);
        for (ByteBuffer channel : channels) {
            RFuture<PubSubConnectionEntry> f = subscribeService.subscribe(ByteArrayCodec.INSTANCE, toChannelName(channel));
            f.onComplete((res, e) -> RedissonReactiveSubscription.this.channels.put(channel, res));
            f.onComplete(listener);
        }
        return Mono.fromFuture(result);
    }

    protected ChannelName toChannelName(ByteBuffer channel) {
        return new ChannelName(RedissonBaseReactive.toByteArray(channel));
    }

    @Override
    public Mono<Void> pSubscribe(ByteBuffer... patterns) {
        RedissonPromise<Void> result = new RedissonPromise<Void>();
        CountableListener<Void> listener = new CountableListener<Void>(result, null, patterns.length);
        for (ByteBuffer channel : patterns) {
            RFuture<PubSubConnectionEntry> f = subscribeService.psubscribe(toChannelName(channel), ByteArrayCodec.INSTANCE);
            f.onComplete((res, e) -> RedissonReactiveSubscription.this.patterns.put(channel, res));
            f.onComplete(listener);
        }
        return Mono.fromFuture(result);
    }

    @Override
    public Mono<Void> unsubscribe() {
        return unsubscribe(channels.keySet().toArray(new ByteBuffer[channels.size()]));
    }

    @Override
    public Mono<Void> unsubscribe(ByteBuffer... channels) {
        RedissonPromise<Void> result = new RedissonPromise<Void>();
        CountableListener<Void> listener = new CountableListener<Void>(result, null, channels.length);
        for (ByteBuffer channel : channels) {
            RFuture<Codec> f = subscribeService.unsubscribe(toChannelName(channel), PubSubType.UNSUBSCRIBE);
            f.onComplete(listener);
        }
        return Mono.fromFuture(result);
    }

    @Override
    public Mono<Void> pUnsubscribe() {
        return unsubscribe(patterns.keySet().toArray(new ByteBuffer[patterns.size()]));
    }

    @Override
    public Mono<Void> pUnsubscribe(ByteBuffer... patterns) {
        RedissonPromise<Void> result = new RedissonPromise<Void>();
        CountableListener<Void> listener = new CountableListener<Void>(result, null, patterns.length);
        for (ByteBuffer channel : patterns) {
            RFuture<Codec> f = subscribeService.unsubscribe(toChannelName(channel), PubSubType.PUNSUBSCRIBE);
            f.onComplete(listener);
        }
        return Mono.fromFuture(result);
    }

    @Override
    public Set<ByteBuffer> getChannels() {
        return channels.keySet();
    }

    @Override
    public Set<ByteBuffer> getPatterns() {
        return patterns.keySet();
    }
    
    private final AtomicReference<Flux<Message<ByteBuffer, ByteBuffer>>> flux = new AtomicReference<>();
    private volatile Disposable disposable;

    @Override
    public Flux<Message<ByteBuffer, ByteBuffer>> receive() {
        if (flux.get() != null) {
            return flux.get();
        }
        
        Flux<Message<ByteBuffer, ByteBuffer>> f = Flux.<Message<ByteBuffer, ByteBuffer>>create(emitter -> {
            emitter.onRequest(n -> {
                
                AtomicLong counter = new AtomicLong(n);
                BaseRedisPubSubListener listener = new BaseRedisPubSubListener() {
                    @Override
                    public void onPatternMessage(CharSequence pattern, CharSequence channel, Object message) {
                        emitter.next(new PatternMessage<>(ByteBuffer.wrap(pattern.toString().getBytes()), 
                                ByteBuffer.wrap(channel.toString().getBytes()), ByteBuffer.wrap((byte[])message)));
                        
                        if (counter.decrementAndGet() == 0) {
                            disposable.dispose();
                            emitter.complete();
                        }
                    }
                    
                    @Override
                    public void onMessage(CharSequence channel, Object msg) {
                        emitter.next(new ChannelMessage<>(ByteBuffer.wrap(channel.toString().getBytes()), ByteBuffer.wrap((byte[])msg)));
                        
                        if (counter.decrementAndGet() == 0) {
                            disposable.dispose();
                            emitter.complete();
                        }
                    }
                };

                disposable = () -> {
                    for (Entry<ByteBuffer, PubSubConnectionEntry> entry : channels.entrySet()) {
                        entry.getValue().removeListener(toChannelName(entry.getKey()), listener);
                    }
                    for (Entry<ByteBuffer, PubSubConnectionEntry> entry : patterns.entrySet()) {
                        entry.getValue().removeListener(toChannelName(entry.getKey()), listener);
                    }
                };
                
                for (Entry<ByteBuffer, PubSubConnectionEntry> entry : channels.entrySet()) {
                    entry.getValue().addListener(toChannelName(entry.getKey()), listener);
                }
                
                for (Entry<ByteBuffer, PubSubConnectionEntry> entry : patterns.entrySet()) {
                    entry.getValue().addListener(toChannelName(entry.getKey()), listener);
                }
                
                emitter.onDispose(disposable);
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
