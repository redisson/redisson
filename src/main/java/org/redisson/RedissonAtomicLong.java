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

import org.redisson.connection.ConnectionManager;
import org.redisson.core.RAtomicLong;

import com.lambdaworks.redis.RedisConnection;

/**
 * Distributed alternative to the {@link java.util.concurrent.atomic.AtomicLong}
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonAtomicLong extends RedissonObject implements RAtomicLong {

    private final ConnectionManager connectionManager;

    RedissonAtomicLong(ConnectionManager connectionManager, String name) {
        super(name);
        this.connectionManager = connectionManager;
    }


    @Override
    public long addAndGet(long delta) {
        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            return conn.incrby(getName(), delta);
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public boolean compareAndSet(long expect, long update) {
        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            while (true) {
                conn.watch(getName());
                Long value = (Long) conn.get(getName());
                if (value != expect) {
                    conn.discard();
                    return false;
                }
                conn.multi();
                conn.set(getName(), update);
                if (conn.exec().size() == 1) {
                    return true;
                }
            }
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public long decrementAndGet() {
        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            return conn.decr(getName());
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public long get() {
        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            return (Long) conn.get(getName());
        } finally {
            connectionManager.release(conn);
        }
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
        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            return (Long) conn.getset(getName(), newValue);
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public long incrementAndGet() {
        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            return conn.incr(getName());
        } finally {
            connectionManager.release(conn);
        }
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
        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            conn.set(getName(), newValue);
        } finally {
            connectionManager.release(conn);
        }
    }

    public String toString() {
        return Long.toString(get());
    }

}
