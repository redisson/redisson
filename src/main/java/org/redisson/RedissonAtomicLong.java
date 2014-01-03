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

import org.redisson.core.RAtomicLong;

import com.lambdaworks.redis.RedisConnection;

public class RedissonAtomicLong implements RAtomicLong {

    private final RedisConnection<Object, Object> connection;
    private final String name;
    private final Redisson redisson;

    RedissonAtomicLong(Redisson redisson, RedisConnection<Object, Object> connection, String name) {
        this.redisson = redisson;
        this.connection = connection;
        this.name = name;
    }


    @Override
    public long addAndGet(long delta) {
        return connection.incrby(name, delta);
    }

    @Override
    public boolean compareAndSet(long expect, long update) {
        RedisConnection<Object, Object> conn = redisson.connect();
        try {
            while (true) {
                conn.watch(name);
                Long value = (Long) conn.get(name);
                if (value != expect) {
                    conn.discard();
                    return false;
                }
                conn.multi();
                conn.set(name, update);
                if (conn.exec().size() == 1) {
                    return true;
                }
            }
        } finally {
            conn.close();
        }
    }

    @Override
    public long decrementAndGet() {
        return connection.decr(name);
    }

    @Override
    public long get() {
        return (Long) connection.get(name);
    }

    @Override
    public long getAndAdd(long delta) {
        while (true) {
            long current = get();
            long next = current + delta;
            if (compareAndSet(current, next))
                return current;
        }
    }

    @Override
    public long getAndSet(long newValue) {
        return (Long) connection.getset(name, newValue);
    }

    @Override
    public long incrementAndGet() {
        return connection.incr(name);
    }

    @Override
    public long getAndIncrement() {
        return getAndAdd(1);
    }

    public long getAndDecrement() {
        return getAndAdd(-1);
    }

    @Override
    public void set(long newValue) {
        connection.set(name, newValue);
    }

    public String toString() {
        return Long.toString(get());
    }

}
