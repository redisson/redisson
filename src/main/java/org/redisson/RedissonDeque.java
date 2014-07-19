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

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.redisson.async.ResultOperation;
import org.redisson.async.VoidOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RDeque;

import com.lambdaworks.redis.RedisAsyncConnection;

/**
 * Distributed and concurrent implementation of {@link java.util.Queue}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonDeque<V> extends RedissonQueue<V> implements RDeque<V> {

    RedissonDeque(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public void addFirst(V e) {
        add(e);
    }

    @Override
    public void addLast(final V e) {
        connectionManager.write(new VoidOperation<V, Long>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, V> async) {
                return async.lpush(getName(), e);
            }
        });
    }

    @Override
    public Iterator<V> descendingIterator() {
        return new Iterator<V>() {

            private int currentIndex = size();
            private boolean removeExecuted;

            @Override
            public boolean hasNext() {
                int size = size();
                return currentIndex > 0 && size > 0;
            }

            @Override
            public V next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No such element at index " + currentIndex);
                }
                currentIndex--;
                removeExecuted = false;
                return RedissonDeque.this.get(currentIndex);
            }

            @Override
            public void remove() {
                if (removeExecuted) {
                    throw new IllegalStateException("Element been already deleted");
                }
                RedissonDeque.this.remove(currentIndex);
                currentIndex++;
                removeExecuted = true;
            }

        };
    }

    @Override
    public V getLast() {
        List<V> list = connectionManager.read(new ResultOperation<List<V>, V>() {
            @Override
            protected Future<List<V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.lrange(getName(), -1, -1);
            }
        });
        if (list.isEmpty()) {
            throw new NoSuchElementException();
        }
        return list.get(0);
    }

    @Override
    public boolean offerFirst(final V e) {
        connectionManager.write(new ResultOperation<Long, Object>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, Object> async) {
                return async.lpush(getName(), e);
            }
        });
        return true;
    }

    @Override
    public boolean offerLast(V e) {
        return offer(e);
    }

    @Override
    public V peekFirst() {
        return peek();
    }

    @Override
    public V peekLast() {
        List<V> list = connectionManager.read(new ResultOperation<List<V>, V>() {
            @Override
            protected Future<List<V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.lrange(getName(), -1, -1);
            }
        });
        if (list.isEmpty()) {
            throw  null;
        }
        return list.get(0);
    }

    @Override
    public V pollFirst() {
        return poll();
    }

    @Override
    public V pollLast() {
        return connectionManager.write(new ResultOperation<V, V>() {
            @Override
            protected Future<V> execute(RedisAsyncConnection<Object, V> async) {
                return async.rpop(getName());
            }
        });
    }

    @Override
    public V pop() {
        return removeFirst();
    }

    @Override
    public void push(V e) {
        addFirst(e);
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        return remove(o, 1);
    }

    @Override
    public V removeLast() {
        V value = connectionManager.write(new ResultOperation<V, V>() {
            @Override
            protected Future<V> execute(RedisAsyncConnection<Object, V> async) {
                return async.rpop(getName());
            }
        });
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        return remove(o, -1);
    }

}
