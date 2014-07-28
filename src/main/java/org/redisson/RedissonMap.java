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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.redisson.async.AsyncOperation;
import org.redisson.async.OperationListener;
import org.redisson.async.ResultOperation;
import org.redisson.async.SyncOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.Predicate;
import org.redisson.core.RMap;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.output.MapScanResult;

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

    RedissonMap(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public int size() {
        return connectionManager.read(new ResultOperation<Long, V>() {
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
        return connectionManager.read(new ResultOperation<Boolean, V>() {
            @Override
            public Future<Boolean> execute(RedisAsyncConnection<Object, V> async) {
                return async.hexists(getName(), key);
            }
        });
    }

    @Override
    public boolean containsValue(final Object value) {
        List<V> list = connectionManager.read(new ResultOperation<List<V>, V>() {
            @Override
            public Future<List<V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.hvals(getName());
            }
        });
        return list.contains(value);
    }

    @Override
    public Map<K, V> getAll(final Set<K> keys) {
        List<V> list = connectionManager.read(new ResultOperation<List<V>, V>() {
            @Override
            protected Future<List<V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.hmget(getName(), keys.toArray());
            }
        });

        Map<K, V> result = new HashMap<K, V>(list.size());
        int index = 0;
        for (K key : keys) {
            V value = list.get(index);
            result.put(key, value);
            index++;
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
        connectionManager.write(new ResultOperation<String, Object>() {
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
        return (Set<K>) connectionManager.read(new ResultOperation<Set<Object>, V>() {
            @Override
            public Future<Set<Object>> execute(RedisAsyncConnection<Object, V> async) {
                return async.hkeys(getName());
            }
        });
    }

    @Override
    public Collection<V> values() {
        return connectionManager.read(new ResultOperation<List<V>, V>() {
            @Override
            public Future<List<V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.hvals(getName());
            }
        });
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        Map<Object, Object> map = connectionManager.read(new ResultOperation<Map<Object, Object>, Object>() {
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
    public V putIfAbsent(final K key, final V value) {
//        while (true) {
//            Boolean res = connection.hsetnx(getName(), key, value);
//            if (!res) {
//                V result = (V) connection.hget(getName(), key);
//                if (result != null) {
//                    return result;
//                }
//            } else {
//                return null;
//            }
//        }

        return connectionManager.write(new AsyncOperation<V, V>() {
            @Override
            public void execute(final Promise<V> promise, final RedisAsyncConnection<Object, V> async) {
                final AsyncOperation<V, V> timeoutCallback = this;
                async.hsetnx(getName(), key, value).addListener(new OperationListener<V, V, Boolean>(promise, async, timeoutCallback) {
                    @Override
                    public void onOperationComplete(Future<Boolean> future) throws Exception {
                        if (future.get()) {
                            promise.setSuccess(null);
                            return;
                        }

                        async.hget(getName(), key).addListener(new OperationListener<V, V, V>(promise, async, timeoutCallback) {
                            @Override
                            public void onOperationComplete(Future<V> future) throws Exception {
                                V prev = future.get();
                                if (prev != null) {
                                    promise.setSuccess(prev);
                                } else {
                                    timeoutCallback.execute(promise, async);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    private boolean isEquals(RedisConnection<Object, Object> connection, Object key, Object value) {
        Object val = connection.hget(getName(), key);
        return (value != null && value.equals(val)) || (value == null && val == null);
    }

    @Override
    public boolean remove(final Object key, final Object value) {
        return connectionManager.write(new SyncOperation<Object, Boolean>() {
            @Override
            public Boolean execute(RedisConnection<Object, Object> connection) {
                while (true) {
                    connection.watch(getName());
                    if (connection.hexists(getName(), key)
                            && isEquals(connection, key, value)) {
                        connection.multi();
                        connection.hdel(getName(), key);
                        if (connection.exec().size() == 1) {
                            return true;
                        }
                    } else {
                        connection.unwatch();
                        return false;
                    }
                }
            }
        });
    }

    @Override
    public boolean replace(final K key, final V oldValue, final V newValue) {
        return connectionManager.write(new SyncOperation<Object, Boolean>() {
            @Override
            public Boolean execute(RedisConnection<Object, Object> connection) {
                while (true) {
                    connection.watch(getName());
                    if (connection.hexists(getName(), key)
                            && isEquals(connection, key, oldValue)) {
                        connection.multi();
                        connection.hset(getName(), key, newValue);
                        if (connection.exec().size() == 1) {
                            return true;
                        }
                    } else {
                        connection.unwatch();
                        return false;
                    }
                }
            }
        });
    }

    @Override
    public V replace(final K key, final V value) {
        return connectionManager.write(new SyncOperation<V, V>() {
            @Override
            public V execute(RedisConnection<Object, V> connection) {
                while (true) {
                    connection.watch(getName());
                    if (connection.hexists(getName(), key)) {
                        V prev = connection.hget(getName(), key);
                        connection.multi();
                        connection.hset(getName(), key, value);
                        if (connection.exec().size() == 1) {
                            return prev;
                        }
                    } else {
                        connection.unwatch();
                    }
                    return null;
                }
            }
        });
    }

    @Override
    public Future<V> getAsync(final K key) {
        return connectionManager.readAsync(new ResultOperation<V, V>() {
            @Override
            public Future<V> execute(RedisAsyncConnection<Object, V> async) {
                return async.hget(getName(), key);
            }
        });
    }

    @Override
    public Future<V> putAsync(final K key, final V value) {
//        while (true) {
//            connection.watch(getName());
//            V prev = (V) connection.hget(getName(), key);
//            connection.multi();
//            connection.hset(getName(), key, value);
//            if (connection.exec().size() == 1) {
//                return prev;
//            }
//        }

        return connectionManager.writeAsync(new AsyncOperation<V, V>() {
            @Override
            public void execute(final Promise<V> promise, RedisAsyncConnection<Object, V> async) {
                putAsync(key, value, promise, async, this);
            }
        });
    }

    private void putAsync(final K key, final V value, final Promise<V> promise,
            final RedisAsyncConnection<Object, V> async, final AsyncOperation<V, V> timeoutCallback) {
        async.watch(getName()).addListener(new OperationListener<V, V, String>(promise, async, timeoutCallback) {
            @Override
            public void onOperationComplete(Future<String> future) throws Exception {

                async.hget(getName(), key).addListener(new OperationListener<V, V, V>(promise, async, timeoutCallback) {
                    @Override
                    public void onOperationComplete(Future<V> future) throws Exception {

                        final V prev = future.get();
                        async.multi().addListener(new OperationListener<V, V, String>(promise, async, timeoutCallback) {
                            @Override
                            public void onOperationComplete(Future<String> future) throws Exception {

                                async.hset(getName(), key, value).addListener(new OperationListener<V, V, Boolean>(promise, async, timeoutCallback) {
                                    @Override
                                    public void onOperationComplete(Future<Boolean> future) throws Exception {

                                        async.exec().addListener(new OperationListener<V, V, List<Object>>(promise, async, timeoutCallback) {
                                            @Override
                                            public void onOperationComplete(Future<List<Object>> future) throws Exception {

                                                if (future.get().size() == 1) {
                                                    promise.setSuccess(prev);
                                                } else {
                                                    timeoutCallback.execute(promise, async);
                                                }
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }

        });
    }

    @Override
    public Future<V> removeAsync(final K key) {
//        while (true) {
//            connection.watch(getName());
//            V prev = (V) connection.hget(getName(), key);
//            connection.multi();
//            connection.hdel(getName(), key);
//            if (connection.exec().size() == 1) {
//                return prev;
//            }
//        }

        return connectionManager.writeAsync(new AsyncOperation<V, V>() {
            @Override
            public void execute(final Promise<V> promise, RedisAsyncConnection<Object, V> async) {
                removeAsync(key, promise, async, this);
            }
        });
    }

    private void removeAsync(final K key, final Promise<V> promise,
            final RedisAsyncConnection<Object, V> async, final AsyncOperation<V, V> timeoutCallback) {
        async.watch(getName()).addListener(new OperationListener<V, V, String>(promise, async, timeoutCallback) {
            @Override
            public void onOperationComplete(Future<String> future) throws Exception {

                async.hget(getName(), key).addListener(new OperationListener<V, V, V>(promise, async, timeoutCallback) {
                    @Override
                    public void onOperationComplete(Future<V> future) throws Exception {
                        final V prev = future.get();

                        async.multi().addListener(new OperationListener<V, V, String>(promise, async, timeoutCallback) {
                            @Override
                            public void onOperationComplete(Future<String> future) throws Exception {

                                async.hdel(getName(), key).addListener(new OperationListener<V, V, Long>(promise, async, timeoutCallback) {
                                    @Override
                                    public void onOperationComplete(Future<Long> future) throws Exception {

                                        async.exec().addListener(new OperationListener<V, V, List<Object>>(promise, async, timeoutCallback) {
                                            @Override
                                            public void onOperationComplete(Future<List<Object>> future) throws Exception {

                                                if (future.get().size() == 1) {
                                                    promise.setSuccess(prev);
                                                } else {
                                                    timeoutCallback.execute(promise, async);
                                                }
                                            }
                                        });

                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    public Future<Boolean> fastPutAsync(final K key, final V value) {
        return connectionManager.writeAsync(new ResultOperation<Boolean, V>() {
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
        return connectionManager.writeAsync(new ResultOperation<Long, V>() {
            @Override
            public Future<Long> execute(RedisAsyncConnection<Object, V> async) {
                return async.hdel(getName(), keys);
            }
        });
    }

    @Override
    public long fastRemove(K ... keys) {
        return connectionManager.get(fastRemoveAsync(keys));
    }

    private MapScanResult<Object, V> scanIterator(final long startPos) {
        return connectionManager.read(new ResultOperation<MapScanResult<Object, V>, V>() {
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
        String res = connectionManager.write(new ResultOperation<String, V>() {

            @Override
            protected Future<String> execute(RedisAsyncConnection<Object, V> async) {
                Number val = (Number) value;
                return async.hincrbyfloat(getName(), key, new BigDecimal(val.toString()).toPlainString());
            }
        });

        //long
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
