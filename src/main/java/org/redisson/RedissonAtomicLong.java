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
import org.redisson.core.RScript;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

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
        return new RedissonScript(connectionManager).evalR(
                "if redis.call('get', KEYS[1]) == ARGV[1] then redis.call('set', KEYS[1], ARGV[2]); return true else return false end",
                RScript.ReturnType.BOOLEAN,
                Collections.<Object>singletonList(getName()), Collections.EMPTY_LIST, Arrays.asList(expect, update));
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
        return new RedissonScript(connectionManager).evalR(
                "local v = redis.call('get', KEYS[1]) or 0; redis.call('set', KEYS[1], v + ARGV[1]); return tonumber(v)",
                RScript.ReturnType.INTEGER,
                Collections.<Object>singletonList(getName()), Collections.EMPTY_LIST, Collections.singletonList(delta));
    }

    @Override
    public long getAndSet(final long newValue) {
        return new RedissonScript(connectionManager).evalR(
                "local v = redis.call('get', KEYS[1]) or 0; redis.call('set', KEYS[1], ARGV[1]); return tonumber(v)",
                RScript.ReturnType.INTEGER,
                Collections.<Object>singletonList(getName()), Collections.EMPTY_LIST, Collections.singletonList(newValue));
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
        new RedissonScript(connectionManager).evalR(
                "redis.call('set', KEYS[1], ARGV[1])",
                RScript.ReturnType.STATUS,
                Collections.<Object>singletonList(getName()), Collections.EMPTY_LIST, Collections.singletonList(newValue));
    }

    public String toString() {
        return Long.toString(get());
    }

}
