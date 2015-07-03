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

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.math.BigDecimal;
import java.util.*;

import org.redisson.async.AsyncOperation;
import org.redisson.async.OperationListener;
import org.redisson.async.ResultOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.Predicate;
import org.redisson.core.RMap;
import org.redisson.core.RScript;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.output.MapScanResult;

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

    protected RedissonMap(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public int size() {
        return connectionManager.read(getName(), new ResultOperation<Long, V>() {
            @Override
            public Future<Long> execute(RedisAsyncConnection<Object, V> async) {
                return async.hlen(getName());
            }
        }).intValue();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(final Object key) {
        return connectionManager.read(getName(), new ResultOperation<Boolean, V>() {
            @Override
            public Future<Boolean> execute(RedisAsyncConnection<Object, V> async) {
                return async.hexists(getName(), key);
            }
        });
    }

    @Override
    public boolean containsValue(final Object value) {
        List<V> list = connectionManager.read(getName(), new ResultOperation<List<V>, V>() {
            @Override
            public Future<List<V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.hvals(getName());
            }
        });
        return list.contains(value);
    }

    @Override
    public Map<K, V> getAll(final Set<K> keys) {
        if (keys.size() == 0) {
            return Collections.emptyMap();
        }
        final Object[] keysArray = keys.toArray();
        List<V> list = connectionManager.read(getName(), new ResultOperation<List<V>, V>() {
            @Override
            protected Future<List<V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.hmget(getName(), keysArray);
            }
        });

        Map<K, V> result = new HashMap<K, V>(list.size());
        for (int index = 0; index < keysArray.length; index++) {
            V value = list.get(index);
            if (value == null) {
                continue;
            }
            result.put((K) keysArray[index], value);
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
        connectionManager.write(getName(), new ResultOperation<String, Object>() {
            @Override
            public Future<String> execute(RedisAsyncConnection<Object, Object> async) {
                return async.hmset(getName(), (Map<Object, Object>) map);
            }
        });
    }

    @Override
    public void clear() {
        delete();
    }

    @Override
    public Set<K> keySet() {
        return (Set<K>) connectionManager.read(getName(), new ResultOperation<Set<Object>, V>() {
            @Override
            public Future<Set<Object>> execute(RedisAsyncConnection<Object, V> async) {
                return async.hkeys(getName());
            }
        });
    }

    @Override
    public Collection<V> values() {
        return connectionManager.read(getName(), new ResultOperation<List<V>, V>() {
            @Override
            public Future<List<V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.hvals(getName());
            }
        });
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        Map<Object, Object> map = connectionManager.read(getName(), new ResultOperation<Map<Object, Object>, Object>() {
            @Override
            public Future<Map<Object, Object>> execute(RedisAsyncConnection<Object, Object> async) {
                return async.hgetall(getName());
            }
        });

        Map<K, V> result = new HashMap<K, V>();
        for (java.util.Map.Entry<Object, Object> entry : map.entrySet()) {
            result.put((K)entry.getKey(), (V)entry.getValue());
        }
        return result.entrySet();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return connectionManager.get(putIfAbsentAsync(key, value));
    }

    @Override
    public Future<V> putIfAbsentAsync(K key, V value) {
        return new RedissonScript(connectionManager)
                .evalAsync("if redis.call('hexists', KEYS[1], ARGV[1]) == 0 then redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); return nil else return redis.call('hget', KEYS[1], ARGV[1]) end",
                        RScript.ReturnType.VALUE,
                        Collections.<Object>singletonList(getName()), key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        Long result = connectionManager.get(removeAsync(key, value));
        return result != null && result == 1;
    }

    @Override
    public Future<Long> removeAsync(Object key, Object value) {
        return new RedissonScript(connectionManager)
                .evalAsync("if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then return redis.call('hdel', KEYS[1], ARGV[1]) else return nil end",
                        RScript.ReturnType.INTEGER,
                        Collections.<Object>singletonList(getName()), key, value);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        return "OK".equals(connectionManager.get(replaceAsync(key, oldValue, newValue)));
    }

    @Override
    public Future<V> replaceAsync(K key, V oldValue, V newValue) {
        return new RedissonScript(connectionManager)
                .evalAsync("if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then redis.call('hset', KEYS[1], ARGV[1], ARGV[3]); return 'OK'; else return nil; end",
                        RScript.ReturnType.STATUS,
                        Collections.<Object>singletonList(getName()), key, oldValue, newValue);
    }

    @Override
    public V replace(K key, V value) {
        return connectionManager.get(replaceAsync(key, value));
    }

    @Override
    public Future<V> replaceAsync(K key, V value) {
        return new RedissonScript(connectionManager)
                .evalAsync("if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then local v = redis.call('hget', KEYS[1], ARGV[1]); redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); return v; else return nil; end",
                        RScript.ReturnType.VALUE,
                        Collections.<Object>singletonList(getName()), key, value);
    }

    @Override
    public Future<V> getAsync(final K key) {
        return connectionManager.readAsync(getName(), new ResultOperation<V, V>() {
            @Override
            public Future<V> execute(RedisAsyncConnection<Object, V> async) {
                return async.hget(getName(), key);
            }
        });
    }

    @Override
    public Future<V> putAsync(K key, V value) {
        return new RedissonScript(connectionManager)
                .evalAsync("local v = redis.call('hget', KEYS[1], ARGV[1]); redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); return v",
                        RScript.ReturnType.VALUE,
                        Collections.<Object>singletonList(getName()), key, value);
    }


    @Override
    public Future<V> removeAsync(K key) {
        return new RedissonScript(connectionManager)
                .evalAsync("local v = redis.call('hget', KEYS[1], ARGV[1]); redis.call('hdel', KEYS[1], ARGV[1]); return v",
                        RScript.ReturnType.VALUE,
                        Collections.<Object>singletonList(getName()), key);
    }

    @Override
    public Future<Boolean> fastPutAsync(final K key, final V value) {
        return connectionManager.writeAsync(getName(), new ResultOperation<Boolean, V>() {
            @Override
            public Future<Boolean> execute(RedisAsyncConnection<Object, V> async) {
                return async.hset(getName(), key, value);
            }
        });
    }

    @Override
    public boolean fastPut(K key, V value) {
        return connectionManager.get(fastPutAsync(key, value));
    }

    @Override
    public Future<Long> fastRemoveAsync(final K ... keys) {
        if (keys != null && keys.length > 0) {
            return connectionManager.writeAsync(getName(), new ResultOperation<Long, V>() {
                @Override
                public Future<Long> execute(RedisAsyncConnection<Object, V> async) {
                    return async.hdel(getName(), keys);
                }
            });
        } else {
            return connectionManager.getGroup().next().newSucceededFuture(0L);
        }
    }

    @Override
    public long fastRemove(K ... keys) {
        return connectionManager.get(fastRemoveAsync(keys));
    }

    private MapScanResult<Object, V> scanIterator(final long startPos) {
        return connectionManager.read(getName(), new ResultOperation<MapScanResult<Object, V>, V>() {
            @Override
            public Future<MapScanResult<Object, V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.hscan(getName(), startPos);
            }
        });
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
    public V addAndGet(final K key, final V value) {
        String res = connectionManager.write(getName(), new ResultOperation<String, V>() {

            @Override
            protected Future<String> execute(RedisAsyncConnection<Object, V> async) {
                Number val = (Number) value;
                return async.hincrbyfloat(getName(), key, new BigDecimal(val.toString()).toPlainString());
            }
        });

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
