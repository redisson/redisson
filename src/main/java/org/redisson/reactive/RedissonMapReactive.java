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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.reactivestreams.Publisher;
import org.redisson.api.RMapReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.convertor.LongReplayConvertor;
import org.redisson.client.protocol.convertor.NumberConvertor;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.command.CommandReactiveExecutor;
import org.redisson.connection.decoder.MapGetAllDecoder;

import reactor.fn.BiFunction;
import reactor.fn.Function;
import reactor.rx.Streams;


/**
 * Distributed and concurrent implementation of {@link java.util.concurrent.ConcurrentMap}
 * and {@link java.util.Map}
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonMapReactive<K, V> extends RedissonExpirableReactive implements RMapReactive<K, V> {

    private static final RedisCommand<Object> EVAL_REMOVE = new RedisCommand<Object>("EVAL", 4, ValueType.MAP_KEY, ValueType.MAP_VALUE);
    private static final RedisCommand<Object> EVAL_REPLACE = new RedisCommand<Object>("EVAL", 4, ValueType.MAP, ValueType.MAP_VALUE);
    private static final RedisCommand<Boolean> EVAL_REPLACE_VALUE = new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4, Arrays.asList(ValueType.MAP_KEY, ValueType.MAP_VALUE, ValueType.MAP_VALUE));
    private static final RedisCommand<Long> EVAL_REMOVE_VALUE = new RedisCommand<Long>("EVAL", new LongReplayConvertor(), 4, ValueType.MAP);
    private static final RedisCommand<Object> EVAL_PUT = EVAL_REPLACE;

    public RedissonMapReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    public RedissonMapReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    @Override
    public Publisher<Integer> size() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.HLEN, getName());
    }

    @Override
    public Publisher<Boolean> containsKey(Object key) {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.HEXISTS, getName(), key);
    }

    @Override
    public Publisher<Boolean> containsValue(Object value) {
        return commandExecutor.evalReadReactive(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4),
                "local s = redis.call('hvals', KEYS[1]);" +
                        "for i = 0, table.getn(s), 1 do "
                            + "if ARGV[1] == s[i] then "
                                + "return true "
                            + "end "
                       + "end;" +
                     "return false",
                Collections.<Object>singletonList(getName()), value);
    }

    @Override
    public Publisher<Map<K, V>> getAll(Set<K> keys) {
        if (keys.size() == 0) {
            return newSucceededObservable(Collections.<K, V>emptyMap());
        }
        List<Object> args = new ArrayList<Object>(keys.size() + 1);
        args.add(getName());
        args.addAll(keys);
        return commandExecutor.readReactive(getName(), codec, new RedisCommand<Map<Object, Object>>("HMGET", new MapGetAllDecoder(args), 2, ValueType.MAP_KEY, ValueType.MAP_VALUE), args.toArray());
    }

    public Publisher<Void> putAll(Map<? extends K, ? extends V> map) {
        if (map.isEmpty()) {
            return newSucceededObservable(null);
        }

        List<Object> params = new ArrayList<Object>(map.size()*2 + 1);
        params.add(getName());
        for (java.util.Map.Entry<? extends K, ? extends V> t : map.entrySet()) {
            params.add(t.getKey());
            params.add(t.getValue());
        }

        return commandExecutor.writeReactive(getName(), codec, RedisCommands.HMSET, params.toArray());
    }

    @Override
    public Publisher<Set<K>> keySet() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.HKEYS, getName());
    }

    @Override
    public Publisher<Collection<V>> values() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.HVALS, getName());
    }

    @Override
    public Publisher<V> putIfAbsent(K key, V value) {
        return commandExecutor.evalWriteReactive(getName(), codec, EVAL_PUT,
                "if redis.call('hexists', KEYS[1], ARGV[1]) == 0 then "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); "
                    + "return nil "
                + "else "
                    + "return redis.call('hget', KEYS[1], ARGV[1]) "
                + "end",
                Collections.<Object>singletonList(getName()), key, value);
    }

    @Override
    public Publisher<Long> remove(Object key, Object value) {
        return commandExecutor.evalWriteReactive(getName(), codec, EVAL_REMOVE_VALUE,
                "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then "
                        + "return redis.call('hdel', KEYS[1], ARGV[1]) "
                + "else "
                    + "return 0 "
                + "end",
            Collections.<Object>singletonList(getName()), key, value);
    }

    @Override
    public Publisher<Boolean> replace(K key, V oldValue, V newValue) {
        return commandExecutor.evalWriteReactive(getName(), codec, EVAL_REPLACE_VALUE,
                "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[3]); "
                    + "return true; "
                + "else "
                    + "return false; "
                + "end",
                Collections.<Object>singletonList(getName()), key, oldValue, newValue);
    }

    @Override
    public Publisher<V> replace(K key, V value) {
        return commandExecutor.evalWriteReactive(getName(), codec, EVAL_REPLACE,
                "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then "
                    + "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); "
                    + "return v; "
                + "else "
                    + "return nil; "
                + "end",
            Collections.<Object>singletonList(getName()), key, value);
    }

    @Override
    public Publisher<V> get(K key) {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.HGET, getName(), key);
    }

    @Override
    public Publisher<V> put(K key, V value) {
        return commandExecutor.evalWriteReactive(getName(), codec, EVAL_PUT,
                "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); "
                + "return v",
                Collections.<Object>singletonList(getName()), key, value);
    }


    @Override
    public Publisher<V> remove(K key) {
        return commandExecutor.evalWriteReactive(getName(), codec, EVAL_REMOVE,
                "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                + "redis.call('hdel', KEYS[1], ARGV[1]); "
                + "return v",
                Collections.<Object>singletonList(getName()), key);
    }

    @Override
    public Publisher<Boolean> fastPut(K key, V value) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.HSET, getName(), key, value);
    }

    @Override
    public Publisher<Long> fastRemove(K ... keys) {
        if (keys == null || keys.length == 0) {
            return newSucceededObservable(0L);
        }

        List<Object> args = new ArrayList<Object>(keys.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(keys));
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.HDEL, args.toArray());
    }

    Publisher<MapScanResult<Object, V>> scanIteratorReactive(InetSocketAddress client, long startPos) {
        return commandExecutor.readReactive(client, getName(), codec, RedisCommands.HSCAN, getName(), startPos);
    }

    @Override
    public Publisher<Map.Entry<K, V>> entryIterator() {
        return new RedissonMapReactiveIterator<K, V, Map.Entry<K, V>>(this).stream();
    }

    @Override
    public Publisher<V> valueIterator() {
        return new RedissonMapReactiveIterator<K, V, V>(this) {
            @Override
            V getValue(java.util.Map.Entry<K, V> entry) {
                return entry.getValue();
            }
        }.stream();
    }

    @Override
    public Publisher<K> keyIterator() {
        return new RedissonMapReactiveIterator<K, V, K>(this) {
            @Override
            K getValue(java.util.Map.Entry<K, V> entry) {
                return entry.getKey();
            }
        }.stream();
    }

    @Override
    public Publisher<V> addAndGet(K key, Number value) {
        return commandExecutor.writeReactive(getName(), StringCodec.INSTANCE,
                new RedisCommand<Object>("HINCRBYFLOAT", new NumberConvertor(value.getClass())),
                   getName(), key, new BigDecimal(value.toString()).toPlainString());
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (o instanceof Map) {
            final Map<?,?> m = (Map<?,?>) o;
            if (m.size() != Streams.create(size()).next().poll()) {
                return false;
            }

            return Streams.create(entryIterator()).map(mapFunction(m)).reduce(true, booleanAnd()).next().poll();
        } else if (o instanceof RMapReactive) {
            final RMapReactive<Object, Object> m = (RMapReactive<Object, Object>) o;
            if (Streams.create(m.size()).next().poll() != Streams.create(size()).next().poll()) {
                return false;
            }

            return Streams.create(entryIterator()).map(mapFunction(m)).reduce(true, booleanAnd()).next().poll();
        }

        return true;
    }

    private BiFunction<Boolean, Boolean, Boolean> booleanAnd() {
        return new BiFunction<Boolean, Boolean, Boolean>() {

            @Override
            public Boolean apply(Boolean t, Boolean u) {
                return t & u;
            }
        };
    }

    private Function<Entry<K, V>, Boolean> mapFunction(final Map<?, ?> m) {
        return new Function<Map.Entry<K, V>, Boolean>() {
            @Override
            public Boolean apply(Entry<K, V> e) {
                K key = e.getKey();
                V value = e.getValue();
                if (value == null) {
                    if (!(m.get(key)==null && m.containsKey(key)))
                        return false;
                } else {
                    if (!value.equals(m.get(key)))
                        return false;
                }
                return true;
            }
        };
    }

    private Function<Entry<K, V>, Boolean> mapFunction(final RMapReactive<Object, Object> m) {
        return new Function<Map.Entry<K, V>, Boolean>() {
            @Override
            public Boolean apply(Entry<K, V> e) {
                Object key = e.getKey();
                Object value = e.getValue();
                if (value == null) {
                    if (!(Streams.create(m.get(key)).next().poll() ==null && Streams.create(m.containsKey(key)).next().poll()))
                        return false;
                } else {
                    if (!value.equals(Streams.create(m.get(key)).next().poll()))
                        return false;
                }
                return true;
            }
        };
    }

    @Override
    public int hashCode() {
        return Streams.create(entryIterator()).map(new Function<Map.Entry<K, V>, Integer>() {
            @Override
            public Integer apply(Entry<K, V> t) {
                return t.hashCode();
            }
        }).reduce(0, new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer apply(Integer t, Integer u) {
                return t + u;
            }
        }).next().poll();
    }

}
