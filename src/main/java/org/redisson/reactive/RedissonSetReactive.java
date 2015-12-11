/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.api.RSetReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandReactiveExecutor;

import reactor.core.reactivestreams.SubscriberBarrier;
import reactor.fn.BiFunction;
import reactor.fn.Function;
import reactor.rx.Stream;

/**
 * Distributed and concurrent implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSetReactive<V> extends RedissonCollectionReactive<V> implements RSetReactive<V> {

    public RedissonSetReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    public RedissonSetReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    @Override
    public Publisher<Long> addAll(Publisher<? extends V> c) {
        return addAll(c, new Function<V, Publisher<Long>>() {
            @Override
            public Publisher<Long> apply(V o) {
                return add(o);
            }
        }, new BiFunction<Long, Long, Long>() {
            @Override
            public Long apply(Long left, Long right) {
                return left + right;
            }
        });
    }

    @Override
    public Publisher<Long> size() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.SCARD, getName());
    }

    @Override
    public Publisher<Boolean> contains(Object o) {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.SISMEMBER, getName(), o);
    }

    private Publisher<ListScanResult<V>> scanIteratorReactive(InetSocketAddress client, long startPos) {
        return commandExecutor.readReactive(client, getName(), codec, RedisCommands.SSCAN, getName(), startPos);
    }

    @Override
    public Publisher<Long> add(V e) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SADD, getName(), e);
    }

    @Override
    public Publisher<V> removeRandom() {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SPOP_SINGLE, getName());
    }

    @Override
    public Publisher<Boolean> remove(Object o) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SREM_SINGLE, getName(), o);
    }

    @Override
    public Publisher<Boolean> containsAll(Collection<?> c) {
        return commandExecutor.evalReadReactive(getName(), codec, RedisCommands.EVAL_BOOLEAN_WITH_VALUES,
                "local s = redis.call('smembers', KEYS[1]);" +
                        "for i = 0, table.getn(s), 1 do " +
                            "for j = 0, table.getn(ARGV), 1 do "
                            + "if ARGV[j] == s[i] "
                            + "then table.remove(ARGV, j) end "
                        + "end; "
                       + "end;"
                       + "return table.getn(ARGV) == 0 and 1 or 0; ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public Publisher<Long> addAll(Collection<? extends V> c) {
        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getName());
        args.addAll(c);
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SADD, args.toArray());
    }

    @Override
    public Publisher<Boolean> retainAll(Collection<?> c) {
        return commandExecutor.evalWriteReactive(getName(), codec, RedisCommands.EVAL_BOOLEAN_WITH_VALUES,
                    "local changed = 0 " +
                    "local s = redis.call('smembers', KEYS[1]) "
                       + "local i = 0 "
                       + "while i <= table.getn(s) do "
                            + "local element = s[i] "
                            + "local isInAgrs = false "
                            + "for j = 0, table.getn(ARGV), 1 do "
                                + "if ARGV[j] == element then "
                                    + "isInAgrs = true "
                                    + "break "
                                + "end "
                            + "end "
                            + "if isInAgrs == false then "
                                + "redis.call('SREM', KEYS[1], element) "
                                + "changed = 1 "
                            + "end "
                            + "i = i + 1 "
                       + "end "
                       + "return changed ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public Publisher<Boolean> removeAll(Collection<?> c) {
        return commandExecutor.evalWriteReactive(getName(), codec, RedisCommands.EVAL_BOOLEAN_WITH_VALUES,
                        "local v = 0 " +
                        "for i = 0, table.getn(ARGV), 1 do "
                            + "if redis.call('srem', KEYS[1], ARGV[i]) == 1 "
                            + "then v = 1 end "
                        +"end "
                       + "return v ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public Publisher<V> iterator() {
        return new Stream<V>() {

            @Override
            public void subscribe(final Subscriber<? super V> t) {
                t.onSubscribe(new SubscriberBarrier<V, V>(t) {

                    private List<V> firstValues;
                    private long nextIterPos;
                    private InetSocketAddress client;

                    private long currentIndex;
                    private List<V> prevValues = new ArrayList<V>();

                    @Override
                    protected void doRequest(long n) {
                        currentIndex = n;

                        if (!prevValues.isEmpty()) {
                            List<V> vals = new ArrayList<V>(prevValues);
                            prevValues.clear();

                            handle(vals);

                            if (currentIndex == 0) {
                                return;
                            }
                        }

                        nextValues();
                    }

                    private void handle(List<V> vals) {
                        for (V val : vals) {
                            if (currentIndex > 0) {
                                onNext(val);
                            } else {
                                prevValues.add(val);
                            }
                            currentIndex--;
                            if (currentIndex == 0) {
                                onComplete();
                            }
                        }
                    }

                    protected void nextValues() {
                        final SubscriberBarrier<V, V> m = this;
                        scanIteratorReactive(client, nextIterPos).subscribe(new Subscriber<ListScanResult<V>>() {

                            @Override
                            public void onSubscribe(Subscription s) {
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(ListScanResult<V> res) {
                                client = res.getRedisClient();

                                long prevIterPos = nextIterPos;
                                if (nextIterPos == 0 && firstValues == null) {
                                    firstValues = res.getValues();
                                } else if (res.getValues().equals(firstValues)) {
                                    m.onComplete();
                                    currentIndex = 0;
                                    return;
                                }

                                nextIterPos = res.getPos();
                                if (prevIterPos == nextIterPos) {
                                    nextIterPos = -1;
                                }

                                handle(res.getValues());

                                if (currentIndex == 0) {
                                    return;
                                }

                                if (nextIterPos == -1) {
                                    m.onComplete();
                                    currentIndex = 0;
                                }
                            }

                            @Override
                            public void onError(Throwable error) {
                                m.onError(error);
                            }

                            @Override
                            public void onComplete() {
                                if (currentIndex == 0) {
                                    return;
                                }
                                nextValues();
                            }
                        });
                    }
                });
            }

        };
    }

}
