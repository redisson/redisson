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
import org.redisson.RedissonBlockingDeque;
import org.redisson.api.RBlockingDequeAsync;
import org.redisson.api.RBlockingDequeReactive;
import org.redisson.api.RBlockingQueueReactive;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveExecutor;

/**
 * Distributed and concurrent implementation of {@link java.util.concurrent.BlockingQueue}.
 *
 * @author Nikita Koksharov
 */
public class RedissonBlockingDequeReactive<V> extends RedissonDequeReactive<V> implements RBlockingDequeReactive<V> {

    private final RBlockingDequeAsync<V> instance;
    private final RBlockingQueueReactive<V> blockingQueue;
    
    public RedissonBlockingDequeReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        blockingQueue = new RedissonBlockingQueueReactive<V>(commandExecutor, name);
        instance = new RedissonBlockingDeque<V>(commandExecutor, name, null);
    }

    public RedissonBlockingDequeReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        blockingQueue = new RedissonBlockingQueueReactive<V>(codec, commandExecutor, name);
        instance = new RedissonBlockingDeque<V>(codec, commandExecutor, name, null);
    }

    @Override
    public Publisher<Integer> put(V e) {
        return offer(e);
    }

    @Override
    public Publisher<V> take() {
        return blockingQueue.take();
    }

    @Override
    public Publisher<V> poll(long timeout, TimeUnit unit) {
        return blockingQueue.poll(timeout, unit);
    }

    @Override
    public Publisher<V> pollFromAny(long timeout, TimeUnit unit, String ... queueNames) {
        return blockingQueue.pollFromAny(timeout, unit, queueNames);
    }

    @Override
    public Publisher<V> pollLastAndOfferFirstTo(String queueName, long timeout, TimeUnit unit) {
        return blockingQueue.pollLastAndOfferFirstTo(queueName, timeout, unit);
    }

    @Override
    public Publisher<Integer> drainTo(Collection<? super V> c) {
        return blockingQueue.drainTo(c);
    }

    @Override
    public Publisher<Integer> drainTo(Collection<? super V> c, int maxElements) {
        return blockingQueue.drainTo(c, maxElements);
    }

    @Override
    public Publisher<V> pollFirstFromAny(final long timeout, final TimeUnit unit, final String... queueNames) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollFirstFromAnyAsync(timeout, unit, queueNames);
            }
        });
    }

    @Override
    public Publisher<V> pollLastFromAny(final long timeout, final TimeUnit unit, final String... queueNames) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollLastFromAnyAsync(timeout, unit, queueNames);
            }
        });
    }

    @Override
    public Publisher<Void> putFirst(final V e) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.putFirstAsync(e);
            }
        });
    }

    @Override
    public Publisher<Void> putLast(final V e) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.putLastAsync(e);
            }
        });
    }

    @Override
    public Publisher<V> pollLast(final long timeout, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollLastAsync(timeout, unit);
            }
        });
    }

    @Override
    public Publisher<V> takeLast() {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.takeLastAsync();
            }
        });
    }

    @Override
    public Publisher<V> pollFirst(final long timeout, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollFirstAsync(timeout, unit);
            }
        });
    }

    @Override
    public Publisher<V> takeFirst() {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.takeFirstAsync();
            }
        });
    }
}