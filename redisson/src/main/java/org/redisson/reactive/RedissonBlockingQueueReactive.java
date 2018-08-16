/**
 * Copyright 2018 Nikita Koksharov
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
package org.redisson.reactive;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonBlockingQueue;
import org.redisson.api.RBlockingQueueAsync;
import org.redisson.api.RBlockingQueueReactive;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveExecutor;

/**
 * <p>Distributed and concurrent implementation of {@link java.util.concurrent.BlockingQueue}.
 *
 * <p>Queue size limited by Redis server memory amount.
 *
 * @author pdeschen@gmail.com
 * @author Nikita Koksharov
 */
public class RedissonBlockingQueueReactive<V> extends RedissonQueueReactive<V> implements RBlockingQueueReactive<V> {

    private final RBlockingQueueAsync<V> instance;
    
    public RedissonBlockingQueueReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        instance = new RedissonBlockingQueue<V>(commandExecutor, name, null);
    }

    public RedissonBlockingQueueReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        instance = new RedissonBlockingQueue<V>(codec, commandExecutor, name, null);
    }

    @Override
    public Publisher<Integer> put(V e) {
        return offer(e);
    }

    @Override
    public Publisher<V> take() {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.takeAsync();
            }
        });
    }

    @Override
    public Publisher<V> poll(final long timeout, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollAsync(timeout, unit);
            }
        });
    }

    @Override
    public Publisher<V> pollFromAny(final long timeout, final TimeUnit unit, final String ... queueNames) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollFromAnyAsync(timeout, unit, queueNames);
            }
        });
    }

    @Override
    public Publisher<V> pollLastAndOfferFirstTo(final String queueName, final long timeout, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollLastAndOfferFirstToAsync(queueName, timeout, unit);
            }
        });
    }

    @Override
    public Publisher<Integer> drainTo(final Collection<? super V> c) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.drainToAsync(c);
            }
        });
    }

    @Override
    public Publisher<Integer> drainTo(final Collection<? super V> c, final int maxElements) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.drainToAsync(c, maxElements);
            }
        });
    }
}