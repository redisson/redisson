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

import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.connection.ConnectionManager;
import org.redisson.core.RAtomicLong;

import com.lambdaworks.redis.RedisConnection;

/**
 * Distributed alternative to the {@link java.util.concurrent.atomic.AtomicLong}
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonAtomicLong extends RedissonExpirable implements RAtomicLong {

    private final AtomicBoolean initOnce = new AtomicBoolean();

    RedissonAtomicLong(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    public void init() {
        if (!initOnce.compareAndSet(false, true)) {
            return;
        }
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        try {
            conn.setnx(getName(), 0);
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public long addAndGet(long delta) {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        try {
            return conn.incrby(getName(), delta);
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public boolean compareAndSet(long expect, long update) {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        try {
            while (true) {
                conn.watch(getName());
                Long value = ((Number) conn.get(getName())).longValue();
                if (value != expect) {
                    conn.unwatch();
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
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        try {
            return conn.decr(getName());
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public long get() {
        RedisConnection<String, Object> conn = connectionManager.connectionReadOp();
        try {
            return ((Number) conn.get(getName())).longValue();
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
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        try {
            return ((Number) conn.getset(getName(), newValue)).longValue();
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public long incrementAndGet() {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
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
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
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
