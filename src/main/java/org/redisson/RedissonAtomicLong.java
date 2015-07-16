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

import java.util.Collections;

import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.StringCodec;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RAtomicLong;

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
    public long addAndGet(long delta) {
        return connectionManager.write(getName(), StringCodec.INSTANCE, RedisCommands.INCRBY, getName(), delta);
    }

    @Override
    public boolean compareAndSet(long expect, long update) {
        return connectionManager.evalWrite(getName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('get', KEYS[1]) == ARGV[1] then "
                     + "redis.call('set', KEYS[1], ARGV[2]); "
                     + "return true "
                   + "else "
                     + "return false end",
                Collections.<Object>singletonList(getName()), expect, update);
    }

    @Override
    public long decrementAndGet() {
        return connectionManager.write(getName(), StringCodec.INSTANCE, RedisCommands.DECR, getName());
    }

    @Override
    public long get() {
        return addAndGet(0);
    }

    @Override
    public long getAndAdd(long delta) {
        return connectionManager.evalWrite(getName(),
                StringCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "local v = redis.call('get', KEYS[1]) or 0; "
                + "redis.call('set', KEYS[1], v + ARGV[1]); "
                + "return tonumber(v)",
                Collections.<Object>singletonList(getName()), delta);
    }

    @Override
    public long getAndSet(long newValue) {
        return connectionManager.evalWrite(getName(),
                StringCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "local v = redis.call('get', KEYS[1]) or 0; redis.call('set', KEYS[1], ARGV[1]); return tonumber(v)",
                Collections.<Object>singletonList(getName()), newValue);
    }

    @Override
    public long incrementAndGet() {
        return connectionManager.write(getName(), StringCodec.INSTANCE, RedisCommands.INCR, getName());
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
        connectionManager.write(getName(), StringCodec.INSTANCE, RedisCommands.SET, getName(), newValue);
    }

    public String toString() {
        return Long.toString(get());
    }

}
