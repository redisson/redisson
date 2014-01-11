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

import java.util.NoSuchElementException;

import org.redisson.connection.ConnectionManager;
import org.redisson.core.RQueue;

import com.lambdaworks.redis.RedisConnection;

/**
 * Distributed and concurrent implementation of {@link java.util.List}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonQueue<V> extends RedissonList<V> implements RQueue<V> {

    RedissonQueue(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public boolean offer(V e) {
        return add(e);
    }

    public V getFirst() {
        RedisConnection<String, Object> connection = getConnectionManager().connection();
        try {
            V value = (V) connection.lindex(getName(), 0);
            if (value == null) {
                throw new NoSuchElementException();
            }
            return value;
        } finally {
            getConnectionManager().release(connection);
        }
    }

    public V removeFirst() {
        RedisConnection<String, Object> connection = getConnectionManager().connection();
        try {
            V value = (V) connection.lpop(getName());
            if (value == null) {
                throw new NoSuchElementException();
            }
            return value;
        } finally {
            getConnectionManager().release(connection);
        }
    }

    @Override
    public V remove() {
        return removeFirst();
    }

    @Override
    public V poll() {
        RedisConnection<String, Object> connection = getConnectionManager().connection();
        try {
            return (V) connection.lpop(getName());
        } finally {
            getConnectionManager().release(connection);
        }
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

}
