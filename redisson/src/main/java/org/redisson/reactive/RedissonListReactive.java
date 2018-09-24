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

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.RedissonList;
import org.redisson.RedissonObject;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.LongReplayConvertor;
import org.redisson.command.CommandReactiveExecutor;

import reactor.fn.Supplier;
import reactor.rx.Stream;
import reactor.rx.subscription.ReactiveSubscription;

/**
 * Distributed and concurrent implementation of {@link java.util.List}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonListReactive<V> {

    private final RedissonList<V> instance;
    private final CommandReactiveExecutor commandExecutor;

    public RedissonListReactive(CommandReactiveExecutor commandExecutor, String name) {
        this.commandExecutor = commandExecutor;
        this.instance = new RedissonList<V>(commandExecutor, name, null);
    }

    public RedissonListReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        this.commandExecutor = commandExecutor;
        this.instance = new RedissonList<V>(codec, commandExecutor, name, null);
    }
    
    public Publisher<V> descendingIterator() {
        return iterator(-1, false);
    }

    public Publisher<V> iterator() {
        return iterator(0, true);
    }

    public Publisher<V> descendingIterator(int startIndex) {
        return iterator(startIndex, false);
    }

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
    
    public Publisher<Integer> add(V e) {
        return commandExecutor.writeReactive(instance.getName(), instance.getCodec(), RPUSH, instance.getName(), ((RedissonObject)instance).encode(e));
    }

    protected Publisher<Boolean> remove(Object o, int count) {
        return commandExecutor.writeReactive(instance.getName(), instance.getCodec(), LREM_SINGLE, instance.getName(), count, ((RedissonObject)instance).encode(o));
    }
    
    public Publisher<Integer> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<V>() {

            @Override
            public Integer sum(Integer first, Integer second) {
                return second;
            }

            @Override
            public Publisher<Integer> add(Object o) {
                return RedissonListReactive.this.add((V)o);
            }

        }.addAll(c);
    }

    public Publisher<Integer> addAll(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return commandExecutor.reactive(new Supplier<RFuture<Integer>>() {
                @Override
                public RFuture<Integer> get() {
                    return instance.sizeAsync();
                }
            });
        }

        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(instance.getName());
        ((RedissonObject)instance).encode(args, c);
        return commandExecutor.writeReactive(instance.getName(), instance.getCodec(), RPUSH, args.toArray());
    }

    public Publisher<Integer> addAll(long index, Collection<? extends V> coll) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("index: " + index);
        }

        if (coll.isEmpty()) {
            return commandExecutor.reactive(new Supplier<RFuture<Integer>>() {
                @Override
                public RFuture<Integer> get() {
                    return instance.sizeAsync();
                }
            });
        }

        if (index == 0) { // prepend elements to list
            List<Object> elements = new ArrayList<Object>();
            ((RedissonObject)instance).encode(elements, coll);
            Collections.reverse(elements);
            elements.add(0, instance.getName());

            return commandExecutor.writeReactive(instance.getName(), instance.getCodec(), RedisCommands.LPUSH, elements.toArray());
        }

        List<Object> args = new ArrayList<Object>(coll.size() + 1);
        args.add(index);
        ((RedissonObject)instance).encode(args, coll);
        return commandExecutor.evalWriteReactive(instance.getName(), instance.getCodec(), RedisCommands.EVAL_INTEGER,
                "local ind = table.remove(ARGV, 1); " + // index is the first parameter
                        "local size = redis.call('llen', KEYS[1]); " +
                        "assert(tonumber(ind) <= size, 'index: ' .. ind .. ' but current size: ' .. size); " +
                        "local tail = redis.call('lrange', KEYS[1], ind, -1); " +
                        "redis.call('ltrim', KEYS[1], 0, ind - 1); " +
                        "for i, v in ipairs(ARGV) do redis.call('rpush', KEYS[1], v) end;" +
                        "for i, v in ipairs(tail) do redis.call('rpush', KEYS[1], v) end;" +
                        "return redis.call('llen', KEYS[1]);",
                Collections.<Object>singletonList(instance.getName()), args.toArray());
    }

    public Publisher<V> get(long index) {
        return commandExecutor.readReactive(instance.getName(), instance.getCodec(), LINDEX, instance.getName(), index);
    }

    public Publisher<V> set(long index, V element) {
        return commandExecutor.evalWriteReactive(instance.getName(), instance.getCodec(), RedisCommands.EVAL_OBJECT,
                "local v = redis.call('lindex', KEYS[1], ARGV[1]); " +
                        "redis.call('lset', KEYS[1], ARGV[1], ARGV[2]); " +
                        "return v",
                Collections.<Object>singletonList(instance.getName()), index, ((RedissonObject)instance).encode(element));
    }

    public Publisher<Void> fastSet(long index, V element) {
        return commandExecutor.writeReactive(instance.getName(), instance.getCodec(), RedisCommands.LSET, instance.getName(), index, ((RedissonObject)instance).encode(element));
    }

    public Publisher<Integer> add(long index, V element) {
        return addAll(index, Collections.singleton(element));
    }

    public Publisher<Long> indexOf(final Object o) {
        return commandExecutor.reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return ((RedissonList)instance).indexOfAsync(o, new LongReplayConvertor());
            }
        });
    }

    public Publisher<Long> lastIndexOf(final Object o) {
        return commandExecutor.reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return ((RedissonList)instance).lastIndexOfAsync(o, new LongReplayConvertor());
            }
        });
    }

}
