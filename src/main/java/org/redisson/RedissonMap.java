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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.redisson.async.AsyncOperation;
import org.redisson.async.OperationListener;
import org.redisson.async.ResultOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RMap;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisConnection;

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
    public V get(Object key) {
        return getAsync((K)key).awaitUninterruptibly().getNow();
    }

    @Override
    public V put(K key, V value) {
        return putAsync(key, value).awaitUninterruptibly().getNow();
    }

    @Override
    public V remove(Object key) {
        return removeAsync((K)key).awaitUninterruptibly().getNow();
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
    public boolean remove(Object key, Object value) {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
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
        } finally {
            connectionManager.releaseWrite(connection);
        }
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
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
        } finally {
            connectionManager.releaseWrite(connection);
        }
    }

    @Override
    public V replace(K key, V value) {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            while (true) {
                connection.watch(getName());
                if (connection.hexists(getName(), key)) {
                    V prev = (V) connection.hget(getName(), key);
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
        } finally {
            connectionManager.releaseWrite(connection);
        }
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

}
