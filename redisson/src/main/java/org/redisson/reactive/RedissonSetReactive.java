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
import java.util.Set;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonSet;
import org.redisson.api.RFuture;
import org.redisson.api.RSetAsync;
import org.redisson.api.RSetReactive;
import org.redisson.api.SortOrder;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
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
public class RedissonSetReactive<V> extends RedissonExpirableReactive implements RSetReactive<V> {

    private final RSetAsync<V> instance;

    public RedissonSetReactive(CommandReactiveExecutor commandExecutor, String name) {
        this(commandExecutor, name, new RedissonSet<V>(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name, null));
    }
    
    public RedissonSetReactive(CommandReactiveExecutor commandExecutor, String name, RSetAsync<V> instance) {
        super(commandExecutor, name, instance);
        this.instance = instance;
    }

    public RedissonSetReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        this(codec, commandExecutor, name, new RedissonSet<V>(codec, commandExecutor, name, null));
    }
    
    public RedissonSetReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name, RSetAsync<V> instance) {
        super(codec, commandExecutor, name, instance);
        this.instance = instance;
    }


    @Override
    public Publisher<Integer> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<V>(this).addAll(c);
    }

    @Override
    public Publisher<Set<V>> removeRandom(final int amount) {
        return reactive(new Supplier<RFuture<Set<V>>>() {
            @Override
            public RFuture<Set<V>> get() {
                return instance.removeRandomAsync(amount);
            }
        });
    }
    
    @Override
    public Publisher<Integer> size() {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.sizeAsync();
            }
        });
    }

    @Override
    public Publisher<Boolean> contains(final Object o) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.containsAsync(o);
            }
        });
    }
    
    @Override
    public Publisher<Set<V>> readAll() {
        return reactive(new Supplier<RFuture<Set<V>>>() {
            @Override
            public RFuture<Set<V>> get() {
                return instance.readAllAsync();
            }
        });
    }

    private Publisher<ListScanResult<Object>> scanIteratorReactive(final RedisClient client, final long startPos, final String pattern, final int count) {
        return reactive(new Supplier<RFuture<ListScanResult<Object>>>() {
            @Override
            public RFuture<ListScanResult<Object>> get() {
                return ((RedissonSet)instance).scanIteratorAsync(getName(), client, startPos, pattern, count);
    }
        });
    }

    @Override
    public Publisher<Integer> add(V e) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SADD, getName(), encode(e));
    }

    @Override
    public Publisher<V> removeRandom() {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.removeRandomAsync();
            }
        });
    }

    @Override
    public Publisher<V> random() {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.randomAsync();
            }
        });
    }

    @Override
    public Publisher<Boolean> remove(final Object o) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.removeAsync(o);
            }
        });
    }

    @Override
    public Publisher<Boolean> move(final String destination, final V member) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.moveAsync(destination, member);
            }
        });
    }

    @Override
    public Publisher<Boolean> containsAll(final Collection<?> c) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.containsAllAsync(c);
            }
        });
    }

    @Override
    public Publisher<Integer> addAll(Collection<? extends V> c) {
        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getName());
        encode(args, c);
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SADD, args.toArray());
    }

    @Override
    public Publisher<Boolean> retainAll(final Collection<?> c) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.retainAllAsync(c);
            }
        });
    }

    @Override
    public Publisher<Boolean> removeAll(final Collection<?> c) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.removeAllAsync(c);
            }
        });
    }

    @Override
    public Publisher<Set<V>> readIntersection(final String... names) {
        return reactive(new Supplier<RFuture<Set<V>>>() {
            @Override
            public RFuture<Set<V>> get() {
                return instance.readIntersectionAsync(names);
            }
        });
    }
    
    @Override
    public Publisher<Long> intersection(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SINTERSTORE, args.toArray());
    }
    
    @Override
    public Publisher<Long> diff(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SDIFFSTORE, args.toArray());
    }
    
    @Override
    public Publisher<Set<V>> readDiff(final String... names) {
        return reactive(new Supplier<RFuture<Set<V>>>() {
            @Override
            public RFuture<Set<V>> get() {
                return instance.readDiffAsync(names);
            }
        });
    }
    
    @Override
    public Publisher<Long> union(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SUNIONSTORE, args.toArray());
    }

    @Override
    public Publisher<Set<V>> readUnion(final String... names) {
        return reactive(new Supplier<RFuture<Set<V>>>() {
            @Override
            public RFuture<Set<V>> get() {
                return instance.readUnionAsync(names);
            }
        });
    }

    @Override
    public Publisher<V> iterator(int count) {
        return iterator(null, count);
    }
    
    @Override
    public Publisher<V> iterator(String pattern) {
        return iterator(pattern, 10);
    }

    @Override
    public Publisher<V> iterator(final String pattern, final int count) {
        return Flux.create(new SetReactiveIterator<V>() {
            @Override
            protected Publisher<ListScanResult<Object>> scanIteratorReactive(RedisClient client, long nextIterPos) {
                return RedissonSetReactive.this.scanIteratorReactive(client, nextIterPos, pattern, count);
            }
        });
    }

    @Override
    public Publisher<V> iterator() {
        return iterator(null, 10);
}
    
    @Override
    public Publisher<Set<V>> readSorted(final SortOrder order) {
        return reactive(new Supplier<RFuture<Set<V>>>() {
            @Override
            public RFuture<Set<V>> get() {
                return instance.readSortAsync(order);
            }
        });
    }

    @Override
    public Publisher<Set<V>> readSorted(final SortOrder order, final int offset, final int count) {
        return reactive(new Supplier<RFuture<Set<V>>>() {
            @Override
            public RFuture<Set<V>> get() {
                return instance.readSortAsync(order, offset, count);
            }
        });
    }

    @Override
    public Publisher<Set<V>> readSorted(final String byPattern, final SortOrder order) {
        return reactive(new Supplier<RFuture<Set<V>>>() {
            @Override
            public RFuture<Set<V>> get() {
                return instance.readSortAsync(byPattern, order);
            }
        });
    }

    @Override
    public Publisher<Set<V>> readSorted(final String byPattern, final SortOrder order, final int offset, final int count) {
        return reactive(new Supplier<RFuture<Set<V>>>() {
            @Override
            public RFuture<Set<V>> get() {
                return instance.readSortAsync(byPattern, order, offset, count);
            }
        });
    }

    @Override
    public <T> Publisher<Collection<T>> readSorted(final String byPattern, final List<String> getPatterns, final SortOrder order) {
        return reactive(new Supplier<RFuture<Collection<T>>>() {
            @Override
            public RFuture<Collection<T>> get() {
                return instance.readSortAsync(byPattern, getPatterns, order);
            }
        });
    }

    @Override
    public <T> Publisher<Collection<T>> readSorted(final String byPattern, final List<String> getPatterns, final SortOrder order,
            final int offset, final int count) {
        return reactive(new Supplier<RFuture<Collection<T>>>() {
            @Override
            public RFuture<Collection<T>> get() {
                return instance.readSortAsync(byPattern, getPatterns, order, offset, count);
            }
        });
    }

    @Override
    public Publisher<Integer> sortTo(final String destName, final SortOrder order) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.sortToAsync(destName, order);
            }
        });
    }

    @Override
    public Publisher<Integer> sortTo(final String destName, final SortOrder order, final int offset, final int count) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.sortToAsync(destName, order, offset, count);
            }
        });
    }

    @Override
    public Publisher<Integer> sortTo(final String destName, final String byPattern, final SortOrder order) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.sortToAsync(destName, byPattern, order);
            }
        });
    }

    @Override
    public Publisher<Integer> sortTo(final String destName, final String byPattern, final SortOrder order, final int offset, final int count) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.sortToAsync(destName, byPattern, order, offset, count);
            }
        });
    }

    @Override
    public Publisher<Integer> sortTo(final String destName, final String byPattern, final List<String> getPatterns, final SortOrder order) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.sortToAsync(destName, byPattern, getPatterns, order);
            }
        });
    }

    @Override
    public Publisher<Integer> sortTo(final String destName, final String byPattern, final List<String> getPatterns, final SortOrder order,
            final int offset, final int count) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.sortToAsync(destName, byPattern, getPatterns, order, offset, count);
            }
        });
    }
    
}
