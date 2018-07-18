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

import static org.redisson.client.protocol.RedisCommands.LINDEX;
import static org.redisson.client.protocol.RedisCommands.LREM_SINGLE;
import static org.redisson.client.protocol.RedisCommands.RPUSH;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.RedissonList;
import org.redisson.api.RFuture;
import org.redisson.api.RListAsync;
import org.redisson.api.RListReactive;
import org.redisson.api.SortOrder;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.LongReplayConvertor;
import org.redisson.command.CommandReactiveExecutor;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

/**
 * Distributed and concurrent implementation of {@link java.util.List}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonListReactive<V> extends RedissonExpirableReactive implements RListReactive<V> {

    private final RListAsync<V> instance;

    public RedissonListReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name, new RedissonList<V>(commandExecutor, name, null));
        this.instance = (RListAsync<V>) super.instance;
    }

    public RedissonListReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name, new RedissonList<V>(codec, commandExecutor, name, null));
        this.instance = (RListAsync<V>) super.instance;
    }
    
    public RedissonListReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name, RListAsync<V> instance) {
        super(codec, commandExecutor, name, instance);
        this.instance = (RListAsync<V>) super.instance;
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
    public Publisher<V> descendingIterator() {
        return iterator(-1, false);
    }

    @Override
    public Publisher<V> iterator() {
        return iterator(0, true);
    }

    @Override
    public Publisher<V> descendingIterator(int startIndex) {
        return iterator(startIndex, false);
    }

    @Override
    public Publisher<V> iterator(int startIndex) {
        return iterator(startIndex, true);
    }

    private Publisher<V> iterator(final int startIndex, final boolean forward) {
        return Flux.create(new Consumer<FluxSink<V>>() {

            @Override
            public void accept(FluxSink<V> emitter) {
                emitter.onRequest(new LongConsumer() {
                    
                    int currentIndex = startIndex;
                    
                    @Override
                    public void accept(long value) {
                        onRequest(forward, emitter, value);
                    }
                    
                    protected void onRequest(final boolean forward, FluxSink<V> emitter, long n) {
                        get(currentIndex).subscribe(new Subscriber<V>() {
                            V currValue;

                            @Override
                            public void onSubscribe(Subscription s) {
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(V value) {
                                currValue = value;
                                emitter.next(value);
                                if (forward) {
                                    currentIndex++;
                                } else {
                                    currentIndex--;
                                }
                            }

                            @Override
                            public void onError(Throwable error) {
                                emitter.error(error);
                            }

                            @Override
                            public void onComplete() {
                                if (currValue == null) {
                                    emitter.complete();
                                    return;
                                }
                                if (n-1 == 0) {
                                    return;
                                }
                                onRequest(forward, emitter, n-1);
                            }
                        });
                    }
                });
                
            }

        });
    }
    
    @Override
    public Publisher<Void> trim(final int fromIndex, final int toIndex) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.trimAsync(fromIndex, toIndex);
            }
        });
    }
    
    @Override
    public Publisher<Void> fastRemove(final long index) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.fastRemoveAsync(index);
            }
        });
    }

    @Override
    public Publisher<List<V>> readAll() {
        return reactive(new Supplier<RFuture<List<V>>>() {
            @Override
            public RFuture<List<V>> get() {
                return instance.readAllAsync();
            }
        });
    }
    
    @Override
    public Publisher<Integer> addBefore(final V elementToFind, final V element) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.addBeforeAsync(elementToFind, element);
            }
        });
    }
    
    @Override
    public Publisher<Integer> addAfter(final V elementToFind, final V element) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.addAfterAsync(elementToFind, element);
            }
        });
    }
    
    @Override
    public Publisher<List<V>> get(final int ...indexes) {
        return reactive(new Supplier<RFuture<List<V>>>() {
            @Override
            public RFuture<List<V>> get() {
                return instance.getAsync(indexes);
            }
        });
    }
    
    @Override
    public Publisher<Integer> add(V e) {
        return commandExecutor.writeReactive(getName(), codec, RPUSH, getName(), encode(e));
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

    protected Publisher<Boolean> remove(Object o, int count) {
        return commandExecutor.writeReactive(getName(), codec, LREM_SINGLE, getName(), count, encode(o));
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
    public Publisher<Integer> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<V>(this) {

            @Override
            public Integer sum(Integer first, Integer second) {
                return second;
            }

        }.addAll(c);
    }

    @Override
    public Publisher<Integer> addAll(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return size();
        }

        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getName());
        encode(args, c);
        return commandExecutor.writeReactive(getName(), codec, RPUSH, args.toArray());
    }

    @Override
    public Publisher<Integer> addAll(long index, Collection<? extends V> coll) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("index: " + index);
        }

        if (coll.isEmpty()) {
            return size();
        }

        if (index == 0) { // prepend elements to list
            List<Object> elements = new ArrayList<Object>();
            encode(elements, coll);
            Collections.reverse(elements);
            elements.add(0, getName());

            return commandExecutor.writeReactive(getName(), codec, RedisCommands.LPUSH, elements.toArray());
        }

        List<Object> args = new ArrayList<Object>(coll.size() + 1);
        args.add(index);
        encode(args, coll);
        return commandExecutor.evalWriteReactive(getName(), codec, RedisCommands.EVAL_INTEGER,
                "local ind = table.remove(ARGV, 1); " + // index is the first parameter
                        "local size = redis.call('llen', KEYS[1]); " +
                        "assert(tonumber(ind) <= size, 'index: ' .. ind .. ' but current size: ' .. size); " +
                        "local tail = redis.call('lrange', KEYS[1], ind, -1); " +
                        "redis.call('ltrim', KEYS[1], 0, ind - 1); " +
                        "for i, v in ipairs(ARGV) do redis.call('rpush', KEYS[1], v) end;" +
                        "for i, v in ipairs(tail) do redis.call('rpush', KEYS[1], v) end;" +
                        "return redis.call('llen', KEYS[1]);",
                Collections.<Object>singletonList(getName()), args.toArray());
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
    public Publisher<Boolean> retainAll(final Collection<?> c) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.retainAllAsync(c);
            }
        });
    }

    @Override
    public Publisher<V> get(long index) {
        return commandExecutor.readReactive(getName(), codec, LINDEX, getName(), index);
    }

    @Override
    public Publisher<V> set(long index, V element) {
        return commandExecutor.evalWriteReactive(getName(), codec, RedisCommands.EVAL_OBJECT,
                "local v = redis.call('lindex', KEYS[1], ARGV[1]); " +
                        "redis.call('lset', KEYS[1], ARGV[1], ARGV[2]); " +
                        "return v",
                Collections.<Object>singletonList(getName()), index, encode(element));
    }

    @Override
    public Publisher<Void> fastSet(long index, V element) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.LSET, getName(), index, encode(element));
    }

    @Override
    public Publisher<Integer> add(long index, V element) {
        return addAll(index, Collections.singleton(element));
    }

    @Override
    public Publisher<V> remove(final long index) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.removeAsync(index);
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
    public Publisher<Long> indexOf(final Object o) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return ((RedissonList)instance).indexOfAsync(o, new LongReplayConvertor());
            }
        });
    }

    @Override
    public Publisher<Long> lastIndexOf(final Object o) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return ((RedissonList)instance).lastIndexOfAsync(o, new LongReplayConvertor());
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof RedissonListReactive))
            return false;

        Flux<Object> e1 = Flux.from((Publisher<Object>)iterator());
        Flux<Object> e2 = Flux.from(((RedissonListReactive<Object>) o).iterator());
        Long count = Flux.merge(e1, e2).groupBy(new Function<Object, Object>() {
            @Override
            public Object apply(Object t) {
                return t;
            }
        }).count().block();

        boolean res = count.intValue() == Mono.from(size()).block();
        res &= count.intValue() == Mono.from(((RedissonListReactive<Object>) o).size()).block();
        return res;
    }

    @Override
    public int hashCode() {
        Integer hash = Flux.from(iterator()).map(new Function<V, Integer>() {
            @Override
            public Integer apply(V t) {
                return t.hashCode();
            }
        }).reduce(1, new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer apply(Integer t, Integer u) {
                return 31*t + u;
            }
        }).block();

        if (hash == null) {
            return 1;
        }
        return hash;
    }

    @Override
    public Publisher<List<V>> readSorted(final SortOrder order) {
        return reactive(new Supplier<RFuture<List<V>>>() {
            @Override
            public RFuture<List<V>> get() {
                return instance.readSortAsync(order);
            }
        });
    }

    @Override
    public Publisher<List<V>> readSorted(final SortOrder order, final int offset, final int count) {
        return reactive(new Supplier<RFuture<List<V>>>() {
            @Override
            public RFuture<List<V>> get() {
                return instance.readSortAsync(order, offset, count);
            }
        });
    }

    @Override
    public Publisher<List<V>> readSorted(final String byPattern, final SortOrder order) {
        return reactive(new Supplier<RFuture<List<V>>>() {
            @Override
            public RFuture<List<V>> get() {
                return instance.readSortAsync(byPattern, order);
            }
        });
    }

    @Override
    public Publisher<List<V>> readSorted(final String byPattern, final SortOrder order, final int offset, final int count) {
        return reactive(new Supplier<RFuture<List<V>>>() {
            @Override
            public RFuture<List<V>> get() {
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
