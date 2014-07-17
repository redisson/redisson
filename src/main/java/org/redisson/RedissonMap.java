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
        RedisConnection<String, Object> connection = connectionManager.connectionReadOp();
        try {
            return connection.hlen(getName()).intValue();
        } finally {
            connectionManager.releaseRead(connection);
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        RedisConnection<Object, Object> connection = connectionManager.connectionReadOp();
        try {
            return connection.hexists(getName(), key);
        } finally {
            connectionManager.releaseRead(connection);
        }
    }

    @Override
    public boolean containsValue(Object value) {
        RedisConnection<Object, Object> connection = connectionManager.connectionReadOp();
        try {
            return connection.hvals(getName()).contains(value);
        } finally {
            connectionManager.releaseRead(connection);
        }
    }

    @Override
    public V get(Object key) {
        RedisConnection<Object, Object> connection = connectionManager.connectionReadOp();
        try {
            return (V) connection.hget(getName(), key);
        } finally {
            connectionManager.releaseRead(connection);
        }
    }

    @Override
    public V put(K key, V value) {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            while (true) {
                connection.watch(getName());
                V prev = (V) connection.hget(getName(), key);
                connection.multi();
                connection.hset(getName(), key, value);
                if (connection.exec().size() == 1) {
                    return prev;
                }
            }
        } finally {
            connectionManager.releaseWrite(connection);
        }
    }

    @Override
    public V remove(Object key) {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            while (true) {
                connection.watch(getName());
                V prev = (V) connection.hget(getName(), key);
                connection.multi();
                connection.hdel(getName(), key);
                if (connection.exec().size() == 1) {
                    return prev;
                }
            }
        } finally {
            connectionManager.releaseWrite(connection);
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            connection.hmset(getName(), (Map<Object, Object>) map);
        } finally {
            connectionManager.releaseWrite(connection);
        }
    }

    @Override
    public void clear() {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            connection.del(getName());
        } finally {
            connectionManager.releaseWrite(connection);
        }
    }

    @Override
    public Set<K> keySet() {
        RedisConnection<Object, Object> connection = connectionManager.connectionReadOp();
        try {
            return (Set<K>) connection.hkeys(getName());
        } finally {
            connectionManager.releaseRead(connection);
        }
    }

    @Override
    public Collection<V> values() {
        RedisConnection<Object, Object> connection = connectionManager.connectionReadOp();
        try {
            return (Collection<V>) connection.hvals(getName());
        } finally {
            connectionManager.releaseRead(connection);
        }
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        RedisConnection<Object, Object> connection = connectionManager.connectionReadOp();
        try {
            Map<Object, Object> map = connection.hgetall(getName());
            Map<K, V> result = new HashMap<K, V>();
            for (java.util.Map.Entry<Object, Object> entry : map.entrySet()) {
                result.put((K)entry.getKey(), (V)entry.getValue());
            }
            return result.entrySet();
        } finally {
            connectionManager.releaseRead(connection);
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            while (true) {
                Boolean res = connection.hsetnx(getName(), key, value);
                if (!res) {
                    V result = (V) connection.hget(getName(), key);
                    if (result != null) {
                        return result;
                    }
                } else {
                    return null;
                }
            }
        } finally {
            connectionManager.releaseWrite(connection);
        }

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
        return connectionManager.readAsync(new AsyncOperation<V, V>() {
            @Override
            public void execute(final Promise<V> promise, RedisAsyncConnection<Object, V> async) {
                async.hget(getName(), key).addListener(new OperationListener<V, V, V>(promise, async, this) {
                    @Override
                    public void onOperationComplete(Future<V> future) throws Exception {
                        promise.setSuccess(future.get());
                    }
                });
            }
        });
    }

    @Override
    public Future<V> putAsync(final K key, final V value) {
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
