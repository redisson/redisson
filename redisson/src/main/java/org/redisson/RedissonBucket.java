/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBucket;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonBucket<V> extends RedissonExpirable implements RBucket<V> {

    public RedissonBucket(CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    public RedissonBucket(Codec codec, CommandAsyncExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
    }

    @Override
    public boolean compareAndSet(V expect, V update) {
        return get(compareAndSetAsync(expect, update));
    }

    @Override
    public RFuture<Boolean> compareAndSetAsync(V expect, V update) {
        if (expect == null && update == null) {
            return trySetAsync(null);
        }

        if (expect == null) {
            return trySetAsync(update);
        }

        if (update == null) {
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "if redis.call('get', KEYS[1]) == ARGV[1] then "
                            + "redis.call('del', KEYS[1]); "
                            + "return 1 "
                          + "else "
                            + "return 0 end",
                    Collections.singletonList(getName()), encode(expect));
        }

        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('get', KEYS[1]) == ARGV[1] then "
                     + "redis.call('set', KEYS[1], ARGV[2]); "
                     + "return 1 "
                   + "else "
                     + "return 0 end",
                Collections.singletonList(getName()), encode(expect), encode(update));
    }

    @Override
    public V getAndSet(V newValue) {
        return get(getAndSetAsync(newValue));
    }

    @Override
    public RFuture<V> getAndSetAsync(V newValue) {
        if (newValue == null) {
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_OBJECT,
                    "local v = redis.call('get', KEYS[1]); "
                    + "redis.call('del', KEYS[1]); "
                    + "return v",
                    Collections.singletonList(getName()));
        }

        return commandExecutor.writeAsync(getName(), codec, RedisCommands.GETSET, getName(), encode(newValue));
    }

    @Override
    public V get() {
        return get(getAsync());
    }

    @Override
    public RFuture<V> getAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.GET, getName());
    }
    
    @Override
    public V getAndDelete() {
        return get(getAndDeleteAsync());
    }
    
    @Override
    public RFuture<V> getAndDeleteAsync() {
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_OBJECT,
                   "local currValue = redis.call('get', KEYS[1]); "
                 + "redis.call('del', KEYS[1]); "
                 + "return currValue; ",
                Collections.singletonList(getName()));
    }
    
    @Override
    public long size() {
        return get(sizeAsync());
    }
    
    @Override
    public RFuture<Long> sizeAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.STRLEN, getName());
    }

    @Override
    public void set(V value) {
        get(setAsync(value));
    }

    @Override
    public RFuture<Void> setAsync(V value) {
        if (value == null) {
            return commandExecutor.writeAsync(getName(), RedisCommands.DEL_VOID, getName());
        }

        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SET, getName(), encode(value));
    }

    @Override
    public void set(V value, long timeToLive, TimeUnit timeUnit) {
        get(setAsync(value, timeToLive, timeUnit));
    }

    @Override
    public RFuture<Void> setAsync(V value, long timeToLive, TimeUnit timeUnit) {
        if (value == null) {
            return commandExecutor.writeAsync(getName(), RedisCommands.DEL_VOID, getName());
        }

        return commandExecutor.writeAsync(getName(), codec, RedisCommands.PSETEX, getName(), timeUnit.toMillis(timeToLive), encode(value));
    }

    @Override
    public RFuture<Boolean> trySetAsync(V value) {
        if (value == null) {
            return commandExecutor.readAsync(getName(), codec, RedisCommands.NOT_EXISTS, getName());
        }

        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SETNX, getName(), encode(value));
    }

    @Override
    public RFuture<Boolean> trySetAsync(V value, long timeToLive, TimeUnit timeUnit) {
        if (value == null) {
            throw new IllegalArgumentException("Value can't be null");
        }
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SETPXNX, getName(), encode(value), "PX", timeUnit.toMillis(timeToLive), "NX");
    }

    @Override
    public boolean trySet(V value, long timeToLive, TimeUnit timeUnit) {
        return get(trySetAsync(value, timeToLive, timeUnit));
    }

    @Override
    public boolean trySet(V value) {
        return get(trySetAsync(value));
    }

    @Override
    public RFuture<V> getAndSetAsync(V value, long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_OBJECT,
                "local currValue = redis.call('get', KEYS[1]); "
              + "redis.call('psetex', KEYS[1], ARGV[2], ARGV[1]); "
              + "return currValue; ",
             Collections.singletonList(getName()),
             encode(value), timeUnit.toMillis(timeToLive));
    }

    @Override
    public V getAndSet(V value, long timeToLive, TimeUnit timeUnit) {
        return get(getAndSetAsync(value, timeToLive, timeUnit));
    }

}
