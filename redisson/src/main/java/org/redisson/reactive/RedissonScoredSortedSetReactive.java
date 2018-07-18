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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;
import org.redisson.RedissonScoredSortedSet;
import org.redisson.api.RFuture;
import org.redisson.api.RScoredSortedSet.Aggregate;
import org.redisson.api.RScoredSortedSetAsync;
import org.redisson.api.RScoredSortedSetReactive;
import org.redisson.api.SortOrder;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.ScoredEntry;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandReactiveExecutor;

import reactor.core.publisher.Flux;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonScoredSortedSetReactive<V> extends RedissonExpirableReactive implements RScoredSortedSetReactive<V> {

    private final RScoredSortedSetAsync<V> instance;
    
    public RedissonScoredSortedSetReactive(CommandReactiveExecutor commandExecutor, String name) {
        this(commandExecutor, name, new RedissonScoredSortedSet<V>(commandExecutor, name, null));
    }

    public RedissonScoredSortedSetReactive(CommandReactiveExecutor commandExecutor, String name, RScoredSortedSetAsync<V> instance) {
        super(commandExecutor, name, instance);
        this.instance = instance;
    }
    
    public RedissonScoredSortedSetReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        this(codec, commandExecutor, name, new RedissonScoredSortedSet<V>(codec, commandExecutor, name, null));
    }

    public RedissonScoredSortedSetReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name, RScoredSortedSetAsync<V> instance) {
        super(codec, commandExecutor, name, instance);
        this.instance = instance;
    }

    @Override
    public Publisher<V> pollFirst() {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollFirstAsync();
            }
        });
    }

    @Override
    public Publisher<V> pollLast() {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollLastAsync();
            }
        });
    }

    @Override
    public Publisher<V> first() {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.firstAsync();
            }
        });
    }

    @Override
    public Publisher<V> last() {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.lastAsync();
            }
        });
    }

    @Override
    public Publisher<Boolean> add(final double score, final V object) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.addAsync(score, object);
            }
        });
    }

    @Override
    public Publisher<Integer> removeRangeByRank(final int startIndex, final int endIndex) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.removeRangeByRankAsync(startIndex, endIndex);
            }
        });
    }

    @Override
    public Publisher<Integer> removeRangeByScore(final double startScore, final boolean startScoreInclusive, final double endScore, final boolean endScoreInclusive) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.removeRangeByScoreAsync(startScore, startScoreInclusive, endScore, endScoreInclusive);
            }
        });
    }

    @Override
    public Publisher<Boolean> remove(final V object) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.removeAsync(object);
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
    public Publisher<Boolean> contains(final V o) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.containsAsync(o);
            }
        });
    }

    @Override
    public Publisher<Double> getScore(final V o) {
        return reactive(new Supplier<RFuture<Double>>() {
            @Override
            public RFuture<Double> get() {
                return instance.getScoreAsync(o);
            }
        });
    }

    @Override
    public Publisher<Integer> rank(final V o) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.rankAsync(o);
            }
        });
    }

    private Publisher<V> scanIteratorReactive(final String pattern, final int count) {
        return Flux.create(new SetReactiveIterator<V>() {
            @Override
            protected Publisher<ListScanResult<Object>> scanIteratorReactive(final RedisClient client, final long nextIterPos) {
                return reactive(new Supplier<RFuture<ListScanResult<Object>>>() {
                    @Override
                    public RFuture<ListScanResult<Object>> get() {
                        return ((RedissonScoredSortedSet<V>)instance).scanIteratorAsync(client, nextIterPos, pattern, count);
                    }
                });
            }
        });
    }

    @Override
    public Publisher<V> iterator() {
        return scanIteratorReactive(null, 10);
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
    public Publisher<Double> addScore(final V object, final Number value) {
        return reactive(new Supplier<RFuture<Double>>() {
            @Override
            public RFuture<Double> get() {
                return instance.addScoreAsync(object, value);
            }
        });
    }

    @Override
    public Publisher<Collection<V>> valueRange(final int startIndex, final int endIndex) {
        return reactive(new Supplier<RFuture<Collection<V>>>() {
            @Override
            public RFuture<Collection<V>> get() {
                return instance.valueRangeAsync(startIndex, endIndex);
            }
        });
    }

    @Override
    public Publisher<Collection<ScoredEntry<V>>> entryRange(final int startIndex, final int endIndex) {
        return reactive(new Supplier<RFuture<Collection<ScoredEntry<V>>>>() {
            @Override
            public RFuture<Collection<ScoredEntry<V>>> get() {
                return instance.entryRangeAsync(startIndex, endIndex);
            }
        });
    }

    @Override
    public Publisher<Collection<V>> valueRange(final double startScore, final boolean startScoreInclusive, final double endScore, final boolean endScoreInclusive) {
        return reactive(new Supplier<RFuture<Collection<V>>>() {
            @Override
            public RFuture<Collection<V>> get() {
                return instance.valueRangeAsync(startScore, startScoreInclusive, endScore, endScoreInclusive);
            }
        });
    }

    @Override
    public Publisher<Collection<ScoredEntry<V>>> entryRange(final double startScore, final boolean startScoreInclusive, final double endScore, final boolean endScoreInclusive) {
        return reactive(new Supplier<RFuture<Collection<ScoredEntry<V>>>>() {
            @Override
            public RFuture<Collection<ScoredEntry<V>>> get() {
                return instance.entryRangeAsync(startScore, startScoreInclusive, endScore, endScoreInclusive);
            }
        });
    }

    @Override
    public Publisher<Collection<V>> valueRange(final double startScore, final boolean startScoreInclusive, final double endScore, final boolean endScoreInclusive, final int offset, final int count) {
        return reactive(new Supplier<RFuture<Collection<V>>>() {
            @Override
            public RFuture<Collection<V>> get() {
                return instance.valueRangeAsync(startScore, startScoreInclusive, endScore, endScoreInclusive, offset, count);
            }
        });
    }

    @Override
    public Publisher<Collection<ScoredEntry<V>>> entryRange(final double startScore, final boolean startScoreInclusive, final double endScore, final boolean endScoreInclusive, final int offset, final int count) {
        return reactive(new Supplier<RFuture<Collection<ScoredEntry<V>>>>() {
            @Override
            public RFuture<Collection<ScoredEntry<V>>> get() {
                return instance.entryRangeAsync(startScore, startScoreInclusive, endScore, endScoreInclusive, offset, count);
            }
        });
    }

    @Override
    public Publisher<Long> count(final double startScore, final boolean startScoreInclusive, final double endScore,
            final boolean endScoreInclusive) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.countAsync(startScore, startScoreInclusive, endScore, endScoreInclusive);
            }
        });
    }

    @Override
    public Publisher<Collection<V>> readAll() {
        return reactive(new Supplier<RFuture<Collection<V>>>() {
            @Override
            public RFuture<Collection<V>> get() {
                return instance.readAllAsync();
            }
        });
    }

    @Override
    public Publisher<Integer> intersection(final String... names) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.intersectionAsync(names);
            }
        });
    }

    @Override
    public Publisher<Integer> intersection(final Aggregate aggregate, final String... names) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.intersectionAsync(aggregate, names);
            }
        });
    }

    @Override
    public Publisher<Integer> intersection(final Map<String, Double> nameWithWeight) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.intersectionAsync(nameWithWeight);
            }
        });
    }

    @Override
    public Publisher<Integer> intersection(final Aggregate aggregate, final Map<String, Double> nameWithWeight) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.intersectionAsync(aggregate, nameWithWeight);
            }
        });
    }

    @Override
    public Publisher<Integer> union(final String... names) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.unionAsync(names);
            }
        });
    }

    @Override
    public Publisher<Integer> union(final Aggregate aggregate, final String... names) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.unionAsync(aggregate, names);
            }
        });
    }

    @Override
    public Publisher<Integer> union(final Map<String, Double> nameWithWeight) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.unionAsync(nameWithWeight);
            }
        });
    }

    @Override
    public Publisher<Integer> union(final Aggregate aggregate, final Map<String, Double> nameWithWeight) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.unionAsync(aggregate, nameWithWeight);
            }
        });
    }

    @Override
    public Publisher<Collection<V>> valueRangeReversed(final int startIndex, final int endIndex) {
        return reactive(new Supplier<RFuture<Collection<V>>>() {
            @Override
            public RFuture<Collection<V>> get() {
                return instance.valueRangeReversedAsync(startIndex, endIndex);
            }
        });
    }

    @Override
    public Publisher<Collection<V>> valueRangeReversed(final double startScore, final boolean startScoreInclusive, final double endScore,
            final boolean endScoreInclusive) {
        return reactive(new Supplier<RFuture<Collection<V>>>() {
            @Override
            public RFuture<Collection<V>> get() {
                return instance.valueRangeReversedAsync(startScore, startScoreInclusive, endScore, endScoreInclusive);
            }
        });
    }

    @Override
    public Publisher<Collection<V>> valueRangeReversed(final double startScore, final boolean startScoreInclusive, final double endScore,
            final boolean endScoreInclusive, final int offset, final int count) {
        return reactive(new Supplier<RFuture<Collection<V>>>() {
            @Override
            public RFuture<Collection<V>> get() {
                return instance.valueRangeReversedAsync(startScore, startScoreInclusive, endScore, endScoreInclusive, offset, count);
            }
        });
    }

    @Override
    public Publisher<Collection<ScoredEntry<V>>> entryRangeReversed(final int startIndex, final int endIndex) {
        return reactive(new Supplier<RFuture<Collection<ScoredEntry<V>>>>() {
            @Override
            public RFuture<Collection<ScoredEntry<V>>> get() {
                return instance.entryRangeReversedAsync(startIndex, endIndex);
            }
        });
    }

    @Override
    public Publisher<Collection<ScoredEntry<V>>> entryRangeReversed(final double startScore, final boolean startScoreInclusive,
            final double endScore, final boolean endScoreInclusive) {
        return reactive(new Supplier<RFuture<Collection<ScoredEntry<V>>>>() {
            @Override
            public RFuture<Collection<ScoredEntry<V>>> get() {
                return instance.entryRangeReversedAsync(startScore, startScoreInclusive, endScore, endScoreInclusive);
            }
        });
    }

    @Override
    public Publisher<Collection<ScoredEntry<V>>> entryRangeReversed(final double startScore, final boolean startScoreInclusive,
            final double endScore, final boolean endScoreInclusive, final int offset, final int count) {
        return reactive(new Supplier<RFuture<Collection<ScoredEntry<V>>>>() {
            @Override
            public RFuture<Collection<ScoredEntry<V>>> get() {
                return instance.entryRangeReversedAsync(startScore, startScoreInclusive, endScore, endScoreInclusive, offset, count);
            }
        });
    }

    @Override
    public Publisher<V> pollLastFromAny(final long timeout, final TimeUnit unit, final String... queueNames) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollLastFromAnyAsync(timeout, unit, queueNames);
}
        });
    }

    @Override
    public Publisher<V> pollFirstFromAny(final long timeout, final TimeUnit unit, final String... queueNames) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollFirstFromAnyAsync(timeout, unit, queueNames);
            }
        });
    }

    @Override
    public Publisher<V> pollFirst(final long timeout, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollFirstAsync(timeout, unit);
            }
        });
    }

    @Override
    public Publisher<V> pollLast(final long timeout, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.pollLastAsync(timeout, unit);
            }
        });
    }

    @Override
    public Publisher<Collection<V>> pollFirst(final int count) {
        return reactive(new Supplier<RFuture<Collection<V>>>() {
            @Override
            public RFuture<Collection<V>> get() {
                return instance.pollFirstAsync(count);
            }
        });
    }

    @Override
    public Publisher<Collection<V>> pollLast(final int count) {
        return reactive(new Supplier<RFuture<Collection<V>>>() {
            @Override
            public RFuture<Collection<V>> get() {
                return instance.pollLastAsync(count);
            }
        });
    }

    @Override
    public Publisher<Double> firstScore() {
        return reactive(new Supplier<RFuture<Double>>() {
            @Override
            public RFuture<Double> get() {
                return instance.firstScoreAsync();
            }
        });
    }

    @Override
    public Publisher<Double> lastScore() {
        return reactive(new Supplier<RFuture<Double>>() {
            @Override
            public RFuture<Double> get() {
                return instance.lastScoreAsync();
            }
        });
    }

    @Override
    public Publisher<V> iterator(String pattern) {
        return scanIteratorReactive(pattern, 10);
    }

    @Override
    public Publisher<V> iterator(int count) {
        return scanIteratorReactive(null, count);
    }

    @Override
    public Publisher<V> iterator(String pattern, int count) {
        return scanIteratorReactive(pattern, count);
    }

    @Override
    public Publisher<Integer> revRank(final V o) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.revRankAsync(o);
            }
        });
    }

    @Override
    public Publisher<Long> addAll(final Map<V, Double> objects) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.addAllAsync(objects);
            }
        });
    }

    @Override
    public Publisher<Integer> addAndGetRank(final double score, final V object) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.addAndGetRankAsync(score, object);
            }
        });
    }

    @Override
    public Publisher<Integer> addAndGetRevRank(final double score, final V object) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.addAndGetRevRankAsync(score, object);
            }
        });
    }

    @Override
    public Publisher<Boolean> tryAdd(final double score, final V object) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.tryAddAsync(score, object);
            }
        });
    }

    @Override
    public Publisher<Integer> addScoreAndGetRevRank(final V object, final Number value) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.addScoreAndGetRevRankAsync(object, value);
            }
        });
    }

    @Override
    public Publisher<Integer> addScoreAndGetRank(final V object, final Number value) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.addScoreAndGetRankAsync(object, value);
            }
        });
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
