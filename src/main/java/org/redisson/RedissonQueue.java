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

import java.util.NoSuchElementException;

import org.redisson.async.ResultOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RQueue;

import com.lambdaworks.redis.RedisAsyncConnection;

/**
 * Distributed and concurrent implementation of {@link java.util.Queue}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonQueue<V> extends RedissonList<V> implements RQueue<V> {

    protected RedissonQueue(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public boolean offer(V e) {
        return add(e);
    }

    public V getFirst() {
        V value = connectionManager.read(getName(), new ResultOperation<V, V>() {
            @Override
            protected Future<V> execute(RedisAsyncConnection<Object, V> async) {
                return async.lindex(getName(), 0);
            }
        });
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public V removeFirst() {
        V value = poll();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    @Override
    public V remove() {
        return removeFirst();
    }

    @Override
    public V poll() {
        return connectionManager.write(getName(), new ResultOperation<V, V>() {
            @Override
            protected Future<V> execute(RedisAsyncConnection<Object, V> async) {
                return async.lpop(getName());
            }
        });
    }

    @Override
    public V element() {
        return getFirst();
    }

    @Override
    public V peek() {
        if (isEmpty()) {
            return null;
        }
        return get(0);
    }

    @Override
    public V pollLastAndOfferFirstTo(final String queueName) {
        return connectionManager.write(new ResultOperation<V, V>() {
            @Override
            protected Future<V> execute(RedisAsyncConnection<Object, V> async) {
                return async.rpoplpush(getName(), queueName);
            }
        });
    }

    @Override
    public V pollLastAndOfferFirstTo(RQueue<V> queue) {
        return pollLastAndOfferFirstTo(queue.getName());
    }

}
