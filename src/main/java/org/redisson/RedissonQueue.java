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

import org.redisson.core.RQueue;

import com.lambdaworks.redis.RedisConnection;

public class RedissonQueue<V> extends RedissonList<V> implements RQueue<V> {

    RedissonQueue(Redisson redisson, RedisConnection<Object, Object> connection, String name) {
        super(redisson, connection, name);
    }

    @Override
    public boolean offer(V e) {
        return add(e);
    }

    public V getFirst() {
        V value = (V) getConnection().lindex(getName(), 0);
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public V removeFirst() {
        V value = (V) getConnection().lpop(getName());
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
        return (V) getConnection().lpop(getName());
    }

    @Override
    public V element() {
        return getFirst();
    }

    @Override
    public V peek() {
        return (V) getConnection().lindex(getName(), 0);
    }

}
