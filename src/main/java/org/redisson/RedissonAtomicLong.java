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

    protected RedissonAtomicLong(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public long addAndGet(final long delta) {
        return connectionManager.write(getName(), new ResultOperation<Long, Object>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, Object> async) {
                return async.incrby(getName(), delta);
            }
        });
    }

    @Override
    public boolean compareAndSet(final long expect, final long update) {
        return connectionManager.write(getName(), new SyncOperation<Object, Boolean>() {
            @Override
            public Boolean execute(RedisConnection<Object, Object> conn) {
                while (true) {
                    conn.watch(getName());

                    Long value = getLongSafe(conn);
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
        return connectionManager.write(getName(), new ResultOperation<Long, Object>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, Object> async) {
                return async.decr(getName());
            }
        });
    }

    @Override
    public long get() {
        return addAndGet(0);
    }

    @Override
    public long getAndAdd(final long delta) {
        return connectionManager.write(getName(), new SyncOperation<Object, Long>() {
            @Override
            public Long execute(RedisConnection<Object, Object> conn) {
                while (true) {
                    conn.watch(getName());

                    Long value = getLongSafe(conn);

                    conn.multi();
                    conn.set(getName(), value + delta);
                    if (conn.exec().size() == 1) {
                        return value;
                    }
                }
            }

        });
    }

    private Long getLongSafe(RedisConnection<Object, Object> conn) {
        Number n = (Number) conn.get(getName());
        if (n != null) {
            return n.longValue();
        }
        return 0L;
    }

    @Override
    public long getAndSet(final long newValue) {
        return connectionManager.write(getName(), new SyncOperation<Object, Long>() {
            @Override
            public Long execute(RedisConnection<Object, Object> conn) {
                while (true) {
                    conn.watch(getName());

                    Long value = getLongSafe(conn);

                    conn.multi();
                    conn.set(getName(), newValue);
                    if (conn.exec().size() == 1) {
                        return value;
                    }
                }
            }
        });
    }

    @Override
    public long incrementAndGet() {
        return connectionManager.write(getName(), new ResultOperation<Long, Object>() {
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
        connectionManager.write(getName(), new ResultOperation<String, Object>() {
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
