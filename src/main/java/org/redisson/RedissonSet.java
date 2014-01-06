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

import java.util.Collection;
import java.util.Iterator;

import org.redisson.core.RSet;

import com.lambdaworks.redis.RedisConnection;

public class RedissonSet<V> implements RSet<V> {

    private final Redisson redisson;
    private final RedisConnection<Object, Object> connection;
    private final String name;

    RedissonSet(Redisson redisson, RedisConnection<Object, Object> connection, String name) {
        this.connection = connection;
        this.name = name;
        this.redisson = redisson;
    }

    @Override
    public int size() {
        return connection.scard(name).intValue();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        return connection.sismember(name, o);
    }

    @Override
    public Iterator<V> iterator() {
        // TODO use SSCAN in case of usage Redis 2.8
        return (Iterator<V>) connection.smembers(name).iterator();
    }

    @Override
    public Object[] toArray() {
        return connection.smembers(name).toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return connection.smembers(name).toArray(a);
    }

    @Override
    public boolean add(V e) {
        return connection.sadd(name, e) > 0;
    }

    @Override
    public boolean remove(Object o) {
        return connection.srem(name, o) > 0;
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
    public boolean addAll(Collection<? extends V> c) {
        return connection.sadd(name, c.toArray()) > 0;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = false;
        for (Iterator<V> iterator = iterator(); iterator.hasNext();) {
            V object = iterator.next();
            if (!c.contains(object)) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return connection.srem(name, c.toArray()) > 0;
    }

    @Override
    public void clear() {
        connection.del(name);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void destroy() {
        connection.close();

        redisson.remove(this);
    }

}
