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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.convertor.LongReplayConvertor;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectMapReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectSetReplayDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.decoder.MapGetAllDecoder;
import org.redisson.core.RCache;

import io.netty.util.concurrent.Future;

/**
 * Distributed and concurrent implementation of {@link java.util.concurrent.ConcurrentMap}
 * and {@link java.util.Map}
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
// TODO override expire methods
public class RedissonCache<K, V> extends RedissonMap<K, V> implements RCache<K, V> {

    private static final RedisCommand<Object> EVAL_REMOVE = new RedisCommand<Object>("EVAL", 4, ValueType.MAP_KEY, ValueType.MAP_VALUE);
    private static final RedisCommand<Object> EVAL_REPLACE = new RedisCommand<Object>("EVAL", 4, ValueType.MAP, ValueType.MAP_VALUE);
    private static final RedisCommand<Long> EVAL_REMOVE_VALUE = new RedisCommand<Long>("EVAL", new LongReplayConvertor(), 5, ValueType.MAP);
    private static final RedisCommand<Object> EVAL_PUT = EVAL_REPLACE;
    private static final RedisCommand<Object> EVAL_PUT_TTL = new RedisCommand<Object>("EVAL", 6, ValueType.MAP, ValueType.MAP_VALUE);
    private static final RedisCommand<Object> EVAL_GET_TTL = new RedisCommand<Object>("EVAL", 6, ValueType.MAP_KEY, ValueType.MAP_VALUE);

    private static final RedisCommand<Boolean> EVAL_CONTAINS = new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 6);
    private static final RedisCommand<Set<Object>> EVAL_HKEYS = new RedisCommand<Set<Object>>("EVAL", new ObjectSetReplayDecoder(), ValueType.MAP_KEY);
    private static final RedisCommand<List<Object>> EVAL_HVALS = new RedisCommand<List<Object>>("EVAL", new ObjectListReplayDecoder<Object>(), ValueType.MAP_VALUE);
    private static final RedisCommand<Map<Object, Object>> EVAL_HGETALL = new RedisCommand<Map<Object, Object>>("EVAL", new ObjectMapReplayDecoder(), ValueType.MAP);
    private static final RedisCommand<Long> EVAL_FAST_REMOVE = new RedisCommand<Long>("EVAL", 2);

    protected RedissonCache(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    public RedissonCache(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    @Override
    public Future<Integer> sizeAsync() {
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_INTEGER,
                  "local expiredKeys = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1]); "
                + "if table.getn(expiredKeys) > 0 then "
                    + "expiredKeys = unpack(expiredKeys); "
                    + "redis.call('zrem', KEYS[2], expiredKeys); "
                    + "redis.call('hdel', KEYS[1], expiredKeys); "
                + "end; "
              + "return redis.call('hlen', KEYS[1]);",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), System.currentTimeMillis());
    }

    @Override
    public Future<Boolean> containsKeyAsync(Object key) {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_CONTAINS,
                "local v = redis.call('hexists', KEYS[1], ARGV[2]); "
                + "if v == 1 then "
                    + "local expireDate = redis.call('zscore', KEYS[2], ARGV[2]); "
                    + "if expireDate ~= false and expireDate <= ARGV[1] then "
                        + "redis.call('zrem', KEYS[2], ARGV[2]); "
                        + "redis.call('hdel', KEYS[1], ARGV[2]); "
                        + "return false;"
                    + "end;"
                + "return true;"
                + "end;"
                + "return false;",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), System.currentTimeMillis(), key);
    }

    @Override
    public Future<Boolean> containsValueAsync(Object value) {
        return commandExecutor.evalWriteAsync(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 6),
                  "local expireSize = redis.call('zcard', KEYS[2])"
                + "local s = redis.call('hgetall', KEYS[1]);" +
                        "for i, v in ipairs(s) do "
                            + "if ARGV[2] == v and i % 2 == 0 then "
                                + "if expireSize > 0 then "
                                    + "local key = s[i-1];"
                                    + "local expireDate = redis.call('zscore', KEYS[2], key); "
                                    + "if expireDate ~= false and expireDate <= ARGV[1] then "
                                        + "redis.call('zrem', KEYS[2], key); "
                                        + "redis.call('hdel', KEYS[1], key); "
                                        + "return false;"
                                    + "end;"
                                + "end;"
                                + "return true "
                            + "end "
                       + "end;" +
                     "return false",
                 Arrays.<Object>asList(getName(), getTimeoutSetName()), System.currentTimeMillis(), value);
    }

    @Override
    public Future<Map<K, V>> getAllAsync(Set<K> keys) {
        if (keys.isEmpty()) {
            return newSucceededFuture(Collections.<K, V>emptyMap());
        }

        List<Object> args = new ArrayList<Object>(keys.size() + 2);
        args.add(System.currentTimeMillis());
        args.addAll(keys);
        return commandExecutor.evalWriteAsync(getName(), codec, new RedisCommand<Map<Object, Object>>("EVAL", new MapGetAllDecoder(args), 6, ValueType.MAP_KEY, ValueType.MAP_VALUE),
                "local expireSize = redis.call('zcard', KEYS[2])" +
                        "local maxDate = table.remove(ARGV, 1); " + // index is the first parameter
                        "if expireSize > 0 then "
                        + "for i, key in ipairs(ARGV) do "
                            + "local expireDate = redis.call('zscore', KEYS[2], key); "
                            + "if expireDate ~= false and expireDate <= maxDate then "
                                + "redis.call('zrem', KEYS[2], key); "
                                + "redis.call('hdel', KEYS[1], key); "
                            + "end;"
                        + "end;"
                      + "end;" +
                       "return redis.call('hmget', KEYS[1], unpack(ARGV));",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), args.toArray());
    }

    @Override
    public Future<Set<K>> keySetAsync() {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_HKEYS,
                "local expiredKeys = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1]); "
                + "if table.getn(expiredKeys) > 0 then "
                    + "expiredKeys = unpack(expiredKeys); "
                    + "redis.call('zrem', KEYS[2], expiredKeys); "
                    + "redis.call('hdel', KEYS[1], expiredKeys); "
                + "end; "
                + "return redis.call('hkeys', KEYS[1]);",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), System.currentTimeMillis());
    }

    @Override
    public Future<Collection<V>> valuesAsync() {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_HVALS,
                "local expiredKeys = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1]); "
                + "if table.getn(expiredKeys) > 0 then "
                    + "expiredKeys = unpack(expiredKeys); "
                    + "redis.call('zrem', KEYS[2], expiredKeys); "
                    + "redis.call('hdel', KEYS[1], expiredKeys); "
                + "end; "
                + "return redis.call('hvals', KEYS[1]);",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), System.currentTimeMillis());
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        Future<Map<K, V>> f = commandExecutor.evalWriteAsync(getName(), codec, EVAL_HGETALL,
                "local expiredKeys = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1]); "
                + "if table.getn(expiredKeys) > 0 then "
                    + "expiredKeys = unpack(expiredKeys); "
                    + "redis.call('zrem', KEYS[2], expiredKeys); "
                    + "redis.call('hdel', KEYS[1], expiredKeys); "
                + "end; "
                + "return redis.call('hgetall', KEYS[1]);",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), System.currentTimeMillis());

        Map<K, V> map = get(f);
        return map.entrySet();
    }

    public V putIfAbsent(K key, V value, long ttl, TimeUnit unit) {
        return get(putIfAbsent(key, value, ttl, unit));
    }

    public Future<V> putIfAbsentAsync(K key, V value, long ttl, TimeUnit unit) {
        if (unit == null) {
            throw new NullPointerException("TimeUnit param can't be null");
        }

        long timeoutDate = System.currentTimeMillis() + unit.toMillis(ttl);
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_PUT,
                "if redis.call('hexists', KEYS[1], ARGV[1]) == 0 then "
                    + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[2]); "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); "
                    + "return nil "
                + "else "
                    + "return redis.call('hget', KEYS[1], ARGV[1]) "
                + "end",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), timeoutDate, key, value);
    }

    @Override
    public Future<Long> removeAsync(Object key, Object value) {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_REMOVE_VALUE,
                "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then "
                        + "redis.call('zrem', KEYS[2], ARGV[1]); "
                        + "return redis.call('hdel', KEYS[1], ARGV[1]); "
                + "else "
                    + "return 0 "
                + "end",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), key, value);
    }

    public Future<V> getAsync(K key) {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_GET_TTL,
                "local v = redis.call('hget', KEYS[1], ARGV[2]); "
                + "local expireDate = redis.call('zscore', KEYS[2], ARGV[2]); "
                + "if expireDate ~= false and expireDate <= ARGV[1] then "
                    + "redis.call('zrem', KEYS[2], ARGV[2]); "
                    + "redis.call('hdel', KEYS[1], ARGV[2]); "
                    + "return nil;"
                + "end;"
                + "return v;",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), System.currentTimeMillis(), key);
    }

    public V put(K key, V value, long ttl, TimeUnit unit) {
        return get(putAsync(key, value, ttl, unit));
    }

    public Future<V> putAsync(K key, V value, long ttl, TimeUnit unit) {
        if (unit == null) {
            throw new NullPointerException("TimeUnit param can't be null");
        }

        long timeoutDate = System.currentTimeMillis() + unit.toMillis(ttl);
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_PUT_TTL,
                "local v = redis.call('hget', KEYS[1], ARGV[2]); "
                + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[2]); "
                + "redis.call('hset', KEYS[1], ARGV[2], ARGV[3]); "
                + "return v",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), timeoutDate, key, value);
    }

    String getTimeoutSetName() {
        return "redisson__timeout__set__{" + getName() + "}";
    }


    @Override
    public Future<V> removeAsync(K key) {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_REMOVE,
                "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                + "redis.call('zrem', KEYS[2], ARGV[1]); "
                + "redis.call('hdel', KEYS[1], ARGV[1]); "
                + "return v",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), key);
    }

    @Override
    public Future<Long> fastRemoveAsync(K ... keys) {
        if (keys == null || keys.length == 0) {
            return newSucceededFuture(0L);
        }

        List<Object> args = new ArrayList<Object>(keys.length);
        args.addAll(Arrays.asList(keys));
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_FAST_REMOVE,
                "redis.call('zrem', KEYS[2], unpack(ARGV)); "
                + "return redis.call('hdel', KEYS[1], unpack(ARGV)); ",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), args.toArray());
    }

    MapScanResult<Object, V> scanIterator(InetSocketAddress client, long startPos) {
        Future<MapScanResult<Object, V>> f = commandExecutor.readAsync(client, getName(), codec, RedisCommands.HSCAN, getName(), startPos);
        return get(f);
    }

//    @Override
//    public Iterator<Map.Entry<K, V>> entryIterator() {
//        return new RedissonMapIterator<K, V, Map.Entry<K, V>>(this);
//    }
//
//    @Override
//    public Iterator<V> valueIterator() {
//        return new RedissonMapIterator<K, V, V>(this) {
//            @Override
//            V getValue(java.util.Map.Entry<K, V> entry) {
//                return entry.getValue();
//            }
//        };
//    }
//
//    @Override
//    public Iterator<K> keyIterator() {
//        return new RedissonMapIterator<K, V, K>(this) {
//            @Override
//            K getValue(java.util.Map.Entry<K, V> entry) {
//                return entry.getKey();
//            }
//        };
//    }


    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof Map))
            return false;
        Map<?,?> m = (Map<?,?>) o;
        if (m.size() != size())
            return false;

        try {
            Iterator<Entry<K,V>> i = entrySet().iterator();
            while (i.hasNext()) {
                Entry<K,V> e = i.next();
                K key = e.getKey();
                V value = e.getValue();
                if (value == null) {
                    if (!(m.get(key)==null && m.containsKey(key)))
                        return false;
                } else {
                    if (!value.equals(m.get(key)))
                        return false;
                }
            }
        } catch (ClassCastException unused) {
            return false;
        } catch (NullPointerException unused) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        Iterator<Entry<K,V>> i = entrySet().iterator();
        while (i.hasNext())
            h += i.next().hashCode();
        return h;
    }

    @Override
    public Future<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_SINGLE, getName(), getTimeoutSetName());
    }

}
