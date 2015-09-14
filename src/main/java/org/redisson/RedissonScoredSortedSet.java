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

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.ScoredEntry;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.core.RScoredSortedSet;

import io.netty.util.concurrent.Future;

public class RedissonScoredSortedSet<V> extends RedissonExpirable implements RScoredSortedSet<V> {

    public RedissonScoredSortedSet(CommandExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    public RedissonScoredSortedSet(Codec codec, CommandExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    @Override
    public boolean add(double score, V object) {
        return get(addAsync(score, object));
    }

    public V first() {
        return get(firstAsync());
    }

    public Future<V> firstAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANGE_SINGLE, getName(), 0, 0);
    }

    public V last() {
        return get(lastAsync());
    }

    public Future<V> lastAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANGE_SINGLE, getName(), -1, -1);
    }

    @Override
    public Future<Boolean> addAsync(double score, V object) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.ZADD, getName(), BigDecimal.valueOf(score).toPlainString(), object);
    }

    @Override
    public boolean remove(Object object) {
        return get(removeAsync(object));
    }

    public int removeRangeByRank(int startIndex, int endIndex) {
        return get(removeRangeByRankAsync(startIndex, endIndex));
    }

    public Future<Integer> removeRangeByRankAsync(int startIndex, int endIndex) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZREMRANGEBYRANK, getName(), startIndex, endIndex);
    }

    public int removeRangeByScore(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        return get(removeRangeByScoreAsync(startScore, startScoreInclusive, endScore, endScoreInclusive));
    }

    public Future<Integer> removeRangeByScoreAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive) {
        String startValue = value(BigDecimal.valueOf(startScore).toPlainString(), startScoreInclusive);
        String endValue = value(BigDecimal.valueOf(endScore).toPlainString(), endScoreInclusive);
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZREMRANGEBYSCORE, getName(), startValue, endValue);
    }

    private String value(String element, boolean inclusive) {
        if (!inclusive) {
            element = "(" + element;
        }
        return element;
    }

    @Override
    public void clear() {
        delete();
    }

    @Override
    public Future<Boolean> removeAsync(Object object) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.ZREM, getName(), object);
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
    public Future<Integer> sizeAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZCARD, getName());
    }

    @Override
    public boolean contains(Object object) {
        return get(containsAsync(object));
    }

    @Override
    public Future<Boolean> containsAsync(Object o) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZSCORE_CONTAINS, getName(), o);
    }

    @Override
    public Double getScore(V o) {
        return get(getScoreAsync(o));
    }

    @Override
    public Future<Double> getScoreAsync(V o) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZSCORE, getName(), o);
    }

    @Override
    public int rank(V o) {
        return get(rankAsync(o));
    }

    @Override
    public Future<Integer> rankAsync(V o) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANK, getName(), o);
    }

    private ListScanResult<V> scanIterator(RedisClient client, long startPos) {
        return commandExecutor.read(client, getName(), codec, RedisCommands.ZSCAN, getName(), startPos);
    }

    @Override
    public Iterator<V> iterator() {
        return new Iterator<V>() {

            private Iterator<V> iter;
            private RedisClient client;
            private Long iterPos;

            private boolean removeExecuted;
            private V value;

            @Override
            public boolean hasNext() {
                if (iter == null) {
                    ListScanResult<V> res = scanIterator(null, 0);
                    client = res.getRedisClient();
                    iter = res.getValues().iterator();
                    iterPos = res.getPos();
                } else if (!iter.hasNext() && iterPos != 0) {
                    ListScanResult<V> res = scanIterator(client, iterPos);
                    iter = res.getValues().iterator();
                    iterPos = res.getPos();
                }
                return iter.hasNext();
            }

            @Override
            public V next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No such element at index");
                }

                value = iter.next();
                removeExecuted = false;
                return value;
            }

            @Override
            public void remove() {
                if (removeExecuted) {
                    throw new IllegalStateException("Element been already deleted");
                }

                iter.remove();
                RedissonScoredSortedSet.this.remove(value);
                removeExecuted = true;
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
    public Future<Boolean> containsAllAsync(Collection<?> c) {
        return commandExecutor.evalReadAsync(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4),
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
    public Future<Boolean> removeAllAsync(Collection<?> c) {
        return commandExecutor.evalWriteAsync(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4),
                        "local v = false " +
                        "for i = 0, table.getn(ARGV), 1 do "
                            + "if redis.call('zrem', KEYS[1], ARGV[i]) == 1 "
                            + "then v = true end "
                        +"end "
                       + "return v ",
                Collections.<Object>singletonList(getName()), c.toArray());
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
    public Future<Boolean> retainAllAsync(Collection<?> c) {
        return commandExecutor.evalWriteAsync(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4),
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
    public Double addScore(V object, Number value) {
        return get(addScoreAsync(object, value));
    }

    @Override
    public Future<Double> addScoreAsync(V object, Number value) {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZINCRBY,
                                   getName(), new BigDecimal(value.toString()).toPlainString(), object);
    }

    @Override
    public Collection<V> valueRange(int startIndex, int endIndex) {
        return get(valueRangeAsync(startIndex, endIndex));
    }

    @Override
    public Future<Collection<V>> valueRangeAsync(int startIndex, int endIndex) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANGE, getName(), startIndex, endIndex);
    }

    @Override
    public Collection<ScoredEntry<V>> entryRange(int startIndex, int endIndex) {
        return get(entryRangeAsync(startIndex, endIndex));
    }

    @Override
    public Future<Collection<ScoredEntry<V>>> entryRangeAsync(int startIndex, int endIndex) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZRANGE_ENTRY, getName(), startIndex, endIndex, "WITHSCORES");
    }

}
