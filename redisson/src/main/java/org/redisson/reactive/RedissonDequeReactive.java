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

import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonDeque;
import org.redisson.api.RDequeAsync;
import org.redisson.api.RDequeReactive;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveExecutor;

/**
 * Distributed and concurrent implementation of {@link java.util.Queue}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonDequeReactive<V> extends RedissonQueueReactive<V> implements RDequeReactive<V> {

    private final RDequeAsync<V> instance;
    
    public RedissonDequeReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        instance = new RedissonDeque<V>(commandExecutor, name, null);
    }

    public RedissonDequeReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        instance = new RedissonDeque<V>(codec, commandExecutor, name, null);
    }

    @Override
    public Publisher<Void> addFirst(final V e) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.addFirstAsync(e);
            }
        });
    }

    @Override
    public Publisher<Void> addLast(final V e) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.addLastAsync(e);
            }
        });
    }

    @Override
    public Publisher<V> getLast() {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.getLastAsync();
            }
        });
    }

    @Override
    public Publisher<Boolean> offerFirst(final V e) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.offerFirstAsync(e);
            }
        });
    }

    @Override
    public Publisher<Integer> offerLast(V e) {
        return offer(e);
    }

    @Override
    public Publisher<V> peekFirst() {
        return get(0);
    }

    @Override
    public Publisher<V> peekLast() {
        return getLast();
    }

    @Override
    public Publisher<V> pollFirst() {
        return poll();
    }

    @Override
    public Publisher<V> pollLast() {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollLastAsync();
            }
        });
    }

    @Override
    public Publisher<V> pop() {
        return poll();
    }

    @Override
    public Publisher<Void> push(V e) {
        return addFirst(e);
    }

    @Override
    public Publisher<Boolean> removeFirstOccurrence(Object o) {
        return remove(o, 1);
    }

    @Override
    public Publisher<V> removeFirst() {
        return poll();
    }

    @Override
    public Publisher<V> removeLast() {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.removeLastAsync();
            }
        });
    }

    @Override
    public Publisher<Boolean> removeLastOccurrence(Object o) {
        return remove(o, -1);
    }

}
