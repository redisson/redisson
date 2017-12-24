/**
 * Copyright 2016 Nikita Koksharov
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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.reactivestreams.Publisher;
import org.redisson.RedissonSet;
import org.redisson.api.RFuture;
import org.redisson.api.RSetReactive;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.ScanCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.command.CommandReactiveExecutor;

import reactor.fn.Supplier;

/**
 * Distributed and concurrent implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSetReactive<V> extends RedissonExpirableReactive implements RSetReactive<V> {

    private final RedissonSet<V> instance;

    public RedissonSetReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        instance = new RedissonSet<V>(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name, null);
    }

    public RedissonSetReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        instance = new RedissonSet<V>(codec, commandExecutor, name, null);
    }

    @Override
    public Publisher<Integer> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<V>(this).addAll(c);
    }

    @Override
    public Publisher<Integer> size() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.SCARD_INT, getName());
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

    private Publisher<ListScanResult<ScanObjectEntry>> scanIteratorReactive(RedisClient client, long startPos) {
        return commandExecutor.readReactive(client, getName(), new ScanCodec(codec), RedisCommands.SSCAN, getName(), startPos);
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
    public Publisher<V> iterator() {
        return new SetReactiveIterator<V>() {
            @Override
            protected Publisher<ListScanResult<ScanObjectEntry>> scanIteratorReactive(RedisClient client, long nextIterPos) {
                return RedissonSetReactive.this.scanIteratorReactive(client, nextIterPos);
            }
        };
    }

}
