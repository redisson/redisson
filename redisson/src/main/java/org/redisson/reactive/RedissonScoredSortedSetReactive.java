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

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;

import org.reactivestreams.Publisher;
import org.redisson.api.RScoredSortedSetReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.ScoredEntry;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandReactiveExecutor;

public class RedissonScoredSortedSetReactive<V> extends RedissonExpirableReactive implements RScoredSortedSetReactive<V> {

    public RedissonScoredSortedSetReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    public RedissonScoredSortedSetReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    @Override
    public Publisher<V> pollFirst() {
        return poll(0);
    }

    @Override
    public Publisher<V> pollLast() {
        return poll(-1);
    }

    private Publisher<V> poll(int index) {
        return commandExecutor.evalWriteReactive(getName(), codec, RedisCommands.EVAL_OBJECT,
                "local v = redis.call('zrange', KEYS[1], ARGV[1], ARGV[2]); "
                + "if v[1] ~= nil then "
                    + "redis.call('zremrangebyrank', KEYS[1], ARGV[1], ARGV[2]); "
                    + "return v[1]; "
                + "end "
                + "return nil;",
                Collections.<Object>singletonList(getName()), index, index);
    }

    @Override
    public Publisher<V> first() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZRANGE_SINGLE, getName(), 0, 0);
    }

    @Override
    public Publisher<V> last() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZRANGE_SINGLE, getName(), -1, -1);
    }

    @Override
    public Publisher<Boolean> add(double score, V object) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.ZADD_BOOL, getName(), BigDecimal.valueOf(score).toPlainString(), object);
    }

    @Override
    public Publisher<Integer> removeRangeByRank(int startIndex, int endIndex) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.ZREMRANGEBYRANK, getName(), startIndex, endIndex);
    }

    @Override
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
        return commandExecutor.readReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZSCORE, getName(), o);
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
        return new SetReactiveIterator<V>() {
            @Override
            protected Publisher<ListScanResult<V>> scanIteratorReactive(InetSocketAddress client, long nextIterPos) {
                return RedissonScoredSortedSetReactive.this.scanIteratorReactive(client, nextIterPos);
            }
        };
    }

    @Override
    public Publisher<Boolean> containsAll(Collection<?> c) {
        return commandExecutor.evalReadReactive(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4, ValueType.OBJECTS),
                "local s = redis.call('zrange', KEYS[1], 0, -1);" +
                        "for i = 1, table.getn(s), 1 do " +
                            "for j = 1, table.getn(ARGV), 1 do "
                            + "if ARGV[j] == s[i] "
                            + "then table.remove(ARGV, j) end "
                        + "end; "
                       + "end;"
                       + "return table.getn(ARGV) == 0 and 1 or 0; ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public Publisher<Boolean> removeAll(Collection<?> c) {
        return commandExecutor.evalWriteReactive(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4, ValueType.OBJECTS),
                        "local v = 0 " +
                        "for i = 1, table.getn(ARGV), 1 do "
                            + "if redis.call('zrem', KEYS[1], ARGV[i]) == 1 "
                            + "then v = 1 end "
                        +"end "
                       + "return v ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public Publisher<Boolean> retainAll(Collection<?> c) {
        return commandExecutor.evalWriteReactive(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4, ValueType.OBJECTS),
                    "local changed = 0 " +
                    "local s = redis.call('zrange', KEYS[1], 0, -1) "
                       + "local i = 1 "
                       + "while i <= table.getn(s) do "
                            + "local element = s[i] "
                            + "local isInAgrs = false "
                            + "for j = 1, table.getn(ARGV), 1 do "
                                + "if ARGV[j] == element then "
                                    + "isInAgrs = true "
                                    + "break "
                                + "end "
                            + "end "
                            + "if isInAgrs == false then "
                                + "redis.call('zrem', KEYS[1], element) "
                                + "changed = 1 "
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
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZRANGEBYSCORE_LIST, getName(), startValue, endValue);
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
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZRANGEBYSCORE_LIST, getName(), startValue, endValue, "LIMIT", offset, count);
    }

    @Override
    public Publisher<Collection<ScoredEntry<V>>> entryRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count) {
        String startValue = value(BigDecimal.valueOf(startScore).toPlainString(), startScoreInclusive);
        String endValue = value(BigDecimal.valueOf(endScore).toPlainString(), endScoreInclusive);
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZRANGEBYSCORE_ENTRY, getName(), startValue, endValue, "WITHSCORES", "LIMIT", offset, count);
    }

}
