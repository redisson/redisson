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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.redisson.client.protocol.BooleanReplayConvertor;
import org.redisson.client.protocol.LongReplayConvertor;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.StringCodec;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.Predicate;
import org.redisson.core.RMap;

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
//TODO implement watching by keys instead of map name
public class RedissonMap<K, V> extends RedissonExpirable implements RMap<K, V> {

    private final RedisCommand<Object> EVAL_PUT = new RedisCommand<Object>("EVAL", 4, ValueType.MAP, ValueType.MAP_VALUE);

    protected RedissonMap(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public int size() {
        Long res = connectionManager.read(getName(), RedisCommands.HLEN, getName());
        return res.intValue();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return connectionManager.read(getName(), RedisCommands.HEXISTS, getName(), key);
    }

    @Override
    public boolean containsValue(Object value) {
        Collection<V> list = values();
        return list.contains(value);
    }

    @Override
    public Map<K, V> getAll(Set<K> keys) {
        if (keys.size() == 0) {
            return Collections.emptyMap();
        }
        List<Object> args = new ArrayList<Object>(keys.size() + 1);
        args.add(getName());
        args.addAll(keys);
        List<V> list = connectionManager.read(getName(), RedisCommands.HMGET, args.toArray());

        Map<K, V> result = new HashMap<K, V>(list.size());
        for (int index = 0; index < args.size()-1; index++) {
            V value = list.get(index);
            if (value == null) {
                continue;
            }
            result.put((K) args.get(index+1), value);
        }
        return result;
    }

    @Override
    public V get(Object key) {
        return connectionManager.get(getAsync((K)key));
    }

    @Override
    public V put(K key, V value) {
        return connectionManager.get(putAsync(key, value));
    }

    @Override
    public V remove(Object key) {
        return connectionManager.get(removeAsync((K)key));
    }

    @Override
    public void putAll(final Map<? extends K, ? extends V> map) {
        if (map.size() == 0) {
            return;
        }

        connectionManager.write(getName(), RedisCommands.HMSET, getName(), map);
    }

    @Override
    public void clear() {
        delete();
    }

    @Override
    public Set<K> keySet() {
        List<K> keys = connectionManager.read(getName(), RedisCommands.HKEYS, getName());
        return new HashSet<K>(keys);
    }

    @Override
    public Collection<V> values() {
        return connectionManager.read(getName(), RedisCommands.HVALS, getName());
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        Map<K, V> map = connectionManager.read(getName(), RedisCommands.HGETALL, getName());
        return map.entrySet();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return connectionManager.get(putIfAbsentAsync(key, value));
    }

    @Override
    public Future<V> putIfAbsentAsync(K key, V value) {
        return connectionManager.evalAsync(EVAL_PUT,
                "if redis.call('hexists', KEYS[1], ARGV[1]) == 0 then redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); return nil else return redis.call('hget', KEYS[1], ARGV[1]) end",
                Collections.<Object>singletonList(getName()), key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return connectionManager.get(removeAsync(key, value)) == 1;
    }

    @Override
    public Future<Long> removeAsync(Object key, Object value) {
        return connectionManager.evalAsync(new RedisCommand<Long>("EVAL", new LongReplayConvertor(), 4, ValueType.MAP),
                "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then return redis.call('hdel', KEYS[1], ARGV[1]) else return 0 end",
            Collections.<Object>singletonList(getName()), key, value);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        return connectionManager.get(replaceAsync(key, oldValue, newValue));
    }

    @Override
    public Future<Boolean> replaceAsync(K key, V oldValue, V newValue) {
        return connectionManager.evalAsync(new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4,
                    Arrays.asList(ValueType.MAP_KEY, ValueType.MAP_VALUE, ValueType.MAP_VALUE)),
                "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then redis.call('hset', KEYS[1], ARGV[1], ARGV[3]); return true; else return false; end",
                Collections.<Object>singletonList(getName()), key, oldValue, newValue);
    }

    @Override
    public V replace(K key, V value) {
        return connectionManager.get(replaceAsync(key, value));
    }

    @Override
    public Future<V> replaceAsync(K key, V value) {
        return connectionManager.evalAsync(new RedisCommand<Object>("EVAL", 4, ValueType.MAP, ValueType.MAP_VALUE),
                "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then local v = redis.call('hget', KEYS[1], ARGV[1]); redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); return v; else return nil; end",
            Collections.<Object>singletonList(getName()), key, value);
    }

    @Override
    public Future<V> getAsync(K key) {
        return connectionManager.readAsync(getName(), RedisCommands.HGET, getName(), key);
    }

    @Override
    public Future<V> putAsync(K key, V value) {
        return connectionManager.evalAsync(EVAL_PUT,
                "local v = redis.call('hget', KEYS[1], ARGV[1]); redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); return v",
                Collections.<Object>singletonList(getName()), key, value);
    }


    @Override
    public Future<V> removeAsync(K key) {
        return connectionManager.evalAsync(new RedisCommand<Object>("EVAL", 4, ValueType.MAP_KEY, ValueType.MAP_VALUE),
                "local v = redis.call('hget', KEYS[1], ARGV[1]); redis.call('hdel', KEYS[1], ARGV[1]); return v",
                Collections.<Object>singletonList(getName()), key);
    }

    @Override
    public Future<Boolean> fastPutAsync(K key, V value) {
        return connectionManager.writeAsync(getName(), RedisCommands.HSET, getName(), key, value);
    }

    @Override
    public boolean fastPut(K key, V value) {
        return connectionManager.get(fastPutAsync(key, value));
    }

    @Override
    public Future<Long> fastRemoveAsync(final K ... keys) {
        if (keys == null || keys.length == 0) {
            return connectionManager.getGroup().next().newSucceededFuture(0L);
        }

        List<Object> args = new ArrayList<Object>(keys.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(keys));
        return connectionManager.writeAsync(getName(), RedisCommands.HDEL, args.toArray());
    }

    @Override
    public long fastRemove(K ... keys) {
        return connectionManager.get(fastRemoveAsync(keys));
    }

    private MapScanResult<Object, V> scanIterator(long startPos) {
        return connectionManager.read(getName(), RedisCommands.HSCAN, getName(), startPos);
    }

    private Iterator<Map.Entry<K, V>> iterator() {
        return new Iterator<Map.Entry<K, V>>() {

            private Iterator<Map.Entry<K, V>> iter;
            private long iterPos = 0;

            private boolean removeExecuted;
            private Map.Entry<K,V> value;

            @Override
            public boolean hasNext() {
                if (iter == null
                        || (!iter.hasNext() && iterPos != 0)) {
                    MapScanResult<Object, V> res = scanIterator(iterPos);
                    iter = ((Map<K, V>)res.getMap()).entrySet().iterator();
                    iterPos = res.getPos();
                }
                return iter.hasNext();
            }

            @Override
            public Map.Entry<K, V> next() {
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

                // lazy init iterator
                hasNext();
                iter.remove();
                RedissonMap.this.fastRemove(value.getKey());
                removeExecuted = true;
            }

        };
    }

    @Override
    public Map<K, V> filterKeys(Predicate<K> predicate) {
        Map<K, V> result = new HashMap<K, V>();
        for (Iterator<Map.Entry<K, V>> iterator = iterator(); iterator.hasNext();) {
            Map.Entry<K, V> entry = iterator.next();
            if (predicate.apply(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    @Override
    public Map<K, V> filterValues(Predicate<V> predicate) {
        Map<K, V> result = new HashMap<K, V>();
        for (Iterator<Map.Entry<K, V>> iterator = iterator(); iterator.hasNext();) {
            Map.Entry<K, V> entry = iterator.next();
            if (predicate.apply(entry.getValue())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public Map<K, V> filterEntries(Predicate<Map.Entry<K, V>> predicate) {
        Map<K, V> result = new HashMap<K, V>();
        for (Iterator<Map.Entry<K, V>> iterator = iterator(); iterator.hasNext();) {
            Map.Entry<K, V> entry = iterator.next();
            if (predicate.apply(entry)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    @Override
    public V addAndGet(K key, V value) {
        String res = connectionManager.write(getName(), StringCodec.INSTANCE,
                RedisCommands.HINCRBYFLOAT, getName(), key, new BigDecimal(value.toString()).toPlainString());

        if (value instanceof Long) {
            Object obj = Long.parseLong(res);
            return (V)obj;
        }
        if (value instanceof Integer) {
            Object obj = Integer.parseInt(res);
            return (V)obj;
        }
        if (value instanceof Float) {
            Object obj = Float.parseFloat(res);
            return (V)obj;
        }
        if (value instanceof Double) {
            Object obj = Double.parseDouble(res);
            return (V)obj;
        }
        if (value instanceof BigDecimal) {
            Object obj = new BigDecimal(res);
            return (V)obj;
        }
        throw new IllegalStateException("Wrong value type!");
    }

}
