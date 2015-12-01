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
package org.redisson;

import static org.redisson.client.protocol.RedisCommands.EVAL_OBJECT;
import static org.redisson.client.protocol.RedisCommands.LINDEX;
import static org.redisson.client.protocol.RedisCommands.LLEN;
import static org.redisson.client.protocol.RedisCommands.LPOP;
import static org.redisson.client.protocol.RedisCommands.LREM_SINGLE;
import static org.redisson.client.protocol.RedisCommands.RPUSH;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.BooleanNumberReplayConvertor;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.client.protocol.convertor.IntegerReplayConvertor;
import org.redisson.client.protocol.convertor.LongReplayConvertor;
import org.redisson.command.CommandReactiveExecutor;
import org.redisson.core.RListReactive;

import reactor.core.reactivestreams.SubscriberBarrier;
import reactor.rx.Stream;
import reactor.rx.subscription.ReactiveSubscription;

/**
 * Distributed and concurrent implementation of {@link java.util.List}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonListReactive<V> extends RedissonExpirableReactive implements RListReactive<V> {

    protected RedissonListReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    protected RedissonListReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    @Override
    public Publisher<Long> size() {
        return commandExecutor.readObservable(getName(), codec, LLEN, getName());
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
    public Publisher<Long> add(V e) {
        return commandExecutor.writeObservable(getName(), codec, RPUSH, getName(), e);
    }

    @Override
    public Publisher<Boolean> remove(Object o) {
        return remove(o, 1);
    }

    protected Publisher<Boolean> remove(Object o, int count) {
        return commandExecutor.writeObservable(getName(), codec, LREM_SINGLE, getName(), count, o);
    }

    @Override
    public Publisher<Boolean> containsAll(Collection<?> c) {
        return commandExecutor.evalReadObservable(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4),
                "local items = redis.call('lrange', KEYS[1], 0, -1) " +
                "for i=1, #items do " +
                    "for j = 0, table.getn(ARGV), 1 do " +
                        "if items[i] == ARGV[j] then " +
                            "table.remove(ARGV, j) " +
                        "end " +
                    "end " +
                "end " +
                "return table.getn(ARGV) == 0",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public Publisher<Long> addAll(final Collection<? extends V> c) {
        if (c.isEmpty()) {
            return size();
        }

        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getName());
        args.addAll(c);
        return commandExecutor.writeObservable(getName(), codec, RPUSH, args.toArray());
    }

    @Override
    public Publisher<Long> addAll(final long index, final Collection<? extends V> coll) {
        if (coll.isEmpty()) {
            return size();
        }

        if (index == 0) { // prepend elements to list
            List<Object> elements = new ArrayList<Object>(coll);
            Collections.reverse(elements);
            elements.add(0, getName());

            return commandExecutor.writeObservable(getName(), codec, RedisCommands.LPUSH, elements.toArray());
        }

        final Processor<Long, Long> promise = newObservable();

        Publisher<Long> s = size();
        s.subscribe(new SubscriberBarrier<Long, Long>(promise) {
            @Override
            public void doNext(Long size) {
                if (!isPositionInRange(index, size)) {
                    IndexOutOfBoundsException e = new IndexOutOfBoundsException("index: " + index + " but current size: "+ size);
                    promise.onError(e);
                    return;
                }

                if (index >= size) {
                    addAll(coll).subscribe(toSubscriber(promise));
                    return;
                }

                // insert into middle of list

                List<Object> args = new ArrayList<Object>(coll.size() + 1);
                args.add(index);
                args.addAll(coll);
                Publisher<Long> f = commandExecutor.evalWriteObservable(getName(), codec, new RedisCommand<Long>("EVAL", new LongReplayConvertor(), 5),
                        "local ind = table.remove(ARGV, 1); " + // index is the first parameter
                                "local tail = redis.call('lrange', KEYS[1], ind, -1); " +
                                "redis.call('ltrim', KEYS[1], 0, ind - 1); " +
                                "for i, v in ipairs(ARGV) do redis.call('rpush', KEYS[1], v) end;" +
                                "for i, v in ipairs(tail) do redis.call('rpush', KEYS[1], v) end;" +
                                "return redis.call('llen', KEYS[1]);",
                        Collections.<Object>singletonList(getName()), args.toArray());
                f.subscribe(toSubscriber(promise));
            }

        });
        return promise;
    }

    @Override
    public Publisher<Boolean> removeAll(Collection<?> c) {
        return commandExecutor.evalWriteObservable(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4),
                        "local v = false " +
                        "for i = 0, table.getn(ARGV), 1 do "
                            + "if redis.call('lrem', KEYS[1], 0, ARGV[i]) == 1 "
                            + "then v = true end "
                        +"end "
                       + "return v ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public Publisher<Boolean> retainAll(Collection<?> c) {
        return commandExecutor.evalWriteObservable(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4),
                "local changed = false " +
                "local items = redis.call('lrange', KEYS[1], 0, -1) "
                   + "local i = 1 "
                   + "local s = table.getn(items) "
                   + "while i <= s do "
                        + "local element = items[i] "
                        + "local isInAgrs = false "
                        + "for j = 0, table.getn(ARGV), 1 do "
                            + "if ARGV[j] == element then "
                                + "isInAgrs = true "
                                + "break "
                            + "end "
                        + "end "
                        + "if isInAgrs == false then "
                            + "redis.call('LREM', KEYS[1], 0, element) "
                            + "changed = true "
                        + "end "
                        + "i = i + 1 "
                   + "end "
                   + "return changed ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public Publisher<V> get(long index) {
        return commandExecutor.readObservable(getName(), codec, LINDEX, getName(), index);
    }

    private boolean isPositionInRange(long index, long size) {
        return index >= 0 && index <= size;
    }

    @Override
    public Publisher<V> set(long index, V element) {
        return commandExecutor.evalWriteObservable(getName(), codec, new RedisCommand<Object>("EVAL", 5),
                "local v = redis.call('lindex', KEYS[1], ARGV[1]); " +
                        "redis.call('lset', KEYS[1], ARGV[1], ARGV[2]); " +
                        "return v",
                Collections.<Object>singletonList(getName()), index, element);
    }

    @Override
    public Publisher<Void> fastSet(long index, V element) {
        return commandExecutor.writeObservable(getName(), codec, RedisCommands.LSET, getName(), index, element);
    }

    @Override
    public Publisher<Long> add(long index, V element) {
        return addAll(index, Collections.singleton(element));
    }

    @Override
    public Publisher<V> remove(int index) {
        if (index == 0) {
            return commandExecutor.writeObservable(getName(), codec, LPOP, getName());
        }

        return commandExecutor.evalWriteObservable(getName(), codec, EVAL_OBJECT,
                "local v = redis.call('lindex', KEYS[1], ARGV[1]); " +
                        "local tail = redis.call('lrange', KEYS[1], ARGV[1]);" +
                        "redis.call('ltrim', KEYS[1], 0, ARGV[1] - 1);" +
                        "for i, v in ipairs(tail) do redis.call('rpush', KEYS[1], v) end;" +
                        "return v",
                Collections.<Object>singletonList(getName()), index);
    }

    @Override
    public Publisher<Boolean> contains(Object o) {
        return indexOf(o, new BooleanNumberReplayConvertor());
    }

    private <R> Publisher<R> indexOf(Object o, Convertor<R> convertor) {
        return commandExecutor.evalReadObservable(getName(), codec, new RedisCommand<R>("EVAL", convertor, 4),
                "local key = KEYS[1] " +
                "local obj = ARGV[1] " +
                "local items = redis.call('lrange', key, 0, -1) " +
                "for i=1,#items do " +
                    "if items[i] == obj then " +
                        "return i - 1 " +
                    "end " +
                "end " +
                "return -1",
                Collections.<Object>singletonList(getName()), o);
    }

    @Override
    public Publisher<Integer> indexOf(Object o) {
        return indexOf(o, new IntegerReplayConvertor());
    }

    @Override
    public Publisher<Integer> lastIndexOf(Object o) {
        return commandExecutor.evalReadObservable(getName(), codec, new RedisCommand<Integer>("EVAL", new IntegerReplayConvertor(), 4),
                "local key = KEYS[1] " +
                "local obj = ARGV[1] " +
                "local items = redis.call('lrange', key, 0, -1) " +
                "for i = table.getn(items), 0, -1 do " +
                    "if items[i] == obj then " +
                        "return i - 1 " +
                    "end " +
                "end " +
                "return -1",
                Collections.<Object>singletonList(getName()), o);
    }

}
