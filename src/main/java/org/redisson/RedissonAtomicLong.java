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

import org.redisson.async.ResultOperation;
import org.redisson.async.SyncOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RAtomicLong;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisConnection;

/**
 * Distributed alternative to the {@link java.util.concurrent.atomic.AtomicLong}
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonAtomicLong extends RedissonExpirable implements RAtomicLong {

    RedissonAtomicLong(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);

        init();
    }

    private void init() {
        connectionManager.writeAsync(new ResultOperation<Boolean, Object>() {
            @Override
            protected Future<Boolean> execute(RedisAsyncConnection<Object, Object> async) {
                return async.setnx(getName(), 0);
            }
        });
    }

    @Override
    public long addAndGet(final long delta) {
        return connectionManager.write(new ResultOperation<Long, Object>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, Object> async) {
                return async.incrby(getName(), delta);
            }
        });
    }

    @Override
    public boolean compareAndSet(final long expect, final long update) {
        return connectionManager.write(new SyncOperation<Object, Boolean>() {
            @Override
            public Boolean execute(RedisConnection<Object, Object> conn) {
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
            }
        });
    }

    @Override
    public long decrementAndGet() {
        return connectionManager.write(new ResultOperation<Long, Object>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, Object> async) {
                return async.decr(getName());
            }
        });
    }

    @Override
    public long get() {
        Number res = connectionManager.read(new ResultOperation<Number, Number>() {
            @Override
            protected Future<Number> execute(RedisAsyncConnection<Object, Number> async) {
                return async.get(getName());
            }
        });
        return res.longValue();
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
    public long getAndSet(final long newValue) {
        Number res = connectionManager.write(new ResultOperation<Number, Number>() {
            @Override
            protected Future<Number> execute(RedisAsyncConnection<Object, Number> async) {
                return async.getset(getName(), newValue);
            }
        });
        return res.longValue();
    }

    @Override
    public long incrementAndGet() {
        return connectionManager.write(new ResultOperation<Long, Object>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, Object> async) {
                return async.incr(getName());
            }
        });
    }

    @Override
    public long getAndIncrement() {
        return getAndAdd(1);
    }

    public long getAndDecrement() {
        return getAndAdd(-1);
    }

    @Override
    public void set(final long newValue) {
        connectionManager.write(new ResultOperation<String, Object>() {
            @Override
            protected Future<String> execute(RedisAsyncConnection<Object, Object> async) {
                return async.set(getName(), newValue);
            }
        });
    }

    public String toString() {
        return Long.toString(get());
    }

}
