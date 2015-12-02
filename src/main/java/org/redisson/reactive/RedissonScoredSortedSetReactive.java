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

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.api.RScoredSortedSetReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.ScoredEntry;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandReactiveExecutor;

import reactor.core.reactivestreams.SubscriberBarrier;
import reactor.rx.Stream;
import reactor.rx.subscription.ReactiveSubscription;

public class RedissonScoredSortedSetReactive<V> extends RedissonCollectionReactive<V> implements RScoredSortedSetReactive<V> {

    public RedissonScoredSortedSetReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    public RedissonScoredSortedSetReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    public Publisher<V> first() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZRANGE_SINGLE, getName(), 0, 0);
    }

    public Publisher<V> last() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZRANGE_SINGLE, getName(), -1, -1);
    }

    @Override
    public Publisher<Boolean> add(double score, V object) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.ZADD_BOOL, getName(), BigDecimal.valueOf(score).toPlainString(), object);
    }

    public Publisher<Integer> removeRangeByRank(int startIndex, int endIndex) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.ZREMRANGEBYRANK, getName(), startIndex, endIndex);
    }

    public Publisher<Integer> removeRangeByScore(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        String startValue = value(BigDecimal.valueOf(startScore).toPlainString(), startScoreInclusive);
        String endValue = value(BigDecimal.valueOf(endScore).toPlainString(), endScoreInclusive);
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.ZREMRANGEBYSCORE, getName(), startValue, endValue);
    }

    private String value(String element, boolean inclusive) {
        if (!inclusive) {
            element = "(" + element;
        }
        return element;
    }

    @Override
    public Publisher<Boolean> remove(Object object) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.ZREM, getName(), object);
    }

    @Override
    public Publisher<Long> size() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZCARD, getName());
    }

    @Override
    public Publisher<Boolean> contains(Object o) {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZSCORE_CONTAINS, getName(), o);
    }

    @Override
    public Publisher<Double> getScore(V o) {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZSCORE, getName(), o);
    }

    @Override
    public Publisher<Long> rank(V o) {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZRANK, getName(), o);
    }

    private Publisher<ListScanResult<V>> scanIteratorReactive(InetSocketAddress client, long startPos) {
        return commandExecutor.readReactive(client, getName(), codec, RedisCommands.ZSCAN, getName(), startPos);
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

    @Override
    public Publisher<Boolean> containsAll(Collection<?> c) {
        return commandExecutor.evalReadReactive(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4),
                "local s = redis.call('zrange', KEYS[1], 0, -1);" +
                        "for i = 0, table.getn(s), 1 do " +
                            "for j = 0, table.getn(ARGV), 1 do "
                            + "if ARGV[j] == s[i] "
                            + "then table.remove(ARGV, j) end "
                        + "end; "
                       + "end;"
                       + "return table.getn(ARGV) == 0; ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public Publisher<Boolean> removeAll(Collection<?> c) {
        return commandExecutor.evalWriteReactive(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4),
                        "local v = false " +
                        "for i = 0, table.getn(ARGV), 1 do "
                            + "if redis.call('zrem', KEYS[1], ARGV[i]) == 1 "
                            + "then v = true end "
                        +"end "
                       + "return v ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public Publisher<Boolean> retainAll(Collection<?> c) {
        return commandExecutor.evalWriteReactive(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4),
                    "local changed = false " +
                    "local s = redis.call('zrange', KEYS[1], 0, -1) "
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
                                + "redis.call('zrem', KEYS[1], element) "
                                + "changed = true "
                            + "end "
                            + "i = i + 1 "
                       + "end "
                       + "return changed ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public Publisher<Double> addScore(V object, Number value) {
        return commandExecutor.writeReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZINCRBY,
                                   getName(), new BigDecimal(value.toString()).toPlainString(), object);
    }

    @Override
    public Publisher<Collection<V>> valueRange(int startIndex, int endIndex) {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZRANGE, getName(), startIndex, endIndex);
    }

    @Override
    public Publisher<Collection<ScoredEntry<V>>> entryRange(int startIndex, int endIndex) {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZRANGE_ENTRY, getName(), startIndex, endIndex, "WITHSCORES");
    }

    @Override
    public Publisher<Collection<V>> valueRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        String startValue = value(BigDecimal.valueOf(startScore).toPlainString(), startScoreInclusive);
        String endValue = value(BigDecimal.valueOf(endScore).toPlainString(), endScoreInclusive);
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZRANGEBYSCORE, getName(), startValue, endValue);
    }

    @Override
    public Publisher<Collection<ScoredEntry<V>>> entryRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        String startValue = value(BigDecimal.valueOf(startScore).toPlainString(), startScoreInclusive);
        String endValue = value(BigDecimal.valueOf(endScore).toPlainString(), endScoreInclusive);
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZRANGEBYSCORE_ENTRY, getName(), startValue, endValue, "WITHSCORES");
    }

    @Override
    public Publisher<Collection<V>> valueRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count) {
        String startValue = value(BigDecimal.valueOf(startScore).toPlainString(), startScoreInclusive);
        String endValue = value(BigDecimal.valueOf(endScore).toPlainString(), endScoreInclusive);
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZRANGEBYSCORE, getName(), startValue, endValue, "LIMIT", offset, count);
    }

    @Override
    public Publisher<Collection<ScoredEntry<V>>> entryRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count) {
        String startValue = value(BigDecimal.valueOf(startScore).toPlainString(), startScoreInclusive);
        String endValue = value(BigDecimal.valueOf(endScore).toPlainString(), endScoreInclusive);
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZRANGEBYSCORE_ENTRY, getName(), startValue, endValue, "WITHSCORES", "LIMIT", offset, count);
    }

}
