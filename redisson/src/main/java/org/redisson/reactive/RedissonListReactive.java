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

import static org.redisson.client.protocol.RedisCommands.LINDEX;
import static org.redisson.client.protocol.RedisCommands.LREM_SINGLE;
import static org.redisson.client.protocol.RedisCommands.RPUSH;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.RedissonList;
import org.redisson.api.RFuture;
import org.redisson.api.RListReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.LongReplayConvertor;
import org.redisson.command.CommandReactiveExecutor;

import reactor.fn.BiFunction;
import reactor.fn.Function;
import reactor.fn.Supplier;
import reactor.rx.Stream;
import reactor.rx.Streams;
import reactor.rx.subscription.ReactiveSubscription;

/**
 * Distributed and concurrent implementation of {@link java.util.List}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonListReactive<V> extends RedissonExpirableReactive implements RListReactive<V> {

    private final RedissonList<V> instance;

    public RedissonListReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        instance = new RedissonList<V>(commandExecutor, name, null);
    }

    public RedissonListReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        instance = new RedissonList<V>(codec, commandExecutor, name, null);
    }

    @Override
    public Publisher<Integer> size() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.LLEN_INT, getName());
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
        return new Stream<V>() {

            @Override
            public void subscribe(final Subscriber<? super V> t) {
                t.onSubscribe(new ReactiveSubscription<V>(this, t) {

                    private int currentIndex = startIndex;

                    @Override
                    protected void onRequest(final long n) {
                        final ReactiveSubscription<V> m = this;
                        get(currentIndex).subscribe(new Subscriber<V>() {
                            V currValue;

                            @Override
                            public void onSubscribe(Subscription s) {
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(V value) {
                                currValue = value;
                                m.onNext(value);
                                if (forward) {
                                    currentIndex++;
                                } else {
                                    currentIndex--;
                                }
                            }

                            @Override
                            public void onError(Throwable error) {
                                m.onError(error);
                            }

                            @Override
                            public void onComplete() {
                                if (currValue == null) {
                                    m.onComplete();
                                    return;
                                }
                                if (n-1 == 0) {
                                    return;
                                }
                                onRequest(n-1);
                            }
                        });
                    }
                });
            }

        };
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
                return instance.indexOfAsync(o, new LongReplayConvertor());
            }
        });
    }

    @Override
    public Publisher<Long> lastIndexOf(final Object o) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.lastIndexOfAsync(o, new LongReplayConvertor());
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof RedissonListReactive))
            return false;

        Stream<Object> e1 = Streams.wrap((Publisher<Object>)iterator());
        Stream<Object> e2 = Streams.wrap(((RedissonListReactive<Object>) o).iterator());
        Long count = Streams.merge(e1, e2).groupBy(new Function<Object, Object>() {
            @Override
            public Object apply(Object t) {
                return t;
            }
        }).count().next().poll();

        boolean res = count.intValue() == Streams.wrap(size()).next().poll();
        res &= count.intValue() == Streams.wrap(((RedissonListReactive<Object>) o).size()).next().poll();
        return res;
    }

    @Override
    public int hashCode() {
        Integer hash = Streams.wrap(iterator()).map(new Function<V, Integer>() {
            @Override
            public Integer apply(V t) {
                return t.hashCode();
            }
        }).reduce(1, new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer apply(Integer t, Integer u) {
                return 31*t + u;
            }
        }).next().poll();

        if (hash == null) {
            return 1;
        }
        return hash;
    }

}
