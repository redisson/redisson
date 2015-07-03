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

import java.util.*;

import org.redisson.async.AsyncOperation;
import org.redisson.async.OperationListener;
import org.redisson.async.ResultOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RSet;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.output.ListScanResult;

/**
 * Distributed and concurrent implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSet<V> extends RedissonExpirable implements RSet<V> {

    protected RedissonSet(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public int size() {
        return connectionManager.read(getName(), new ResultOperation<Long, V>() {
            @Override
            public Future<Long> execute(RedisAsyncConnection<Object, V> async) {
                return async.scard(getName());
            }
        }).intValue();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(final Object o) {
        return connectionManager.read(getName(), new ResultOperation<Boolean, Object>() {
            @Override
            public Future<Boolean> execute(RedisAsyncConnection<Object, Object> async) {
                return async.sismember(getName(), o);
            }
        });
    }

    private ListScanResult<V> scanIterator(final long startPos) {
        return connectionManager.read(getName(), new ResultOperation<ListScanResult<V>, V>() {
            @Override
            public Future<ListScanResult<V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.sscan(getName(), startPos);
            }
        });
    }

    @Override
    public Iterator<V> iterator() {
        return new Iterator<V>() {

            private Iterator<V> iter;
            private Long iterPos;

            private boolean removeExecuted;
            private V value;

            @Override
            public boolean hasNext() {
                if (iter == null) {
                    ListScanResult<V> res = scanIterator(0);
                    iter = res.getValues().iterator();
                    iterPos = res.getPos();
                } else if (!iter.hasNext() && iterPos != 0) {
                    ListScanResult<V> res = scanIterator(iterPos);
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

                // lazy init iterator
//                hasNext();
                iter.remove();
                RedissonSet.this.remove(value);
                removeExecuted = true;
            }

        };
    }

    @Override
    public Object[] toArray() {
        Set<V> res = connectionManager.read(getName(), new ResultOperation<Set<V>, V>() {
            @Override
            public Future<Set<V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.smembers(getName());
            }
        });
        return res.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        Set<V> res = connectionManager.read(getName(), new ResultOperation<Set<V>, V>() {
            @Override
            public Future<Set<V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.smembers(getName());
            }
        });
        return res.toArray(a);
    }

    @Override
    public boolean add(final V e) {
        return connectionManager.get(addAsync(e));
    }

    @Override
    public Future<Boolean> addAsync(final V e) {
        return connectionManager.writeAsync(getName(), new AsyncOperation<V, Boolean>() {
            @Override
            public void execute(final Promise<Boolean> promise, RedisAsyncConnection<Object, V> async) {
                async.sadd(getName(), e).addListener(new OperationListener<V, Boolean, Long>(promise, async, this) {
                    @Override
                    public void onOperationComplete(Future<Long> future) throws Exception {
                        promise.setSuccess(future.get() > 0);
                    }
                });
            }
        });
    }

    @Override
    public Future<Boolean> removeAsync(final V e) {
        return connectionManager.writeAsync(getName(), new AsyncOperation<V, Boolean>() {
            @Override
            public void execute(final Promise<Boolean> promise, RedisAsyncConnection<Object, V> async) {
                async.srem(getName(), e).addListener(new OperationListener<V, Boolean, Long>(promise, async, this) {
                    @Override
                    public void onOperationComplete(Future<Long> future) throws Exception {
                        promise.setSuccess(future.get() > 0);
                    }
                });
            }
        });
    }

    @Override
    public boolean remove(Object value) {
        return connectionManager.get(removeAsync((V)value));
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object object : c) {
            if (!contains(object)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(final Collection<? extends V> c) {
        if (c.isEmpty()) {
            return false;
        }

        Long res = connectionManager.write(getName(), new ResultOperation<Long, Object>() {
            @Override
            public Future<Long> execute(RedisAsyncConnection<Object, Object> async) {
                return async.sadd(getName(), c.toArray());
            }
        });
        return res > 0;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        List<V> toRemove = new ArrayList<V>();
        for (V object : this) {
            if (!c.contains(object)) {
                toRemove.add(object);
            }
        }
        return removeAll(toRemove);
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        if (c.isEmpty()) {
            return false;
        }
        
        Long res = connectionManager.write(getName(), new ResultOperation<Long, Object>() {
            @Override
            public Future<Long> execute(RedisAsyncConnection<Object, Object> async) {
                return async.srem(getName(), c.toArray());
            }
        });
        return res > 0;
    }

    @Override
    public void clear() {
        delete();
    }

}
