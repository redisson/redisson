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
package org.redisson;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.Set;

import org.redisson.api.RFuture;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.api.SortOrder;
import org.redisson.api.mapreduce.RCollectionMapReduce;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.ScanCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.ScoredEntry;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.mapreduce.RedissonCollectionMapReduce;
import org.redisson.misc.RedissonPromise;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonScoredSortedSet<V> extends RedissonExpirable implements RScoredSortedSet<V> {

    private RedissonClient redisson;
    
    public RedissonScoredSortedSet(CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name);
        this.redisson = redisson;
    }

    public RedissonScoredSortedSet(Codec codec, CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(codec, commandExecutor, name);
        this.redisson = redisson;
    }

    @Override
    public <KOut, VOut> RCollectionMapReduce<V, KOut, VOut> mapReduce() {
        return new RedissonCollectionMapReduce<V, KOut, VOut>(this, redisson, commandExecutor.getConnectionManager());
    }

    @Override
    public Collection<V> readAll() {
        return get(readAllAsync());
    }
    
    @Override
    public RFuture<Collection<V>> readAllAsync() {
        return valueRangeAsync(0, -1);
    }
    
    @Override
    public V pollFirst() {
        return get(pollFirstAsync());
    }

    @Override
    public V pollLast() {
        return get(pollLastAsync());
    }

    @Override
    public Collection<V> pollFirst(int count) {
        return get(pollFirstAsync(count));
    }
    
    @Override
    public Collection<V> pollLast(int count) {
        return get(pollLastAsync(count));
    }
    
    @Override
    public RFuture<Collection<V>> pollFirstAsync(int count) {
        if (count <= 0) {
            return RedissonPromise.<Collection<V>>newSucceededFuture(Collections.<V>emptyList());
        }

        return poll(0, count-1, RedisCommands.EVAL_LIST);
    }
    
    @Override
    public RFuture<Collection<V>> pollLastAsync(int count) {
        if (count <= 0) {
            return RedissonPromise.<Collection<V>>newSucceededFuture(Collections.<V>emptyList());
        }
        return poll(-count, -1, RedisCommands.EVAL_LIST);
    }
    
    @Override
    public RFuture<V> pollFirstAsync() {
        return poll(0, 0, RedisCommands.EVAL_FIRST_LIST);
    }

    @Override
    public RFuture<V> pollLastAsync() {
        return poll(-1, -1, RedisCommands.EVAL_FIRST_LIST);
    }

    private <T> RFuture<T> poll(int from, int to, RedisCommand<?> command) {
        return commandExecutor.evalWriteAsync(getName(), codec, command,
                "local v = redis.call('zrange', KEYS[1], ARGV[1], ARGV[2]); "
                + "if #v > 0 then "
                    + "redis.call('zremrangebyrank', KEYS[1], ARGV[1], ARGV[2]); "
                    + "return v; "
                + "end "
                + "return v;",
                Collections.<Object>singletonList(getName()), from, to);
    }

    @Override
    public V pollFirst(long timeout, TimeUnit unit) {
        return get(pollFirstAsync(timeout, unit));
    }
    
    @Override
    public RFuture<V> pollFirstAsync(long timeout, TimeUnit unit) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.BZPOPMIN_VALUE, getName(), toSeconds(timeout, unit));
    }
    
    @Override
    public V pollFirstFromAny(long timeout, TimeUnit unit, String ... queueNames) {
        return get(pollFirstFromAnyAsync(timeout, unit, queueNames));
    }

    @Override
    public RFuture<V> pollFirstFromAnyAsync(long timeout, TimeUnit unit, String ... queueNames) {
        List<Object> params = new ArrayList<Object>(queueNames.length + 1);
        params.add(getName());
        for (Object name : queueNames) {
            params.add(name);
        }
        params.add(toSeconds(timeout, unit));
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.BZPOPMIN_VALUE, params.toArray());
    }

    @Override
    public V pollLastFromAny(long timeout, TimeUnit unit, String ... queueNames) {
        return get(pollLastFromAnyAsync(timeout, unit, queueNames));
    }

    @Override
    public RFuture<V> pollLastFromAnyAsync(long timeout, TimeUnit unit, String ... queueNames) {
        List<Object> params = new ArrayList<Object>(queueNames.length + 1);
        params.add(getName());
        for (Object name : queueNames) {
            params.add(name);
        }
        params.add(toSeconds(timeout, unit));
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.BZPOPMAX_VALUE, params.toArray());
    }
    
    @Override
    public V pollLast(long timeout, TimeUnit unit) {
        return get(pollLastAsync(timeout, unit));
    }
    
    @Override
    public RFuture<V> pollLastAsync(long timeout, TimeUnit unit) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.BZPOPMAX_VALUE, getName(), toSeconds(timeout, unit));
    }

    @Override
    public boolean add(double score, V object) {
        return get(addAsync(score, object));
    }

    @Override
    public Integer addAndGetRank(double score, V object) {
        return get(addAndGetRankAsync(score, object));
    }

    @Override
    public RFuture<Integer> addAndGetRankAsync(double score, V object) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "redis.call('zadd', KEYS[1], ARGV[1], ARGV[2]);" +
                "return redis.call('zrank', KEYS[1], ARGV[2]); ",
                Collections.<Object>singletonList(getName()), new BigDecimal(score).toPlainString(), encode(object));
    }

    @Override
    public Integer addAndGetRevRank(double score, V object) {
        return get(addAndGetRevRankAsync(score, object));
    }

    @Override
    public RFuture<Integer> addAndGetRevRankAsync(double score, V object) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "redis.call('zadd', KEYS[1], ARGV[1], ARGV[2]);" +
                "return redis.call('zrevrank', KEYS[1], ARGV[2]); ",
                Collections.<Object>singletonList(getName()), new BigDecimal(score).toPlainString(), encode(object));
    }

    @Override
    public boolean tryAdd(double score, V object) {
        return get(tryAddAsync(score, object));
    }

    @Override
    public V first() {
        return get(firstAsync());
    }

    @Override
    public RFuture<V> firstAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANGE_SINGLE, getName(), 0, 0);
    }

    @Override
    public V last() {
        return get(lastAsync());
    }

    @Override
    public RFuture<V> lastAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANGE_SINGLE, getName(), -1, -1);
    }
    
    @Override
    public Double firstScore() {
        return get(firstScoreAsync());
    }

    @Override
    public RFuture<Double> firstScoreAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANGE_SINGLE_SCORE, getName(), 0, 0, "WITHSCORES");
    }
    
    @Override
    public Double lastScore() {
        return get(lastScoreAsync());
    }

    @Override
    public RFuture<Double> lastScoreAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANGE_SINGLE_SCORE, getName(), -1, -1, "WITHSCORES");
    }


    @Override
    public RFuture<Boolean> addAsync(double score, V object) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.ZADD_BOOL, getName(), BigDecimal.valueOf(score).toPlainString(), encode(object));
    }

    @Override
    public Long addAll(Map<V, Double> objects) {
        return get(addAllAsync(objects));
    }

    @Override
    public RFuture<Long> addAllAsync(Map<V, Double> objects) {
        if (objects.isEmpty()) {
            return RedissonPromise.newSucceededFuture(0L);
        }
        List<Object> params = new ArrayList<Object>(objects.size()*2+1);
        params.add(getName());
        for (Entry<V, Double> entry : objects.entrySet()) {
            params.add(BigDecimal.valueOf(entry.getValue()).toPlainString());
            params.add(encode(entry.getKey()));
        }

        return commandExecutor.writeAsync(getName(), codec, RedisCommands.ZADD, params.toArray());
    }

    @Override
    public RFuture<Boolean> tryAddAsync(double score, V object) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.ZADD_NX_BOOL, getName(), "NX", BigDecimal.valueOf(score).toPlainString(), encode(object));
    }

    @Override
    public boolean remove(Object object) {
        return get(removeAsync(object));
    }

    @Override
    public int removeRangeByRank(int startIndex, int endIndex) {
        return get(removeRangeByRankAsync(startIndex, endIndex));
    }

    @Override
    public RFuture<Integer> removeRangeByRankAsync(int startIndex, int endIndex) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.ZREMRANGEBYRANK, getName(), startIndex, endIndex);
    }

    @Override
    public int removeRangeByScore(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        return get(removeRangeByScoreAsync(startScore, startScoreInclusive, endScore, endScoreInclusive));
    }

    @Override
    public RFuture<Integer> removeRangeByScoreAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        String startValue = value(startScore, startScoreInclusive);
        String endValue = value(endScore, endScoreInclusive);
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.ZREMRANGEBYSCORE, getName(), startValue, endValue);
    }

    private String value(double score, boolean inclusive) {
        StringBuilder element = new StringBuilder();
        if (!inclusive) {
            element.append("(");
        }
        if (Double.isInfinite(score)) {
            element.append(score > 0 ? "+inf" : "-inf");
        } else {
            element.append(BigDecimal.valueOf(score).toPlainString());
        }
        return element.toString();
    }

    @Override
    public void clear() {
        delete();
    }

    @Override
    public RFuture<Boolean> removeAsync(Object object) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.ZREM, getName(), encode(object));
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    @Override
    public RFuture<Integer> sizeAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZCARD_INT, getName());
    }

    @Override
    public boolean contains(Object object) {
        return get(containsAsync(object));
    }

    @Override
    public RFuture<Boolean> containsAsync(Object o) {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZSCORE_CONTAINS, getName(), encode(o));
    }

    @Override
    public Double getScore(V o) {
        return get(getScoreAsync(o));
    }

    @Override
    public RFuture<Double> getScoreAsync(V o) {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZSCORE, getName(), encode(o));
    }

    @Override
    public Integer rank(V o) {
        return get(rankAsync(o));
    }

    @Override
    public RFuture<Integer> rankAsync(V o) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANK_INT, getName(), encode(o));
    }

    private ListScanResult<ScanObjectEntry> scanIterator(RedisClient client, long startPos) {
        RFuture<ListScanResult<ScanObjectEntry>> f = commandExecutor.readAsync(client, getName(), new ScanCodec(codec), RedisCommands.ZSCAN, getName(), startPos);
        return get(f);
    }

    @Override
    public Iterator<V> iterator() {
        return new RedissonBaseIterator<V>() {

            @Override
            protected ListScanResult<ScanObjectEntry> iterator(RedisClient client, long nextIterPos) {
                return scanIterator(client, nextIterPos);
            }

            @Override
            protected void remove(ScanObjectEntry value) {
                RedissonScoredSortedSet.this.remove((V)value.getObj());
            }
            
        };
    }

    @Override
    public Object[] toArray() {
        List<Object> res = (List<Object>) get(valueRangeAsync(0, -1));
        return res.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        List<Object> res = (List<Object>) get(valueRangeAsync(0, -1));
        return res.toArray(a);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return get(containsAllAsync(c));
    }

    @Override
    public RFuture<Boolean> containsAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return RedissonPromise.newSucceededFuture(true);
        }
        
        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                            "for j = 1, #ARGV, 1 do "
                            + "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[j]) "
                            + "if expireDateScore == false then "
                                + "return 0;"
                            + "end; "
                        + "end; "
                       + "return 1; ",
                Collections.<Object>singletonList(getName()), encode(c).toArray());
    }

    @Override
    public RFuture<Boolean> removeAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return RedissonPromise.newSucceededFuture(false);
        }
        
        List<Object> params = new ArrayList<Object>(c.size()+1);
        params.add(getName());
        encode(params, c);

        return commandExecutor.writeAsync(getName(), codec, RedisCommands.ZREM, params.toArray());
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return get(removeAllAsync(c));
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return get(retainAllAsync(c));
    }
    
    @Override
    public RFuture<Boolean> retainAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return deleteAsync();
        }
        
        List<Object> params = new ArrayList<Object>(c.size()*2);
        for (Object object : c) {
            params.add(0);
            params.add(encode((V)object));
        }
        
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "redis.call('zadd', KEYS[2], unpack(ARGV)); "
                 + "local prevSize = redis.call('zcard', KEYS[1]); "
                 + "local size = redis.call('zinterstore', KEYS[1], 2, KEYS[1], KEYS[2], 'aggregate', 'sum');"
                 + "redis.call('del', KEYS[2]); "
                 + "return size ~= prevSize and 1 or 0; ",
             Arrays.<Object>asList(getName(), "redisson_temp__{" + getName() + "}"), params.toArray());
    }

    @Override
    public Double addScore(V object, Number value) {
        return get(addScoreAsync(object, value));
    }

    @Override
    public RFuture<Double> addScoreAsync(V object, Number value) {
        return commandExecutor.writeAsync(getName(), DoubleCodec.INSTANCE, RedisCommands.ZINCRBY,
                                   getName(), new BigDecimal(value.toString()).toPlainString(), encode(object));
    }
    
    @Override
    public Integer addScoreAndGetRank(V object, Number value) {
        return get(addScoreAndGetRankAsync(object, value));
    }
    
    @Override
    public RFuture<Integer> addScoreAndGetRankAsync(V object, Number value) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "redis.call('zincrby', KEYS[1], ARGV[1], ARGV[2]); "
               +"return redis.call('zrank', KEYS[1], ARGV[2]); ",
                Collections.<Object>singletonList(getName()), new BigDecimal(value.toString()).toPlainString(), encode(object));
    }

    @Override
    public Integer addScoreAndGetRevRank(V object, Number value) {
        return get(addScoreAndGetRevRankAsync(object, value));
    }
    
    @Override
    public RFuture<Integer> addScoreAndGetRevRankAsync(V object, Number value) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "redis.call('zincrby', KEYS[1], ARGV[1], ARGV[2]); "
               +"return redis.call('zrevrank', KEYS[1], ARGV[2]); ",
                Collections.<Object>singletonList(getName()), new BigDecimal(value.toString()).toPlainString(), encode(object));
    }

    @Override
    public Collection<V> valueRange(int startIndex, int endIndex) {
        return get(valueRangeAsync(startIndex, endIndex));
    }

    @Override
    public RFuture<Collection<V>> valueRangeAsync(int startIndex, int endIndex) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANGE, getName(), startIndex, endIndex);
    }

    @Override
    public Collection<V> valueRangeReversed(int startIndex, int endIndex) {
        return get(valueRangeReversedAsync(startIndex, endIndex));
    }
    
    @Override
    public RFuture<Collection<V>> valueRangeReversedAsync(int startIndex, int endIndex) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZREVRANGE, getName(), startIndex, endIndex);
    }
    
    @Override
    public Collection<ScoredEntry<V>> entryRange(int startIndex, int endIndex) {
        return get(entryRangeAsync(startIndex, endIndex));
    }

    @Override
    public RFuture<Collection<ScoredEntry<V>>> entryRangeAsync(int startIndex, int endIndex) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANGE_ENTRY, getName(), startIndex, endIndex, "WITHSCORES");
    }

    @Override
    public Collection<ScoredEntry<V>> entryRangeReversed(int startIndex, int endIndex) {
        return get(entryRangeReversedAsync(startIndex, endIndex));
    }
    
    @Override
    public RFuture<Collection<ScoredEntry<V>>> entryRangeReversedAsync(int startIndex, int endIndex) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZREVRANGE_ENTRY, getName(), startIndex, endIndex, "WITHSCORES");
    }

    @Override
    public Collection<V> valueRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        return get(valueRangeAsync(startScore, startScoreInclusive, endScore, endScoreInclusive));
    }

    @Override
    public RFuture<Collection<V>> valueRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        String startValue = value(startScore, startScoreInclusive);
        String endValue = value(endScore, endScoreInclusive);
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANGEBYSCORE_LIST, getName(), startValue, endValue);
    }

    @Override
    public Collection<V> valueRangeReversed(double startScore, boolean startScoreInclusive, double endScore,
            boolean endScoreInclusive) {
        return get(valueRangeReversedAsync(startScore, startScoreInclusive, endScore, endScoreInclusive));
    }

    @Override
    public RFuture<Collection<V>> valueRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore,
            boolean endScoreInclusive) {
        String startValue = value(startScore, startScoreInclusive);
        String endValue = value(endScore, endScoreInclusive);
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZREVRANGEBYSCORE, getName(), endValue, startValue);
    }


    @Override
    public Collection<ScoredEntry<V>> entryRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        return get(entryRangeAsync(startScore, startScoreInclusive, endScore, endScoreInclusive));
    }

    @Override
    public RFuture<Collection<ScoredEntry<V>>> entryRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        String startValue = value(startScore, startScoreInclusive);
        String endValue = value(endScore, endScoreInclusive);
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANGEBYSCORE_ENTRY, getName(), startValue, endValue, "WITHSCORES");
    }

    @Override
    public Collection<V> valueRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count) {
        return get(valueRangeAsync(startScore, startScoreInclusive, endScore, endScoreInclusive, offset, count));
    }

    @Override
    public RFuture<Collection<V>> valueRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count) {
        String startValue = value(startScore, startScoreInclusive);
        String endValue = value(endScore, endScoreInclusive);
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANGEBYSCORE_LIST, getName(), startValue, endValue, "LIMIT", offset, count);
    }

    @Override
    public Collection<V> valueRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count) {
        return get(valueRangeReversedAsync(startScore, startScoreInclusive, endScore, endScoreInclusive, offset, count));
    }

    @Override
    public RFuture<Collection<V>> valueRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count) {
        String startValue = value(startScore, startScoreInclusive);
        String endValue = value(endScore, endScoreInclusive);
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZREVRANGEBYSCORE, getName(), endValue, startValue, "LIMIT", offset, count);
    }

    @Override
    public Collection<ScoredEntry<V>> entryRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count) {
        return get(entryRangeAsync(startScore, startScoreInclusive, endScore, endScoreInclusive, offset, count));
    }

    @Override
    public Collection<ScoredEntry<V>> entryRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count) {
        return get(entryRangeReversedAsync(startScore, startScoreInclusive, endScore, endScoreInclusive, offset, count));
    }

    @Override
    public RFuture<Collection<ScoredEntry<V>>> entryRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count) {
        String startValue = value(startScore, startScoreInclusive);
        String endValue = value(endScore, endScoreInclusive);
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANGEBYSCORE_ENTRY, getName(), startValue, endValue, "WITHSCORES", "LIMIT", offset, count);
    }

    @Override
    public Collection<ScoredEntry<V>> entryRangeReversed(double startScore, boolean startScoreInclusive,
            double endScore, boolean endScoreInclusive) {
        return get(entryRangeReversedAsync(startScore, startScoreInclusive, endScore, endScoreInclusive));
    }
    
    @Override
    public RFuture<Collection<ScoredEntry<V>>> entryRangeReversedAsync(double startScore, boolean startScoreInclusive,
            double endScore, boolean endScoreInclusive) {
        String startValue = value(startScore, startScoreInclusive);
        String endValue = value(endScore, endScoreInclusive);
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZREVRANGEBYSCORE_ENTRY, getName(), endValue, startValue, "WITHSCORES");
    }
    
    @Override
    public RFuture<Collection<ScoredEntry<V>>> entryRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count) {
        String startValue = value(startScore, startScoreInclusive);
        String endValue = value(endScore, endScoreInclusive);
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZREVRANGEBYSCORE_ENTRY, getName(), endValue, startValue, "WITHSCORES", "LIMIT", offset, count);
    }

    @Override
    public RFuture<Integer> revRankAsync(V o) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZREVRANK_INT, getName(), encode(o));
    }

    @Override
    public Integer revRank(V o) {
        return get(revRankAsync(o));
    }

    @Override
    public Long count(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        return get(countAsync(startScore, startScoreInclusive, endScore, endScoreInclusive));
    }
    
    @Override
    public RFuture<Long> countAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        String startValue = value(startScore, startScoreInclusive);
        String endValue = value(endScore, endScoreInclusive);
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZCOUNT, getName(), startValue, endValue);
    }
    
    @Override
    public int intersection(String... names) {
        return get(intersectionAsync(names));        
    }

    @Override
    public RFuture<Integer> intersectionAsync(String... names) {
        return intersectionAsync(Aggregate.SUM, names);
    }
    
    @Override
    public int intersection(Aggregate aggregate, String... names) {
        return get(intersectionAsync(aggregate, names));        
    }
    
    @Override
    public RFuture<Integer> intersectionAsync(Aggregate aggregate, String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 4);
        args.add(getName());
        args.add(names.length);
        args.addAll(Arrays.asList(names));
        args.add("AGGREGATE");
        args.add(aggregate.name());
        return commandExecutor.writeAsync(getName(), LongCodec.INSTANCE, RedisCommands.ZINTERSTORE_INT, args.toArray());
    }

    @Override
    public int intersection(Map<String, Double> nameWithWeight) {
        return get(intersectionAsync(nameWithWeight));        
    }
    
    @Override
    public RFuture<Integer> intersectionAsync(Map<String, Double> nameWithWeight) {
        return intersectionAsync(Aggregate.SUM, nameWithWeight);
    }

    @Override
    public int intersection(Aggregate aggregate, Map<String, Double> nameWithWeight) {
        return get(intersectionAsync(aggregate, nameWithWeight));        
    }

    @Override
    public RFuture<Integer> intersectionAsync(Aggregate aggregate, Map<String, Double> nameWithWeight) {
        List<Object> args = new ArrayList<Object>(nameWithWeight.size()*2 + 5);
        args.add(getName());
        args.add(nameWithWeight.size());
        args.addAll(nameWithWeight.keySet());
        args.add("WEIGHTS");
        List<String> weights = new ArrayList<String>();
        for (Double weight : nameWithWeight.values()) {
            weights.add(BigDecimal.valueOf(weight).toPlainString());
        }
        args.addAll(weights);
        args.add("AGGREGATE");
        args.add(aggregate.name());
        return commandExecutor.writeAsync(getName(), LongCodec.INSTANCE, RedisCommands.ZINTERSTORE_INT, args.toArray());
    }
    
    @Override
    public int union(String... names) {
        return get(unionAsync(names));        
    }

    @Override
    public RFuture<Integer> unionAsync(String... names) {
        return unionAsync(Aggregate.SUM, names);
    }
    
    @Override
    public int union(Aggregate aggregate, String... names) {
        return get(unionAsync(aggregate, names));        
    }
    
    @Override
    public RFuture<Integer> unionAsync(Aggregate aggregate, String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 4);
        args.add(getName());
        args.add(names.length);
        args.addAll(Arrays.asList(names));
        args.add("AGGREGATE");
        args.add(aggregate.name());
        return commandExecutor.writeAsync(getName(), LongCodec.INSTANCE, RedisCommands.ZUNIONSTORE_INT, args.toArray());
    }

    @Override
    public int union(Map<String, Double> nameWithWeight) {
        return get(unionAsync(nameWithWeight));        
    }
    
    @Override
    public RFuture<Integer> unionAsync(Map<String, Double> nameWithWeight) {
        return unionAsync(Aggregate.SUM, nameWithWeight);
    }

    @Override
    public int union(Aggregate aggregate, Map<String, Double> nameWithWeight) {
        return get(unionAsync(aggregate, nameWithWeight));        
    }

    @Override
    public RFuture<Integer> unionAsync(Aggregate aggregate, Map<String, Double> nameWithWeight) {
        List<Object> args = new ArrayList<Object>(nameWithWeight.size()*2 + 5);
        args.add(getName());
        args.add(nameWithWeight.size());
        args.addAll(nameWithWeight.keySet());
        args.add("WEIGHTS");
        List<String> weights = new ArrayList<String>();
        for (Double weight : nameWithWeight.values()) {
            weights.add(BigDecimal.valueOf(weight).toPlainString());
        }
        args.addAll(weights);
        args.add("AGGREGATE");
        args.add(aggregate.name());
        return commandExecutor.writeAsync(getName(), LongCodec.INSTANCE, RedisCommands.ZUNIONSTORE_INT, args.toArray());
    }

    @Override
    public Set<V> readSort(SortOrder order) {
        return get(readSortAsync(order));
    }
    
    @Override
    public RFuture<Set<V>> readSortAsync(SortOrder order) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.SORT_SET, getName(), order);
    }

    @Override
    public Set<V> readSort(SortOrder order, int offset, int count) {
        return get(readSortAsync(order, offset, count));
    }
    
    @Override
    public RFuture<Set<V>> readSortAsync(SortOrder order, int offset, int count) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.SORT_SET, getName(), "LIMIT", offset, count, order);
    }

    @Override
    public Set<V> readSort(String byPattern, SortOrder order) {
        return get(readSortAsync(byPattern, order));
    }
    
    @Override
    public RFuture<Set<V>> readSortAsync(String byPattern, SortOrder order) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.SORT_SET, getName(), "BY", byPattern, order);
    }
    
    @Override
    public Set<V> readSort(String byPattern, SortOrder order, int offset, int count) {
        return get(readSortAsync(byPattern, order, offset, count));
    }
    
    @Override
    public RFuture<Set<V>> readSortAsync(String byPattern, SortOrder order, int offset, int count) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.SORT_SET, getName(), "BY", byPattern, "LIMIT", offset, count, order);
    }

    @Override
    public <T> Collection<T> readSort(String byPattern, List<String> getPatterns, SortOrder order) {
        return (Collection<T>)get(readSortAsync(byPattern, getPatterns, order));
    }
    
    @Override
    public <T> RFuture<Collection<T>> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order) {
        return readSortAsync(byPattern, getPatterns, order, -1, -1);
    }
    
    @Override
    public <T> Collection<T> readSort(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return (Collection<T>)get(readSortAsync(byPattern, getPatterns, order, offset, count));
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        List<Object> params = new ArrayList<Object>();
        params.add(getName());
        if (byPattern != null) {
            params.add("BY");
            params.add(byPattern);
        }
        if (offset != -1 && count != -1) {
            params.add("LIMIT");
        }
        if (offset != -1) {
            params.add(offset);
        }
        if (count != -1) {
            params.add(count);
        }
        for (String pattern : getPatterns) {
            params.add("GET");
            params.add(pattern);
        }
        params.add(order);
        
        return commandExecutor.readAsync(getName(), codec, RedisCommands.SORT_SET, params.toArray());
    }
    
    @Override
    public int sortTo(String destName, SortOrder order) {
        return get(sortToAsync(destName, order));
    }
    
    @Override
    public RFuture<Integer> sortToAsync(String destName, SortOrder order) {
        return sortToAsync(destName, null, Collections.<String>emptyList(), order, -1, -1);
    }
    
    @Override
    public int sortTo(String destName, SortOrder order, int offset, int count) {
        return get(sortToAsync(destName, order, offset, count));
    }
    
    @Override
    public RFuture<Integer> sortToAsync(String destName, SortOrder order, int offset, int count) {
        return sortToAsync(destName, null, Collections.<String>emptyList(), order, offset, count);
    }

    @Override
    public int sortTo(String destName, String byPattern, SortOrder order, int offset, int count) {
        return get(sortToAsync(destName, byPattern, order, offset, count));
    }
    
    @Override
    public int sortTo(String destName, String byPattern, SortOrder order) {
        return get(sortToAsync(destName, byPattern, order));
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, SortOrder order) {
        return sortToAsync(destName, byPattern, Collections.<String>emptyList(), order, -1, -1);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, SortOrder order, int offset, int count) {
        return sortToAsync(destName, byPattern, Collections.<String>emptyList(), order, offset, count);
    }

    @Override
    public int sortTo(String destName, String byPattern, List<String> getPatterns, SortOrder order) {
        return get(sortToAsync(destName, byPattern, getPatterns, order));
    }
    
    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, List<String> getPatterns, SortOrder order) {
        return sortToAsync(destName, byPattern, getPatterns, order, -1, -1);
    }
    
    @Override
    public int sortTo(String destName, String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return get(sortToAsync(destName, byPattern, getPatterns, order, offset, count));
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        List<Object> params = new ArrayList<Object>();
        params.add(getName());
        if (byPattern != null) {
            params.add("BY");
            params.add(byPattern);
        }
        if (offset != -1 && count != -1) {
            params.add("LIMIT");
        }
        if (offset != -1) {
            params.add(offset);
        }
        if (count != -1) {
            params.add(count);
        }
        for (String pattern : getPatterns) {
            params.add("GET");
            params.add(pattern);
        }
        params.add(order);
        params.add("STORE");
        params.add(destName);
        
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SORT_TO, params.toArray());
    }
    
}
