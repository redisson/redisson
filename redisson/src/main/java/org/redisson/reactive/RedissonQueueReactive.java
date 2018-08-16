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
import org.redisson.RedissonQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RQueueAsync;
import org.redisson.api.RQueueReactive;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveExecutor;

/**
 * Distributed and concurrent implementation of {@link java.util.Queue}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonQueueReactive<V> extends RedissonListReactive<V> implements RQueueReactive<V> {

    private final RQueueAsync<V> instance;
    
    public RedissonQueueReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        instance = new RedissonQueue<V>(commandExecutor, name, null);
    }

    public RedissonQueueReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        instance = new RedissonQueue<V>(codec, commandExecutor, name, null);
    }

    @Override
    public Publisher<Integer> offer(V e) {
        return add(e);
    }

    @Override
    public Publisher<V> poll() {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollAsync();
            }
        });
    }

    @Override
    public Publisher<V> peek() {
        return get(0);
    }

    @Override
    public Publisher<V> pollLastAndOfferFirstTo(final String queueName) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollLastAndOfferFirstToAsync(queueName);
            }
        });
    }

}
