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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;
import org.redisson.EvictionScheduler;
import org.redisson.api.RSetCacheReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.convertor.VoidReplayConvertor;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.command.CommandReactiveExecutor;

import net.openhft.hashing.LongHashFunction;

/**
 * <p>Set-based cache with ability to set TTL for each entry via
 * {@link #put(Object, Object, long, TimeUnit)} method.
 * And therefore has an complex lua-scripts inside.
 * Uses map(value_hash, value) to tie with sorted set which contains expiration record for every value with TTL.
 * </p>
 *
 * <p>Current Redis implementation doesn't have set entry eviction functionality.
 * Thus values are checked for TTL expiration during any value read operation.
 * If entry expired then it doesn't returns and clean task runs hronous.
 * Clean task deletes removes 100 expired entries at once.
 * In addition there is {@link org.redisson.EvictionScheduler}. This scheduler
 * deletes expired entries in time interval between 5 seconds to 2 hours.</p>
 *
 * <p>If eviction is not required then it's better to use {@link org.redisson.reactive.RedissonSet}.</p>
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonSetCacheReactive<V> extends RedissonExpirableReactive implements RSetCacheReactive<V> {

    private static final RedisCommand<Void> ADD_ALL = new RedisCommand<Void>("HMSET", new VoidReplayConvertor());
    private static final RedisStrictCommand<Boolean> HDEL = new RedisStrictCommand<Boolean>("HDEL", new BooleanReplayConvertor());

    public RedissonSetCacheReactive(EvictionScheduler evictionScheduler, CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        evictionScheduler.schedule(getName(), getTimeoutSetName());
    }

    public RedissonSetCacheReactive(Codec codec, EvictionScheduler evictionScheduler, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        evictionScheduler.schedule(getName(), getTimeoutSetName());
    }

    @Override
    public Publisher<Long> size() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.HLEN_LONG, getName());
    }

    private byte[] hash(Object o) {
        if (o == null) {
            throw new NullPointerException("Value can't be null");
        }
        try {
            byte[] objectState = codec.getValueEncoder().encode(o);
            return hash(objectState);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private byte[] hash(byte[] objectState) {
        long h1 = LongHashFunction.farmUo().hashBytes(objectState);
        long h2 = LongHashFunction.xx_r39().hashBytes(objectState);

        return ByteBuffer.allocate((2 * Long.SIZE) / Byte.SIZE)
                .putLong(h1)
                .putLong(h2)
                .array();
    }

    String getTimeoutSetName() {
        return "redisson__timeout__set__{" + getName() + "}";
    }

    @Override
    public Publisher<Boolean> contains(Object o) {
        byte[] key = hash(o);

        return commandExecutor.evalReadReactive(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local value = redis.call('hexists', KEYS[1], ARGV[2]); " +
                "if value == 1 then " +
                    "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); "
                    + "if expireDateScore ~= false and tonumber(expireDateScore) <= tonumber(ARGV[1]) then "
                        + "return 0;"
                    + "end; " +
                "end;" +
                "return value; ",
               Arrays.<Object>asList(getName(), getTimeoutSetName()), System.currentTimeMillis(), key);
    }

    Publisher<ListScanResult<V>> scanIterator(InetSocketAddress client, long startPos) {
        return commandExecutor.evalReadReactive(client, getName(), codec, RedisCommands.EVAL_SSCAN,
                  "local result = {}; "
                + "local res = redis.call('hscan', KEYS[1], ARGV[1]); "
                + "for i, value in ipairs(res[2]) do "
                    + "if i % 2 == 0 then "
                        + "local key = res[2][i-1]; "
                        + "local expireDate = redis.call('zscore', KEYS[2], key); "
                        + "if (expireDate == false) or (expireDate ~= false and tonumber(expireDate) > tonumber(ARGV[2])) then "
                            + "table.insert(result, value); "
                        + "end; "
                    + "end; "
                + "end;"
                + "return {res[1], result};", Arrays.<Object>asList(getName(), getTimeoutSetName()), startPos, System.currentTimeMillis());
    }

    @Override
    public Publisher<V> iterator() {
        return new SetReactiveIterator<V>() {
            @Override
            protected Publisher<ListScanResult<V>> scanIteratorReactive(InetSocketAddress client, long nextIterPos) {
                return RedissonSetCacheReactive.this.scanIterator(client, nextIterPos);
            }
        };
    }

    @Override
    public Publisher<Boolean> add(V value, long ttl, TimeUnit unit) {
        if (ttl < 0) {
            throw new IllegalArgumentException("TTL can't be negative");
        }
        if (ttl == 0) {
            try {
                byte[] objectState = encode(value);
                byte[] key = hash(objectState);
                return commandExecutor.evalWriteReactive(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                        "if redis.call('hexists', KEYS[1], ARGV[1]) == 0 then " +
                                "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                                "return 1; " +
                                "end; " +
                                "return 0; ",
                                Arrays.<Object>asList(getName()), key, objectState);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (unit == null) {
            throw new NullPointerException("TimeUnit param can't be null");
        }

        try {
            byte[] objectState = encode(value);
            byte[] key = hash(objectState);

            long timeoutDate = System.currentTimeMillis() + unit.toMillis(ttl);
            return commandExecutor.evalWriteReactive(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "redis.call('zadd', KEYS[2], ARGV[1], ARGV[3]); " +
                            "if redis.call('hexists', KEYS[1], ARGV[3]) == 0 then " +
                            "redis.call('hset', KEYS[1], ARGV[3], ARGV[2]); " +
                            "return 1; " +
                            "end;" +
                            "return 0; ",
                            Arrays.<Object>asList(getName(), getTimeoutSetName()), timeoutDate, objectState, key);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] encode(V value) throws IOException {
        return codec.getValueEncoder().encode(value);
    }

    @Override
    public Publisher<Long> add(V value) {
        try {
            byte[] objectState = encode(value);
            byte[] key = hash(objectState);
            return commandExecutor.evalWriteReactive(getName(), codec, RedisCommands.EVAL_LONG,
                    "if redis.call('hexists', KEYS[1], ARGV[1]) == 0 then " +
                        "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                        "return 1; " +
                    "end; " +
                    "return 0; ",
                Arrays.<Object>asList(getName()), key, objectState);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Publisher<Boolean> remove(Object o) {
        byte[] key = hash(o);
        return commandExecutor.writeReactive(getName(), codec, HDEL, getName(), key);
    }

    @Override
    public Publisher<Boolean> containsAll(Collection<?> c) {
        return commandExecutor.evalReadReactive(getName(), codec, RedisCommands.EVAL_BOOLEAN_WITH_VALUES,
                "local s = redis.call('hvals', KEYS[1]);" +
                        "for i = 1, table.getn(s), 1 do " +
                            "for j = 1, table.getn(ARGV), 1 do "
                            + "if ARGV[j] == s[i] then "
                                + "table.remove(ARGV, j) "
                            + "end "
                        + "end; "
                       + "end;"
                       + "return table.getn(ARGV) == 0 and 1 or 0; ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public Publisher<Long> addAll(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return newSucceeded(0L);
        }

        List<Object> params = new ArrayList<Object>(c.size()*2 + 1);
        params.add(getName());
        try {
            for (V value : c) {
                byte[] objectState = encode(value);
                byte[] key = hash(objectState);
                params.add(key);
                params.add(objectState);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return commandExecutor.writeReactive(getName(), codec, ADD_ALL, params.toArray());
    }

    @Override
    public Publisher<Boolean> retainAll(Collection<?> c) {
        List<byte[]> params = new ArrayList<byte[]>(c.size());
        for (Object object : c) {
            params.add(hash(object));
        }
        return commandExecutor.evalWriteReactive(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "local keys = redis.call('hkeys', KEYS[1]); " +
                    "local i=1;" +
                    "while i <= #keys do "
                        + "local changed = false;"
                        + "local element = keys[i];"
                        + "for j, argElement in pairs(ARGV) do "
                            + "if argElement == element then "
                                + "changed = true;"
                                + "table.remove(keys, i); "
                                + "table.remove(ARGV, j); "
                                + "break; "
                            + "end; "
                        + "end; " +
                        "if changed == false then " +
                            "i = i + 1 " +
                        "end " +
                    "end " +
                   "if #keys > 0 then "
                       + "for i=1, #keys,5000 do "
                           + "redis.call('hdel', KEYS[1], unpack(keys, i, math.min(i+4999, #keys))); "
                           + "redis.call('zrem', KEYS[2], unpack(keys, i, math.min(i+4999, #keys))); "
                       + "end "
                       + "return 1;"
                   + "end; "
                   + "return 0; ",
                   Arrays.<Object>asList(getName(), getTimeoutSetName()), params.toArray());
    }

    @Override
    public Publisher<Boolean> removeAll(Collection<?> c) {
        List<Object> params = new ArrayList<Object>(c.size()+1);
        params.add(getName());
        for (Object object : c) {
            params.add(hash(object));
        }

        return commandExecutor.writeReactive(getName(), codec, HDEL, params.toArray());
    }

    @Override
    public Publisher<Boolean> delete() {
        return commandExecutor.writeReactive(getName(), RedisCommands.DEL_OBJECTS, getName(), getTimeoutSetName());
    }

    @Override
    public Publisher<Boolean> expire(long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.evalWriteReactive(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('zadd', KEYS[2], 92233720368547758, 'redisson__expiretag');" +
                "redis.call('pexpire', KEYS[2], ARGV[1]); "
                + "return redis.call('pexpire', KEYS[1], ARGV[1]); ",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), timeUnit.toMillis(timeToLive));
    }

    @Override
    public Publisher<Boolean> expireAt(long timestamp) {
        return commandExecutor.evalWriteReactive(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('zadd', KEYS[2], 92233720368547758, 'redisson__expiretag');" +
                "redis.call('pexpireat', KEYS[2], ARGV[1]); "
                + "return redis.call('pexpireat', KEYS[1], ARGV[1]); ",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), timestamp);
    }

    @Override
    public Publisher<Boolean> clearExpire() {
        return commandExecutor.evalWriteReactive(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('zrem', KEYS[2], 'redisson__expiretag'); " +
                "redis.call('persist', KEYS[2]); "
                + "return redis.call('persist', KEYS[1]); ",
                Arrays.<Object>asList(getName(), getTimeoutSetName()));
    }

    @Override
    public Publisher<Long> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<V>(this).addAll(c);
    }

}
