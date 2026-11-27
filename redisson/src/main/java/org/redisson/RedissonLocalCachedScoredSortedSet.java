/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.redisson.api.RFuture;
import org.redisson.api.RLocalCachedScoredSortedSet;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.BaseStatusListener;
import org.redisson.api.options.LocalCachedScoredSortedSetOptions;
import org.redisson.api.options.LocalCachedScoredSortedSetParams;
import org.redisson.cache.AbstractCacheMap;
import org.redisson.cache.CachedSortedSetEntry;
import org.redisson.cache.LFUCacheMap;
import org.redisson.cache.LRUCacheMap;
import org.redisson.cache.LocalCachedMapClear;
import org.redisson.cache.LocalCachedMapUpdate;
import org.redisson.cache.LocalCachedMessageCodec;
import org.redisson.cache.LocalCachedScoreSortedSetInvalidate;
import org.redisson.cache.NoOpCacheMap;
import org.redisson.cache.NoneCacheMap;
import org.redisson.cache.ReferenceCacheMap;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.ScoredEntry;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;

public class RedissonLocalCachedScoredSortedSet<V> extends RedissonScoredSortedSet<V> implements RLocalCachedScoredSortedSet<V> {

    private final ConcurrentSkipListMap<Double, ConcurrentSkipListSet<CachedSortedSetEntry<V>>> scoreCache = new ConcurrentSkipListMap<>();
    private Map<V, Double> cache;
    private RTopic topic;
    private byte[] instanceId;
    private int syncListenerId;
    private int reconnectionListenerId;
    private final boolean isLocalOnly;
    private final boolean readFromLocalCache;
    private final boolean preload;
    private final LocalCachedScoredSortedSetOptions.ReconnectionStrategy reconnectionStrategy;

    /**
     * Creates a local-cached scored sorted set wrapper.
     *
     * @param codec           codec used to encode/decode values and scores
     * @param commandExecutor command executor used by Redisson internals
     * @param name            Redis object name
     * @param redisson        Redisson client instance
     * @param options         local cache behavior options
     */
    public RedissonLocalCachedScoredSortedSet(Codec codec, CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson, LocalCachedScoredSortedSetOptions<V> options) {
        super(codec, commandExecutor, name, redisson);
        LocalCachedScoredSortedSetParams<V> params = (LocalCachedScoredSortedSetParams<V>) options;
        isLocalOnly = params.getStoreMode() == LocalCachedScoredSortedSetOptions.StoreMode.LOCALCACHE;
        readFromLocalCache = isLocalOnly
                || params.getReadMode() == LocalCachedScoredSortedSetOptions.ReadMode.LOCALCACHE;
        reconnectionStrategy = params.getReconnectionStrategy();
        preload = params.isPreload();
        init(commandExecutor, name, params);
    }

    /**
     * Reloads local cache state from Redis and drops stale local entries.
     * <p>
     * This method synchronizes both internal structures: value->score cache and score buckets.
     */
    @Override
    public void preloadCache() {
        Double lastScore = get(super.lastScoreAsync());
        Double firstScore = get(super.firstScoreAsync());
        if (lastScore == null) {
            scoreCache.clear();
            cache.clear();
            return;
        }
        Set<V> allCachedValue = new HashSet<>(cache.keySet());
        get(super.entryRangeAsync(firstScore, true, lastScore, true)).forEach(entry -> {
            V value = entry.getValue();
            addCache(entry.getScore(), value);
            allCachedValue.remove(value);
        });
        for (V v : allCachedValue) {
            removeCache(v);
        }
    }

    /**
     * Returns the internal score-bucket cache used for local rank/range operations.
     *
     * @return local score-to-values cache
     */
    @Override
    public ConcurrentMap<Double, ConcurrentSkipListSet<CachedSortedSetEntry<V>>> getScoreCache() {
        return scoreCache;
    }

    /**
     * Returns the internal value-to-score cache.
     *
     * @return local value-to-score cache
     */
    @Override
    public Map<V, Double> getCache() {
        return cache;
    }

    @Override
    public RFuture<Boolean> addAsync(double score, V value) {
        boolean result = addCache(score, value);
        broadcastUpdate(encode(value), encode(score));
        if (isLocalOnly) {
            return new CompletableFutureWrapper<>(result);
        }
        return super.addAsync(score, value);
    }

    @Override
    public RFuture<Integer> addAllAsync(Map<V, Double> objects) {
        List<LocalCachedMapUpdate.Entry> entries = new LinkedList<>();
        for (Map.Entry<V, Double> entry : objects.entrySet()) {
            addCache(entry.getValue(), entry.getKey());
            ByteBuf bValue = encode(entry.getKey());
            ByteBuf bScore = encode(entry.getValue());
            try {
                entries.add(new LocalCachedMapUpdate.Entry(bValue, bScore));
            } finally {
                bValue.release();
                bScore.release();
            }
        }
        if (!entries.isEmpty()) {
            Object msg = new LocalCachedMapUpdate(instanceId, entries);
            topic.publishAsync(msg);
        }
        if (isLocalOnly) {
            return new CompletableFutureWrapper<>(entries.size());
        }
        return super.addAllAsync(objects);
    }

    @Override
    public RFuture<Boolean> addIfAbsentAsync(double score, V object) {
        if (isLocalOnly) {
            if (cache.containsKey(object)) {
                return new CompletableFutureWrapper<>(false);
            }
            addCache(score, object);
            broadcastUpdate(encode(object), encode(score));
            return new CompletableFutureWrapper<>(true);
        }
        return new CompletableFutureWrapper<>(
                super.addIfAbsentAsync(score, object).toCompletableFuture().thenApply(added -> {
                    if (Boolean.TRUE.equals(added)) {
                        addCache(score, object);
                        broadcastUpdate(encode(object), encode(score));
                    }
                    return added;
                }));
    }

    @Override
    public RFuture<Boolean> addIfExistsAsync(double score, V object) {
        if (isLocalOnly) {
            if (!cache.containsKey(object)) {
                return new CompletableFutureWrapper<>(false);
            }
            addCache(score, object);
            broadcastUpdate(encode(object), encode(score));
            return new CompletableFutureWrapper<>(true);
        }
        return new CompletableFutureWrapper<>(
                super.addIfExistsAsync(score, object).toCompletableFuture().thenApply(added -> {
                    if (Boolean.TRUE.equals(added)) {
                        addCache(score, object);
                        broadcastUpdate(encode(object), encode(score));
                    }
                    return added;
                }));
    }

    @Override
    public RFuture<Boolean> addIfLessAsync(double score, V object) {
        if (isLocalOnly) {
            Double current = cache.get(object);
            if (current != null && score >= current) {
                return new CompletableFutureWrapper<>(false);
            }
            addCache(score, object);
            broadcastUpdate(encode(object), encode(score));
            return new CompletableFutureWrapper<>(true);
        }
        return new CompletableFutureWrapper<>(
                super.addIfLessAsync(score, object).toCompletableFuture().thenApply(added -> {
                    if (Boolean.TRUE.equals(added)) {
                        addCache(score, object);
                        broadcastUpdate(encode(object), encode(score));
                    }
                    return added;
                }));
    }

    @Override
    public RFuture<Boolean> addIfGreaterAsync(double score, V object) {
        if (isLocalOnly) {
            Double current = cache.get(object);
            if (current != null && score <= current) {
                return new CompletableFutureWrapper<>(false);
            }
            addCache(score, object);
            broadcastUpdate(encode(object), encode(score));
            return new CompletableFutureWrapper<>(true);
        }
        return new CompletableFutureWrapper<>(
                super.addIfGreaterAsync(score, object).toCompletableFuture().thenApply(added -> {
                    if (Boolean.TRUE.equals(added)) {
                        addCache(score, object);
                        broadcastUpdate(encode(object), encode(score));
                    }
                    return added;
                }));
    }

    @SuppressWarnings("unchecked")
    @Override
    public RFuture<Boolean> removeAsync(Object object) {
        boolean removed = removeCache((V) object);
        if (removed) {
            broadcastRemove((V) object);
        }
        if (isLocalOnly) {
            return new CompletableFutureWrapper<>(removed);
        }
        return super.removeAsync(object);
    }

    @Override
    public RFuture<Boolean> removeAllAsync(Collection<?> c) {
        List<LocalCachedScoreSortedSetInvalidate.Entry> entries = new LinkedList<>();
        boolean changed = false;
        for (Object o : c) {
            @SuppressWarnings("unchecked")
            boolean removed = removeCache((V) o);
            if (removed) {
                changed = true;
                ByteBuf bValue = encode(o);
                try {
                    entries.add(new LocalCachedScoreSortedSetInvalidate.Entry(bValue));
                } finally {
                    bValue.release();
                }
            }
        }
        if (!entries.isEmpty()) {
            topic.publishAsync(new LocalCachedScoreSortedSetInvalidate(instanceId, entries));
        }
        if (isLocalOnly) {
            return new CompletableFutureWrapper<>(changed);
        }
        return super.removeAllAsync(c);
    }

    @Override
    public RFuture<Integer> removeRangeByScoreAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        if (startScore > endScore) {
            return new CompletableFutureWrapper<>(0);
        }
        ConcurrentNavigableMap<Double, ConcurrentSkipListSet<CachedSortedSetEntry<V>>> temp = scoreCache.subMap(startScore, startScoreInclusive, endScore, endScoreInclusive);
        List<LocalCachedScoreSortedSetInvalidate.Entry> entries = new LinkedList<>();
        temp.forEach((score, values) -> {
            for (CachedSortedSetEntry<V> ce : values) {
                cache.remove(ce.getValue());
                ByteBuf bValue = encode(ce.getValue());
                try {
                    entries.add(new LocalCachedScoreSortedSetInvalidate.Entry(bValue));
                } finally {
                    bValue.release();
                }
            }
        });
        temp.clear();
        if (!entries.isEmpty()) {
            topic.publishAsync(new LocalCachedScoreSortedSetInvalidate(instanceId, entries));
        }
        if (isLocalOnly) {
            return new CompletableFutureWrapper<>(entries.size());
        }
        return super.removeRangeByScoreAsync(startScore, startScoreInclusive, endScore, endScoreInclusive);
    }

    @Override
    public RFuture<Double> addScoreAsync(V element, Number value) {
        if (isLocalOnly) {
            Double currentScore = cache.get(element);
            double baseScore;
            if (currentScore != null) {
                baseScore = currentScore;
            } else {
                baseScore = 0.0;
            }
            double newScore = baseScore + value.doubleValue();
            addCache(newScore, element);
            broadcastUpdate(encode(element), encode(newScore));
            return new CompletableFutureWrapper<>(newScore);
        }
        return new CompletableFutureWrapper<>(
                super.addScoreAsync(element, value).toCompletableFuture().thenApply(newScore -> {
                    if (newScore != null) {
                        addCache(newScore, element);
                        broadcastUpdate(encode(element), encode(newScore));
                    }
                    return newScore;
                }));
    }

    @Override
    public RFuture<Boolean> replaceAsync(V oldObject, V newObject) {
        if (isLocalOnly) {
            Double score = cache.get(oldObject);
            if (score == null) {
                return new CompletableFutureWrapper<>(false);
            }
            removeCache(oldObject);
            addCache(score, newObject);
            // Broadcast remove of old and add of new
            ByteBuf bOld = encode(oldObject);
            try {
                topic.publishAsync(new LocalCachedScoreSortedSetInvalidate(instanceId, bOld));
            } finally {
                bOld.release();
            }
            broadcastUpdate(encode(newObject), encode(score));
            return new CompletableFutureWrapper<>(true);
        }
        return new CompletableFutureWrapper<>(
                super.replaceAsync(oldObject, newObject).toCompletableFuture().thenApply(replaced -> {
                    if (Boolean.TRUE.equals(replaced)) {
                        Double score = cache.remove(oldObject);
                        if (score != null) {
                            removeElement(score, oldObject);
                            addCache(score, newObject);
                            ByteBuf bOld = encode(oldObject);
                            try {
                                topic.publishAsync(new LocalCachedScoreSortedSetInvalidate(instanceId, bOld));
                            } finally {
                                bOld.release();
                            }
                            broadcastUpdate(encode(newObject), encode(score));
                        }
                    }
                    return replaced;
                }));
    }

    @Override
    public RFuture<V> pollFirstAsync() {
        if (isLocalOnly) {
            if (scoreCache.isEmpty()) {
                return new CompletableFutureWrapper<>((V) null);
            }
            Map.Entry<Double, ConcurrentSkipListSet<CachedSortedSetEntry<V>>> firstEntry = scoreCache.firstEntry();
            if (firstEntry == null || firstEntry.getValue().isEmpty()) {
                return new CompletableFutureWrapper<>((V) null);
            }
            V first = firstEntry.getValue().first().getValue();
            removeCache(first);
            broadcastRemove(first);
            return new CompletableFutureWrapper<>(first);
        }
        return new CompletableFutureWrapper<>(
                super.pollFirstAsync().toCompletableFuture().thenApply(v -> {
                    if (v != null) {
                        boolean removed = removeCache(v);
                        if (removed) broadcastRemove(v);
                    }
                    return v;
                }));
    }

    @Override
    public RFuture<V> pollLastAsync() {
        if (isLocalOnly) {
            if (scoreCache.isEmpty()) {
                return new CompletableFutureWrapper<>((V) null);
            }
            Map.Entry<Double, ConcurrentSkipListSet<CachedSortedSetEntry<V>>> lastEntry = scoreCache.lastEntry();
            if (lastEntry == null || lastEntry.getValue().isEmpty()) {
                return new CompletableFutureWrapper<>((V) null);
            }
            V last = lastEntry.getValue().last().getValue();
            removeCache(last);
            broadcastRemove(last);
            return new CompletableFutureWrapper<>(last);
        }
        return new CompletableFutureWrapper<>(
                super.pollLastAsync().toCompletableFuture().thenApply(v -> {
                    if (v != null) {
                        boolean removed = removeCache(v);
                        if (removed) broadcastRemove(v);
                    }
                    return v;
                }));
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        scoreCache.clear();
        cache.clear();
        // Broadcast delete/clear to other instances to invalidate their local caches
        topic.publishAsync(new LocalCachedMapClear(instanceId, new byte[16], false));
        if (isLocalOnly) {
            return new CompletableFutureWrapper<>(true);
        }
        return super.deleteAsync();
    }

    @Override
    public RFuture<Boolean> retainAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return deleteAsync();
        }
        List<LocalCachedScoreSortedSetInvalidate.Entry> entries = new LinkedList<>();
        for (V key : new ArrayList<>(cache.keySet())) {
            if (!c.contains(key) && removeCache(key)) {
                ByteBuf bValue = encode(key);
                try {
                    entries.add(new LocalCachedScoreSortedSetInvalidate.Entry(bValue));
                } finally {
                    bValue.release();
                }
            }
        }
        boolean changed = !entries.isEmpty();
        if (changed) {
            topic.publishAsync(new LocalCachedScoreSortedSetInvalidate(instanceId, entries));
        }
        if (isLocalOnly) {
            return new CompletableFutureWrapper<>(changed);
        }
        return super.retainAllAsync(c);
    }

    @Override
    public RFuture<Integer> removeRangeByRankAsync(int startIndex, int endIndex) {
        List<V> toRemove = getValuesByRankRange(startIndex, endIndex, false);
        for (V v : toRemove) {
            Double score = cache.remove(v);
            if (score != null) removeElement(score, v);
        }
        if (!toRemove.isEmpty()) {
            broadcastRemoveBatch(toRemove);
        }
        if (isLocalOnly) {
            return new CompletableFutureWrapper<>(toRemove.size());
        }
        return super.removeRangeByRankAsync(startIndex, endIndex);
    }

    @Override
    public RFuture<Collection<V>> pollFirstAsync(int count) {
        if (count <= 0) {
            return new CompletableFutureWrapper<>(Collections.<V>emptyList());
        }
        if (isLocalOnly) {
            List<V> result = pollFromCache(count, false);
            broadcastRemoveBatch(result);
            return new CompletableFutureWrapper<>(result);
        }
        return new CompletableFutureWrapper<>(
                super.pollFirstAsync(count).toCompletableFuture().thenApply(polled -> {
                    if (polled != null) {
                        for (V v : polled) {
                            if (removeCache(v)) broadcastRemove(v);
                        }
                    }
                    return polled;
                }));
    }

    @Override
    public RFuture<Collection<V>> pollLastAsync(int count) {
        if (count <= 0) {
            return new CompletableFutureWrapper<>(Collections.<V>emptyList());
        }
        if (isLocalOnly) {
            List<V> result = pollFromCache(count, true);
            broadcastRemoveBatch(result);
            return new CompletableFutureWrapper<>(result);
        }
        return new CompletableFutureWrapper<>(
                super.pollLastAsync(count).toCompletableFuture().thenApply(polled -> {
                    if (polled != null) {
                        for (V v : polled) {
                            if (removeCache(v)) broadcastRemove(v);
                        }
                    }
                    return polled;
                }));
    }

    @Override
    public RFuture<ScoredEntry<V>> pollFirstEntryAsync() {
        if (isLocalOnly) {
            List<ScoredEntry<V>> result = pollEntriesFromCache(1, false);
            if (result.isEmpty()) {
                return new CompletableFutureWrapper<>((ScoredEntry<V>) null);
            }
            broadcastRemoveBatch(Collections.singletonList(result.get(0).getValue()));
            return new CompletableFutureWrapper<>(result.get(0));
        }
        return new CompletableFutureWrapper<>(
                super.pollFirstEntryAsync().toCompletableFuture().thenApply(entry -> {
                    if (entry != null && removeCache(entry.getValue())) broadcastRemove(entry.getValue());
                    return entry;
                }));
    }

    @Override
    public RFuture<ScoredEntry<V>> pollLastEntryAsync() {
        if (isLocalOnly) {
            List<ScoredEntry<V>> result = pollEntriesFromCache(1, true);
            if (result.isEmpty()) {
                return new CompletableFutureWrapper<>((ScoredEntry<V>) null);
            }
            broadcastRemoveBatch(Collections.singletonList(result.get(0).getValue()));
            return new CompletableFutureWrapper<>(result.get(0));
        }
        return new CompletableFutureWrapper<>(
                super.pollLastEntryAsync().toCompletableFuture().thenApply(entry -> {
                    if (entry != null && removeCache(entry.getValue())) broadcastRemove(entry.getValue());
                    return entry;
                }));
    }

    @Override
    public RFuture<List<ScoredEntry<V>>> pollFirstEntriesAsync(int count) {
        if (count <= 0) {
            return new CompletableFutureWrapper<>(Collections.<ScoredEntry<V>>emptyList());
        }
        if (isLocalOnly) {
            List<ScoredEntry<V>> result = pollEntriesFromCache(count, false);
            List<V> values = new ArrayList<>(result.size());
            for (ScoredEntry<V> e : result) {
                values.add(e.getValue());
            }
            broadcastRemoveBatch(values);
            return new CompletableFutureWrapper<>(result);
        }
        return new CompletableFutureWrapper<>(
                super.pollFirstEntriesAsync(count).toCompletableFuture().thenApply(entries -> {
                    if (entries != null) {
                        for (ScoredEntry<V> e : entries) {
                            if (removeCache(e.getValue())) broadcastRemove(e.getValue());
                        }
                    }
                    return entries;
                }));
    }

    @Override
    public RFuture<List<ScoredEntry<V>>> pollLastEntriesAsync(int count) {
        if (count <= 0) {
            return new CompletableFutureWrapper<>(Collections.<ScoredEntry<V>>emptyList());
        }
        if (isLocalOnly) {
            List<ScoredEntry<V>> result = pollEntriesFromCache(count, true);
            List<V> values = new ArrayList<>(result.size());
            for (ScoredEntry<V> e : result) {
                values.add(e.getValue());
            }
            broadcastRemoveBatch(values);
            return new CompletableFutureWrapper<>(result);
        }
        return new CompletableFutureWrapper<>(
                super.pollLastEntriesAsync(count).toCompletableFuture().thenApply(entries -> {
                    if (entries != null) {
                        for (ScoredEntry<V> e : entries) {
                            if (removeCache(e.getValue())) broadcastRemove(e.getValue());
                        }
                    }
                    return entries;
                }));
    }

    @Override
    public RFuture<Integer> addAllIfAbsentAsync(Map<V, Double> objects) {
        if (isLocalOnly) {
            return addAllConditional(objects, (key, current, newScore) -> current == null);
        }
        return new CompletableFutureWrapper<>(
                super.addAllIfAbsentAsync(objects).toCompletableFuture().thenApply(added -> {
                    addAllConditional(objects, (key, current, newScore) -> current == null);
                    return added;
                }));
    }

    @Override
    public RFuture<Integer> addAllIfExistAsync(Map<V, Double> objects) {
        if (isLocalOnly) {
            return addAllConditional(objects, (key, current, newScore) -> current != null);
        }
        return new CompletableFutureWrapper<>(
                super.addAllIfExistAsync(objects).toCompletableFuture().thenApply(added -> {
                    addAllConditional(objects, (key, current, newScore) -> current != null);
                    return added;
                }));
    }

    @Override
    public RFuture<Integer> addAllIfGreaterAsync(Map<V, Double> objects) {
        if (isLocalOnly) {
            return addAllConditional(objects, (key, current, newScore) -> current == null || newScore > current);
        }
        return new CompletableFutureWrapper<>(
                super.addAllIfGreaterAsync(objects).toCompletableFuture().thenApply(added -> {
                    addAllConditional(objects, (key, current, newScore) -> current == null || newScore > current);
                    return added;
                }));
    }

    @Override
    public RFuture<Integer> addAllIfLessAsync(Map<V, Double> objects) {
        if (isLocalOnly) {
            return addAllConditional(objects, (key, current, newScore) -> current == null || newScore < current);
        }
        return new CompletableFutureWrapper<>(
                super.addAllIfLessAsync(objects).toCompletableFuture().thenApply(added -> {
                    addAllConditional(objects, (key, current, newScore) -> current == null || newScore < current);
                    return added;
                }));
    }

    @Override
    public RFuture<Integer> addAndGetRevRankAsync(double score, V object) {
        addCache(score, object);
        broadcastUpdate(encode(object), encode(score));
        if (isLocalOnly) {
            return new CompletableFutureWrapper<>(computeRevRank(score, object));
        }
        return super.addAndGetRevRankAsync(score, object);
    }

    @Override
    public RFuture<Integer> addScoreAndGetRankAsync(V object, Number value) {
        if (isLocalOnly) {
            Double currentScore = cache.get(object);
            double baseScore;
            if (currentScore != null) {
                baseScore = currentScore;
            } else {
                baseScore = 0.0;
            }
            double newScore = baseScore + value.doubleValue();
            addCache(newScore, object);
            broadcastUpdate(encode(object), encode(newScore));
            return new CompletableFutureWrapper<>(computeRank(newScore, object));
        }
        return new CompletableFutureWrapper<>(
                super.addScoreAndGetRankAsync(object, value).toCompletableFuture().thenApply(rank -> {
                    Double currentScore = cache.get(object);
                    double baseScore;
                    if (currentScore != null) {
                        baseScore = currentScore;
                    } else {
                        baseScore = 0.0;
                    }
                    double newScore = baseScore + value.doubleValue();
                    addCache(newScore, object);
                    broadcastUpdate(encode(object), encode(newScore));
                    return rank;
                }));
    }

    @Override
    public RFuture<Integer> addScoreAndGetRevRankAsync(V object, Number value) {
        if (isLocalOnly) {
            Double currentScore = cache.get(object);
            double baseScore;
            if (currentScore != null) {
                baseScore = currentScore;
            } else {
                baseScore = 0.0;
            }
            double newScore = baseScore + value.doubleValue();
            addCache(newScore, object);
            broadcastUpdate(encode(object), encode(newScore));
            return new CompletableFutureWrapper<>(computeRevRank(newScore, object));
        }
        return new CompletableFutureWrapper<>(
                super.addScoreAndGetRevRankAsync(object, value).toCompletableFuture().thenApply(rank -> {
                    Double currentScore = cache.get(object);
                    double baseScore;
                    if (currentScore != null) {
                        baseScore = currentScore;
                    } else {
                        baseScore = 0.0;
                    }
                    double newScore = baseScore + value.doubleValue();
                    addCache(newScore, object);
                    broadcastUpdate(encode(object), encode(newScore));
                    return rank;
                }));
    }

    @Override
    public RFuture<Double> getScoreAsync(V o) {
        if (readFromLocalCache) {
            return new CompletableFutureWrapper<>(cache.get(o));
        }
        return super.getScoreAsync(o);
    }

    @Override
    public RFuture<List<Double>> getScoreAsync(Collection<V> elements) {
        if (readFromLocalCache) {
            List<V> elementList = new ArrayList<>(elements);
            Double[] scores = new Double[elementList.size()];
            for (int i = 0; i < elementList.size(); i++) {
                Double score = cache.get(elementList.get(i));
                scores[i] = score;
            }
            return new CompletableFutureWrapper<>(Arrays.asList(scores));
        }
        return super.getScoreAsync(elements);
    }

    @Override
    @SuppressWarnings("SuspiciousMethodCalls")
    public RFuture<Boolean> containsAsync(Object o) {
        if (readFromLocalCache) {
            return new CompletableFutureWrapper<>(cache.containsKey(o));
        }
        return super.containsAsync(o);
    }

    @Override
    @SuppressWarnings("SuspiciousMethodCalls")
    public RFuture<Boolean> containsAllAsync(Collection<?> c) {
        if (readFromLocalCache) {
            return new CompletableFutureWrapper<>(cache.keySet().containsAll(c));
        }
        return super.containsAllAsync(c);
    }

    @Override
    public RFuture<Integer> sizeAsync() {
        if (readFromLocalCache) {
            return new CompletableFutureWrapper<>(cache.size());
        }
        return super.sizeAsync();
    }

    @Override
    public RFuture<Collection<V>> readAllAsync() {
        if (readFromLocalCache) {
            return new CompletableFutureWrapper<>(new ArrayList<>(cache.keySet()));
        }
        return super.readAllAsync();
    }

    @Override
    public RFuture<Double> firstScoreAsync() {
        if (readFromLocalCache) {
            if (scoreCache.isEmpty()) {
                return new CompletableFutureWrapper<>((Double) null);
            }
            return new CompletableFutureWrapper<>(scoreCache.firstKey());
        }
        return super.firstScoreAsync();
    }

    @Override
    public RFuture<Double> lastScoreAsync() {
        if (readFromLocalCache) {
            if (scoreCache.isEmpty()) {
                return new CompletableFutureWrapper<>((Double) null);
            }
            return new CompletableFutureWrapper<>(scoreCache.lastKey());
        }
        return super.lastScoreAsync();
    }

    @Override
    public RFuture<V> firstAsync() {
        if (readFromLocalCache) {
            if (scoreCache.isEmpty()) {
                return new CompletableFutureWrapper<>((V) null);
            }
            ConcurrentSkipListSet<CachedSortedSetEntry<V>> firstSet = scoreCache.firstEntry().getValue();
            V first;
            if (firstSet.isEmpty()) {
                first = null;
            } else {
                first = firstSet.first().getValue();
            }
            return new CompletableFutureWrapper<>(first);
        }
        return super.firstAsync();
    }

    @Override
    public RFuture<V> lastAsync() {
        if (readFromLocalCache) {
            if (scoreCache.isEmpty()) {
                return new CompletableFutureWrapper<>((V) null);
            }
            ConcurrentSkipListSet<CachedSortedSetEntry<V>> lastSet = scoreCache.lastEntry().getValue();
            V last;
            if (lastSet.isEmpty()) {
                last = null;
            } else {
                last = lastSet.last().getValue();
            }
            return new CompletableFutureWrapper<>(last);
        }
        return super.lastAsync();
    }

    @Override
    public RFuture<Integer> countAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        if (readFromLocalCache) {
            if (startScore > endScore) {
                return new CompletableFutureWrapper<>(0);
            }
            int count = 0;
            for (ConcurrentSkipListSet<CachedSortedSetEntry<V>> ts : scoreCache.subMap(startScore, startScoreInclusive, endScore, endScoreInclusive).values()) {
                count += ts.size();
            }
            return new CompletableFutureWrapper<>(count);
        }
        return super.countAsync(startScore, startScoreInclusive, endScore, endScoreInclusive);
    }

    @Override
    public RFuture<Collection<V>> valueRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        if (startScore > endScore) {
            return new CompletableFutureWrapper<>(Collections.emptyList());
        }
        if (readFromLocalCache) {
            List<V> list = new ArrayList<>();
            for (ConcurrentSkipListSet<CachedSortedSetEntry<V>> ts : scoreCache.subMap(startScore, startScoreInclusive, endScore, endScoreInclusive).values()) {
                for (CachedSortedSetEntry<V> ce : ts) {
                    list.add(ce.getValue());
                }
            }
            return new CompletableFutureWrapper<>(list);
        }
        return super.valueRangeAsync(startScore, startScoreInclusive, endScore, endScoreInclusive);
    }

    @Override
    public RFuture<Collection<ScoredEntry<V>>> entryRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        if (startScore > endScore) {
            return new CompletableFutureWrapper<>(Collections.<ScoredEntry<V>>emptyList());
        }
        if (readFromLocalCache) {
            List<ScoredEntry<V>> list = new ArrayList<>();
            scoreCache.subMap(startScore, startScoreInclusive, endScore, endScoreInclusive)
                    .forEach((score, values) -> {
                        for (CachedSortedSetEntry<V> ce : values) {
                            list.add(new ScoredEntry<>(score, ce.getValue()));
                        }
                    });
            return new CompletableFutureWrapper<>(list);
        }
        return super.entryRangeAsync(startScore, startScoreInclusive, endScore, endScoreInclusive);
    }

    @Override
    public RFuture<ScoredEntry<V>> firstEntryAsync() {
        if (readFromLocalCache) {
            if (scoreCache.isEmpty()) {
                return new CompletableFutureWrapper<>((ScoredEntry<V>) null);
            }
            Map.Entry<Double, ConcurrentSkipListSet<CachedSortedSetEntry<V>>> first = scoreCache.firstEntry();
            ConcurrentSkipListSet<CachedSortedSetEntry<V>> values = first.getValue();
            V v;
            if (values.isEmpty()) {
                v = null;
            } else {
                v = values.first().getValue();
            }
            ScoredEntry<V> entry;
            if (v != null) {
                entry = new ScoredEntry<>(first.getKey(), v);
            } else {
                entry = null;
            }
            return new CompletableFutureWrapper<>(entry);
        }
        return super.firstEntryAsync();
    }

    @Override
    public RFuture<ScoredEntry<V>> lastEntryAsync() {
        if (readFromLocalCache) {
            if (scoreCache.isEmpty()) {
                return new CompletableFutureWrapper<>((ScoredEntry<V>) null);
            }
            Map.Entry<Double, ConcurrentSkipListSet<CachedSortedSetEntry<V>>> last = scoreCache.lastEntry();
            ConcurrentSkipListSet<CachedSortedSetEntry<V>> values = last.getValue();
            V v;
            if (values.isEmpty()) {
                v = null;
            } else {
                v = values.last().getValue();
            }
            ScoredEntry<V> entry;
            if (v != null) {
                entry = new ScoredEntry<>(last.getKey(), v);
            } else {
                entry = null;
            }
            return new CompletableFutureWrapper<>(entry);
        }
        return super.lastEntryAsync();
    }

    @Override
    public RFuture<Integer> rankAsync(V o) {
        if (readFromLocalCache) {
            Double score = cache.get(o);
            if (score == null) return new CompletableFutureWrapper<>((Integer) null);
            return new CompletableFutureWrapper<>(computeRank(score, o));
        }
        return super.rankAsync(o);
    }

    @Override
    public RFuture<Integer> revRankAsync(V o) {
        if (readFromLocalCache) {
            Double score = cache.get(o);
            if (score == null) return new CompletableFutureWrapper<>((Integer) null);
            return new CompletableFutureWrapper<>(computeRevRank(score, o));
        }
        return super.revRankAsync(o);
    }

    @Override
    public RFuture<List<Integer>> revRankAsync(Collection<V> elements) {
        if (readFromLocalCache) {
            List<Integer> ranks = new ArrayList<>(elements.size());
            for (V o : elements) {
                Double score = cache.get(o);
                Integer rank;
                if (score == null) {
                    rank = null;
                } else {
                    rank = computeRevRank(score, o);
                }
                ranks.add(rank);
            }
            return new CompletableFutureWrapper<>(ranks);
        }
        return super.revRankAsync(elements);
    }

    @Override
    public RFuture<Collection<V>> valueRangeAsync(int startIndex, int endIndex) {
        if (readFromLocalCache) {
            return new CompletableFutureWrapper<>(getValuesByRankRange(startIndex, endIndex, false));
        }
        return super.valueRangeAsync(startIndex, endIndex);
    }

    @Override
    public RFuture<Collection<V>> valueRangeReversedAsync(int startIndex, int endIndex) {
        if (readFromLocalCache) {
            return new CompletableFutureWrapper<>(getValuesByRankRange(startIndex, endIndex, true));
        }
        return super.valueRangeReversedAsync(startIndex, endIndex);
    }

    @Override
    public RFuture<Collection<V>> valueRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        if (startScore > endScore) {
            return new CompletableFutureWrapper<>(Collections.emptyList());
        }
        if (readFromLocalCache) {
            List<V> list = new ArrayList<>();
            scoreCache.subMap(startScore, startScoreInclusive, endScore, endScoreInclusive)
                    .descendingMap()
                    .forEach((score, values) -> {
                        for (CachedSortedSetEntry<V> ce : values.descendingSet()) {
                            list.add(ce.getValue());
                        }
                    });
            return new CompletableFutureWrapper<>(list);
        }
        return super.valueRangeReversedAsync(startScore, startScoreInclusive, endScore, endScoreInclusive);
    }

    @Override
    public RFuture<Collection<ScoredEntry<V>>> entryRangeAsync(int startIndex, int endIndex) {
        if (readFromLocalCache) {
            List<ScoredEntry<V>> result = new ArrayList<>();
            for (CachedSortedSetEntry<V> value : getCacheValuesByRankRange(startIndex, endIndex, false)) {
                result.add(new ScoredEntry<>(value.getScore(), value.getValue()));
            }
            return new CompletableFutureWrapper<>(result);
        }
        return super.entryRangeAsync(startIndex, endIndex);
    }

    @Override
    public RFuture<Collection<ScoredEntry<V>>> entryRangeReversedAsync(int startIndex, int endIndex) {
        if (readFromLocalCache) {
            List<ScoredEntry<V>> result = new ArrayList<>();
            for (CachedSortedSetEntry<V> value : getCacheValuesByRankRange(startIndex, endIndex, true)) {
                result.add(new ScoredEntry<>(value.getScore(), value.getValue()));
            }
            return new CompletableFutureWrapper<>(result);
        }
        return super.entryRangeReversedAsync(startIndex, endIndex);
    }

    @Override
    public RFuture<Collection<ScoredEntry<V>>> entryRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        if (startScore > endScore) {
            return new CompletableFutureWrapper<>(Collections.emptyList());
        }
        if (readFromLocalCache) {
            List<ScoredEntry<V>> list = new ArrayList<>();
            scoreCache.subMap(startScore, startScoreInclusive, endScore, endScoreInclusive)
                    .descendingMap()
                    .forEach((score, values) ->
                            values.descendingSet().forEach(ce -> list.add(new ScoredEntry<>(score, ce.getValue()))));
            return new CompletableFutureWrapper<>(list);
        }
        return super.entryRangeReversedAsync(startScore, startScoreInclusive, endScore, endScoreInclusive);
    }

    @Override
    public RFuture<Integer> addAndGetRankAsync(double score, V object) {
        addCache(score, object);
        broadcastUpdate(encode(object), encode(score));
        if (isLocalOnly) {
            return new CompletableFutureWrapper<>(computeRank(score, object));
        }
        return super.addAndGetRankAsync(score, object);
    }

    /**
     * Releases resources (topic listeners and local caches) associated with this instance.
     * Must be called when this instance is no longer needed.
     */
    @Override
    public void destroy() {
        topic.removeListener(syncListenerId, reconnectionListenerId);
        scoreCache.clear();
        cache.clear();
    }

    private boolean removeCache(V object) {
        Double previousScore = cache.remove(object);
        if (previousScore != null) {
            removeElement(previousScore, object);
            return true;
        }
        return false;
    }

    private boolean addCache(double score, V value) {
        if (cache instanceof NoOpCacheMap) {
            return false;
        }
        Double current = cache.get(value);
        if (current != null && Double.compare(current, score) == 0) {
            return false;
        }
        CachedSortedSetEntry<V> newEntry = new CachedSortedSetEntry<>(score, value, encode(value));
        Set<CachedSortedSetEntry<V>> entries = scoreCache.computeIfAbsent(score, key ->
                new ConcurrentSkipListSet<>((e1, e2) -> compareBytes(e1.getEncoded(), e2.getEncoded())));
        entries.add(newEntry);
        Double previousScore = cache.put(value, score);
        if (previousScore != null && Double.compare(score, previousScore) != 0) {
            removeElement(previousScore, value);
        }
        return previousScore == null;
    }

    private int compareBytes(byte[] a, byte[] b) {
        int minLen = Math.min(a.length, b.length);
        for (int i = 0; i < minLen; i++) {
            byte ba = a[i];
            byte bb = b[i];
            if (ba != bb) {
                return Byte.compare(ba, bb);
            }
        }
        return Integer.compare(a.length, b.length);
    }

    private void removeElement(Double previousScore, V entry) {
        if (previousScore != null) {
            Set<CachedSortedSetEntry<V>> previousSet = scoreCache.get(previousScore);
            if (previousSet != null) {
                previousSet.remove(new CachedSortedSetEntry<>(previousScore, entry, encode(entry)));
                if (previousSet.isEmpty()) {
                    scoreCache.remove(previousScore, previousSet);
                }
            }
        }
    }

    /**
     * Broadcast a remove-notification for a single value.
     */
    private void broadcastRemove(V value) {
        ByteBuf bValue = encode(value);
        try {
            topic.publishAsync(new LocalCachedScoreSortedSetInvalidate(instanceId, bValue));
        } finally {
            bValue.release();
        }
    }

    private void broadcastUpdate(ByteBuf bValue, ByteBuf bScore) {
        try {
            Object msg = new LocalCachedMapUpdate(instanceId, bValue, bScore);
            topic.publishAsync(msg);
        } finally {
            bValue.release();
            bScore.release();
        }
    }

    private int addSyncListener() {
        return topic.addListener(Object.class, (channel, msg) -> syncUpdate(msg));
    }

    @SuppressWarnings("unchecked")
    protected void syncUpdate(Object msg) {
        if (syncClear(msg)) {
            return;
        }
        if (syncInvalidate(msg)) {
            return;
        }
        if (!(msg instanceof LocalCachedMapUpdate)) {
            return;
        }
        LocalCachedMapUpdate updateMsg = (LocalCachedMapUpdate) msg;
        if (Arrays.equals(updateMsg.getExcludedId(), instanceId)) {
            return;
        }
        for (LocalCachedMapUpdate.Entry entry : updateMsg.getEntries()) {
            ByteBuf keyBuf = Unpooled.wrappedBuffer(entry.getKey());
            ByteBuf scoreBuf = Unpooled.wrappedBuffer(entry.getValue());
            try {
                Object value = codec.getMapValueDecoder().decode(keyBuf, null);
                Object score = codec.getMapValueDecoder().decode(scoreBuf, null);
                addCache(((Number) score).doubleValue(), (V) value);
            } catch (IOException e) {
                // ignore decode errors
            } finally {
                keyBuf.release();
                scoreBuf.release();
            }
        }
    }

    private boolean syncClear(Object msg) {
        if (msg instanceof LocalCachedMapClear) {
            LocalCachedMapClear clearMsg = (LocalCachedMapClear) msg;
            if (Arrays.equals(clearMsg.getExcludedId(), instanceId)) {
                return true;
            }
            scoreCache.clear();
            cache.clear();
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean syncInvalidate(Object msg) {
        if (msg instanceof LocalCachedScoreSortedSetInvalidate) {
            LocalCachedScoreSortedSetInvalidate invalidateMsg = (LocalCachedScoreSortedSetInvalidate) msg;
            if (Arrays.equals(invalidateMsg.getExcludedId(), instanceId)) {
                return true;
            }
            for (LocalCachedScoreSortedSetInvalidate.Entry entry : invalidateMsg.getEntries()) {
                ByteBuf valueBuf = Unpooled.wrappedBuffer(entry.getValue());
                try {
                    V value = (V) codec.getMapValueDecoder().decode(valueBuf, null);
                    removeCache(value);
                } catch (IOException e) {
                    // ignore decode errors
                } finally {
                    valueBuf.release();
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Polls {@code count} values from front or back of scoreCache without touching Redis.
     */
    private List<V> pollFromCache(int count, boolean descending) {
        List<V> result = new ArrayList<>(count);
        while (result.size() < count && !scoreCache.isEmpty()) {
            Map.Entry<Double, ConcurrentSkipListSet<CachedSortedSetEntry<V>>> scoreEntry;
            if (descending) {
                scoreEntry = scoreCache.lastEntry();
            } else {
                scoreEntry = scoreCache.firstEntry();
            }
            if (scoreEntry == null) break;
            ConcurrentSkipListSet<CachedSortedSetEntry<V>> values = scoreEntry.getValue();
            if (values.isEmpty()) {
                scoreCache.remove(scoreEntry.getKey(), values);
                continue;
            }
            V v;
            if (descending) {
                v = values.last().getValue();
            } else {
                v = values.first().getValue();
            }
            result.add(v);
            removeCache(v);
        }
        return result;
    }

    /**
     * Polls {@code count} scored entries from front or back of scoreCache without touching Redis.
     */
    private List<ScoredEntry<V>> pollEntriesFromCache(int count, boolean descending) {
        List<ScoredEntry<V>> result = new ArrayList<>(count);
        while (result.size() < count && !scoreCache.isEmpty()) {
            Map.Entry<Double, ConcurrentSkipListSet<CachedSortedSetEntry<V>>> scoreEntry;
            if (descending) {
                scoreEntry = scoreCache.lastEntry();
            } else {
                scoreEntry = scoreCache.firstEntry();
            }
            if (scoreEntry == null) break;
            ConcurrentSkipListSet<CachedSortedSetEntry<V>> values = scoreEntry.getValue();
            if (values.isEmpty()) {
                scoreCache.remove(scoreEntry.getKey(), values);
                continue;
            }
            V v;
            if (descending) {
                v = values.last().getValue();
            } else {
                v = values.first().getValue();
            }
            result.add(new ScoredEntry<>(scoreEntry.getKey(), v));
            removeCache(v);
        }
        return result;
    }

    /**
     * Broadcasts removals for multiple values in a single batched message.
     */
    private void broadcastRemoveBatch(Collection<V> values) {
        if (values.isEmpty()) return;
        List<LocalCachedScoreSortedSetInvalidate.Entry> entries = new ArrayList<>(values.size());
        for (V v : values) {
            ByteBuf bValue = encode(v);
            try {
                entries.add(new LocalCachedScoreSortedSetInvalidate.Entry(bValue));
            } finally {
                bValue.release();
            }
        }
        topic.publishAsync(new LocalCachedScoreSortedSetInvalidate(instanceId, entries));
    }

    /**
     * Returns elements in the rank range [startIndex, endIndex].
     * Negative indices are treated as offsets from the end (-1 = last).
     * If {@code descending} is true, iterates from highest to lowest score.
     */
    private List<V> getValuesByRankRange(int startIndex, int endIndex, boolean descending) {
        List<CachedSortedSetEntry<V>> values = getCacheValuesByRankRange(startIndex, endIndex, descending);
        if (values.isEmpty()) {
            return Collections.emptyList();
        }
        List<V> result = new ArrayList<>(values.size());
        for (CachedSortedSetEntry<V> value : values) {
            result.add(value.getValue());
        }
        return result;
    }

    private List<CachedSortedSetEntry<V>> getCacheValuesByRankRange(int startIndex, int endIndex, boolean descending) {
        int totalSize = cache.size();
        if (totalSize == 0) {
            return Collections.emptyList();
        }
        if (endIndex < 0) {
            endIndex = totalSize + endIndex;
        }
        if (startIndex < 0) {
            startIndex = totalSize + startIndex;
        }
        if (startIndex < 0) {
            startIndex = 0;
        }
        if (endIndex >= totalSize) {
            endIndex = totalSize - 1;
        }
        if (startIndex > endIndex) {
            return Collections.emptyList();
        }
        return getCacheValuesRankRangeMain(startIndex, endIndex, descending);
    }

    private List<CachedSortedSetEntry<V>> getCacheValuesRankRangeMain(int startIndex, int endIndex, boolean descending) {
        List<CachedSortedSetEntry<V>> result = new ArrayList<>(endIndex - startIndex + 1);
        int currentRank = 0;
        Iterable<ConcurrentSkipListSet<CachedSortedSetEntry<V>>> buckets = getConcurrentSkipListSets(descending);
        boolean needBreak = false;
        for (ConcurrentSkipListSet<CachedSortedSetEntry<V>> bucket : buckets) {
            Iterable<CachedSortedSetEntry<V>> iter;
            if (descending) {
                iter = bucket.descendingSet();
            } else {
                iter = bucket;
            }
            for (CachedSortedSetEntry<V> ce : iter) {
                if (currentRank > endIndex) {
                    needBreak = true;
                    break;
                }
                if (currentRank >= startIndex) {
                    result.add(ce);
                }
                currentRank++;
            }
            if (needBreak) {
                break;
            }
        }
        return result;
    }

    private Iterable<ConcurrentSkipListSet<CachedSortedSetEntry<V>>> getConcurrentSkipListSets(boolean descending) {
        Iterable<ConcurrentSkipListSet<CachedSortedSetEntry<V>>> buckets;
        if (descending) {
            buckets = scoreCache.descendingMap().values();
        } else {
            buckets = scoreCache.values();
        }
        return buckets;
    }

    /**
     * Computes the 0-based ascending rank of {@code object} within scoreCache.
     */
    private int computeRank(double score, V object) {
        int rank = 0;
        for (Map.Entry<Double, ConcurrentSkipListSet<CachedSortedSetEntry<V>>> entry : scoreCache.headMap(score, false).entrySet()) {
            rank += entry.getValue().size();
        }
        ConcurrentSkipListSet<CachedSortedSetEntry<V>> sameScore = scoreCache.get(score);
        if (sameScore != null) {
            // Count CacheEntries that come before `object` in the set
            for (CachedSortedSetEntry<V> ce : sameScore) {
                if (ce.getValue().equals(object)) {
                    break;
                }
                rank++;
            }
        }
        return rank;
    }

    /**
     * Computes the 0-based descending rank (reverse rank) of {@code object} within scoreCache.
     */
    private int computeRevRank(double score, V object) {
        int rank = 0;
        for (Map.Entry<Double, ConcurrentSkipListSet<CachedSortedSetEntry<V>>> entry : scoreCache.tailMap(score, false).entrySet()) {
            rank += entry.getValue().size();
        }
        ConcurrentSkipListSet<CachedSortedSetEntry<V>> sameScore = scoreCache.get(score);
        if (sameScore != null) {
            // Count CacheEntries that come after `object` in the set (reverse order)
            for (CachedSortedSetEntry<V> ce : sameScore.descendingSet()) {
                if (ce.getValue().equals(object)) {
                    break;
                }
                rank++;
            }
        }
        return rank;
    }

    /**
     * Functional interface used to decide whether an entry should be added to the cache/set.
     *
     * @param <V> value type
     */
    @FunctionalInterface
    private interface AddCondition<V> {
        boolean test(V key, Double currentScore, double newScore);
    }

    /**
     * Applies a conditional batch-add to the local cache and broadcasts the accepted entries.
     * Used by addAllIfAbsentAsync / addAllIfExistAsync / addAllIfGreaterAsync / addAllIfLessAsync
     * in isLocalOnly mode.
     */
    private RFuture<Integer> addAllConditional(Map<V, Double> objects, AddCondition<V> condition) {
        List<LocalCachedMapUpdate.Entry> entries = new LinkedList<>();
        int count = 0;
        for (Map.Entry<V, Double> entry : objects.entrySet()) {
            Double current = cache.get(entry.getKey());
            if (condition.test(entry.getKey(), current, entry.getValue())) {
                addCache(entry.getValue(), entry.getKey());
                count++;
                ByteBuf bValue = encode(entry.getKey());
                ByteBuf bScore = encode(entry.getValue());
                try {
                    entries.add(new LocalCachedMapUpdate.Entry(bValue, bScore));
                } finally {
                    bValue.release();
                    bScore.release();
                }
            }
        }
        if (!entries.isEmpty()) {
            topic.publishAsync(new LocalCachedMapUpdate(instanceId, entries));
        }
        return new CompletableFutureWrapper<>(count);
    }

    private void init(CommandAsyncExecutor commandExecutor, String name, LocalCachedScoredSortedSetParams<V> params) {
        cache = createCache(params);
        try {
            this.topic = new RedissonTopic(LocalCachedMessageCodec.INSTANCE, commandExecutor, name + ":cache-sync-topic");
            instanceId = commandExecutor.getServiceManager().generateIdArray();
            syncListenerId = addSyncListener();
            if (preload) {
                preloadCache();
            }
            reconnectionListenerId = topic.addListener(new BaseStatusListener() {
                @Override
                public void onSubscribe(String channel) {
                    if (reconnectionStrategy == LocalCachedScoredSortedSetOptions.ReconnectionStrategy.PRE_LOAD) {
                        preloadCache();
                    }
                }
            });
        } catch (Exception e) {
            destroy();
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private <K1, V1> ConcurrentMap<K1, V1> createCache(LocalCachedScoredSortedSetParams<V> options) {
        if (options.getCacheSize() == -1) {
            return new NoOpCacheMap<>();
        }
        if (options.getCacheProvider() == LocalCachedScoredSortedSetOptions.CacheProvider.CAFFEINE) {
            return createCaffeineCache(options);
        }
        AbstractCacheMap<K1, V1> cacheMap;
        if (options.getEvictionPolicy() == LocalCachedScoredSortedSetOptions.EvictionPolicy.NONE) {
            cacheMap = new NoneCacheMap<>(options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        } else if (options.getEvictionPolicy() == LocalCachedScoredSortedSetOptions.EvictionPolicy.LRU) {
            cacheMap = new LRUCacheMap<>(options.getCacheSize(), options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        } else if (options.getEvictionPolicy() == LocalCachedScoredSortedSetOptions.EvictionPolicy.LFU) {
            cacheMap = new LFUCacheMap<>(options.getCacheSize(), options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        } else if (options.getEvictionPolicy() == LocalCachedScoredSortedSetOptions.EvictionPolicy.SOFT) {
            cacheMap = ReferenceCacheMap.soft(options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        } else if (options.getEvictionPolicy() == LocalCachedScoredSortedSetOptions.EvictionPolicy.WEAK) {
            cacheMap = ReferenceCacheMap.weak(options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        } else {
            throw new IllegalArgumentException("Invalid eviction policy: " + options.getEvictionPolicy());
        }

        cacheMap.removalListener(cachedValue -> {
            Double latestScore =
                    (Double) cacheMap.get(cachedValue.getKey());
            Double removedScore =
                    (Double) cachedValue.getValue();
            if (latestScore != null
                    && Double.compare(latestScore, removedScore) == 0) {
                return;
            }
            removeElement((Double) cachedValue.getValue(), (V) cachedValue.getKey());
        });

        return cacheMap;
    }

    @SuppressWarnings("unchecked")
    private <K1, V1> ConcurrentMap<K1, V1> createCaffeineCache(LocalCachedScoredSortedSetParams<V> options) {
        Caffeine<Object, Object> caffeineBuilder = Caffeine.newBuilder();
        if (options.getTimeToLiveInMillis() > 0) {
            caffeineBuilder.expireAfterWrite(options.getTimeToLiveInMillis(), TimeUnit.MILLISECONDS);
        }
        if (options.getMaxIdleInMillis() > 0) {
            caffeineBuilder.expireAfterAccess(options.getMaxIdleInMillis(), TimeUnit.MILLISECONDS);
        }
        if (options.getCacheSize() > 0) {
            caffeineBuilder.maximumSize(options.getCacheSize());
        }
        if (options.getEvictionPolicy() == LocalCachedScoredSortedSetOptions.EvictionPolicy.SOFT) {
            caffeineBuilder.softValues();
        }
        if (options.getEvictionPolicy() == LocalCachedScoredSortedSetOptions.EvictionPolicy.WEAK) {
            caffeineBuilder.weakValues();
        }
        caffeineBuilder.removalListener((K1 key, V1 value, RemovalCause cause) -> {
            if (cause.wasEvicted()) {
                removeElement((Double) value, (V) key);
            }
        });
        return caffeineBuilder.<K1, V1>build().asMap();
    }

}
