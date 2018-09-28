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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonObject;
import org.redisson.RedissonSet;
import org.redisson.api.RFuture;
import org.redisson.api.RSet;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandReactiveExecutor;

import reactor.core.publisher.Flux;

/**
 * Distributed and concurrent implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSetReactive<V> {

    private final RSet<V> instance;
    private final CommandReactiveExecutor commandExecutor;

    public RedissonSetReactive(CommandReactiveExecutor commandExecutor, RSet<V> instance) {
        this.commandExecutor = commandExecutor;
        this.instance = instance;
    }

    public Publisher<Integer> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<Object>() {
            @Override
            public Publisher<Integer> add(Object e) {
                return RedissonSetReactive.this.add((V)e);
            }
        }.addAll(c);
    }

    private Publisher<ListScanResult<Object>> scanIteratorReactive(final RedisClient client, final long startPos, final String pattern, final int count) {
        return commandExecutor.reactive(new Supplier<RFuture<ListScanResult<Object>>>() {
            @Override
            public RFuture<ListScanResult<Object>> get() {
                return ((RedissonSet)instance).scanIteratorAsync(instance.getName(), client, startPos, pattern, count);
    }
        });
    }

    public Publisher<Integer> add(V e) {
        return commandExecutor.writeReactive(instance.getName(), instance.getCodec(), RedisCommands.SADD, instance.getName(), ((RedissonObject)instance).encode(e));
    }

    public Publisher<Integer> addAll(Collection<? extends V> c) {
        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(instance.getName());
        ((RedissonObject)instance).encode(args, c);
        return commandExecutor.writeReactive(instance.getName(), instance.getCodec(), RedisCommands.SADD, args.toArray());
    }

    public Publisher<Long> intersection(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(instance.getName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeReactive(instance.getName(), instance.getCodec(), RedisCommands.SINTERSTORE, args.toArray());
    }
    
    public Publisher<Long> diff(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(instance.getName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeReactive(instance.getName(), instance.getCodec(), RedisCommands.SDIFFSTORE, args.toArray());
    }
    
    public Publisher<Long> union(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(instance.getName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeReactive(instance.getName(), instance.getCodec(), RedisCommands.SUNIONSTORE, args.toArray());
    }

    public Publisher<V> iterator(int count) {
        return iterator(null, count);
    }
    
    public Publisher<V> iterator(String pattern) {
        return iterator(pattern, 10);
    }

    public Publisher<V> iterator(final String pattern, final int count) {
        return Flux.create(new SetReactiveIterator<V>() {
            @Override
            protected RFuture<ListScanResult<Object>> scanIterator(RedisClient client, long nextIterPos) {
                return ((RedissonSet)instance).scanIteratorAsync(instance.getName(), client, nextIterPos, pattern, count);
            }
        });
    }

    public Publisher<V> iterator() {
        return iterator(null, 10);
}
    
            }
