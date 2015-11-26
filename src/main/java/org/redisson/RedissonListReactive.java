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
import static org.redisson.client.protocol.RedisCommands.LPUSH;
import static org.redisson.client.protocol.RedisCommands.LREM_SINGLE;
import static org.redisson.client.protocol.RedisCommands.RPUSH;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.BooleanNumberReplayConvertor;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.client.protocol.convertor.IntegerReplayConvertor;
import org.redisson.client.protocol.convertor.LongReplayConvertor;
import org.redisson.core.RListReactive;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Single;
import rx.SingleSubscriber;
import rx.Subscriber;
import rx.observables.ConnectableObservable;
import rx.subjects.PublishSubject;

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
    public Single<Long> size() {
        return commandExecutor.readObservable(getName(), codec, LLEN, getName());
    }

    @Override
    public Observable<V> descendingIterator() {
        return iterator(-1, false);
    }

    @Override
    public Observable<V> iterator() {
        return iterator(0, true);
    }

    @Override
    public Observable<V> descendingIterator(int startIndex) {
        return iterator(startIndex, false);
    }

    @Override
    public Observable<V> iterator(int startIndex) {
        return iterator(startIndex, true);
    }

    private Observable<V> iterator(final int startIndex, final boolean forward) {
        return Observable.create(new OnSubscribe<V>() {

            private int currentIndex = startIndex;

            @Override
            public void call(final Subscriber<? super V> t) {
                get(currentIndex).subscribe(new SingleSubscriber<V>() {

                    @Override
                    public void onError(Throwable e) {
                        t.onError(e);
                    }

                    @Override
                    public void onSuccess(V val) {
                        if (val == null) {
                            t.onCompleted();
                            return;
                        }
                        t.onNext(val);
                        if (forward) {
                            currentIndex++;
                        } else {
                            currentIndex--;
                        }
                        call(t);
                    }
                });
            }

        });
    }

    @Override
    public Single<Long> add(V e) {
        return commandExecutor.writeObservable(getName(), codec, RPUSH, getName(), e);
    }

    @Override
    public Single<Boolean> remove(Object o) {
        return remove(o, 1);
    }

    protected Single<Boolean> remove(Object o, int count) {
        return commandExecutor.writeObservable(getName(), codec, LREM_SINGLE, getName(), count, o);
    }

    @Override
    public Single<Boolean> containsAll(Collection<?> c) {
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
    public Single<Long> addAll(final Collection<? extends V> c) {
        if (c.isEmpty()) {
            return size();
        }

        final PublishSubject<Long> promise = newObservable();
        ConnectableObservable<Long> r = promise.replay();
        r.connect();
        Single<Long> sizeObservable = size();
        sizeObservable.subscribe(new SingleSubscriber<Long>() {
            @Override
            public void onSuccess(final Long listSize) {
                List<Object> args = new ArrayList<Object>(c.size() + 1);
                args.add(getName());
                args.addAll(c);
                Single<Long> res = commandExecutor.writeObservable(getName(), codec, RPUSH, args.toArray());
                res.subscribe(new SingleSubscriber<Long>() {

                    @Override
                    public void onSuccess(Long value) {
                        promise.onNext(value);
                        promise.onCompleted();
                    }

                    @Override
                    public void onError(Throwable error) {
                        promise.onError(error);
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                promise.onError(error);
            }

        });
        return r.toSingle();
    }

    @Override
    public Single<Long> addAll(final long index, final Collection<? extends V> coll) {
        if (coll.isEmpty()) {
            return size();
        }

        if (index == 0) { // prepend elements to list
            List<Object> elements = new ArrayList<Object>(coll);
            Collections.reverse(elements);
            elements.add(0, getName());

            return commandExecutor.writeObservable(getName(), codec, LPUSH, elements.toArray());
        }

        final PublishSubject<Long> promise = newObservable();

        Single<Long> s = size();
        s.subscribe(new SingleSubscriber<Long>() {
            @Override
            public void onSuccess(Long size) {
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
                Single<Long> f = commandExecutor.evalWriteObservable(getName(), codec, new RedisCommand<Long>("EVAL", new LongReplayConvertor(), 5),
                        "local ind = table.remove(ARGV, 1); " + // index is the first parameter
                                "local tail = redis.call('lrange', KEYS[1], ind, -1); " +
                                "redis.call('ltrim', KEYS[1], 0, ind - 1); " +
                                "for i, v in ipairs(ARGV) do redis.call('rpush', KEYS[1], v) end;" +
                                "for i, v in ipairs(tail) do redis.call('rpush', KEYS[1], v) end;" +
                                "return redis.call('llen', KEYS[1]);",
                        Collections.<Object>singletonList(getName()), args.toArray());
                f.subscribe(toSubscriber(promise));
            }

            @Override
            public void onError(Throwable error) {
                promise.onError(error);
            }
        });
        return promise.toSingle();
    }

    @Override
    public Single<Boolean> removeAll(Collection<?> c) {
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
    public Single<Boolean> retainAll(Collection<?> c) {
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
    public Single<V> get(long index) {
        return commandExecutor.readObservable(getName(), codec, LINDEX, getName(), index);
    }

    private boolean isPositionInRange(long index, long size) {
        return index >= 0 && index <= size;
    }

    @Override
    public Single<V> set(long index, V element) {
        return commandExecutor.evalWriteObservable(getName(), codec, new RedisCommand<Object>("EVAL", 5),
                "local v = redis.call('lindex', KEYS[1], ARGV[1]); " +
                        "redis.call('lset', KEYS[1], ARGV[1], ARGV[2]); " +
                        "return v",
                Collections.<Object>singletonList(getName()), index, element);
    }

    @Override
    public Single<Void> fastSet(long index, V element) {
        return commandExecutor.writeObservable(getName(), codec, RedisCommands.LSET, getName(), index, element);
    }

    @Override
    public Single<Long> add(long index, V element) {
        return addAll(index, Collections.singleton(element));
    }

    @Override
    public Single<V> remove(int index) {
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
    public Single<Boolean> contains(Object o) {
        return indexOf(o, new BooleanNumberReplayConvertor());
    }

    private <R> Single<R> indexOf(Object o, Convertor<R> convertor) {
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
    public Single<Integer> indexOf(Object o) {
        return indexOf(o, new IntegerReplayConvertor());
    }

    @Override
    public Single<Integer> lastIndexOf(Object o) {
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
