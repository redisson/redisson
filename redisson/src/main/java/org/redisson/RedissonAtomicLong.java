/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.api.ObjectListener;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RFuture;
import org.redisson.api.listener.IncrByListener;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.command.CommandAsyncExecutor;

import java.util.Collections;

/**
 * Distributed alternative to the {@link java.util.concurrent.atomic.AtomicLong}
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonAtomicLong extends RedissonExpirable implements RAtomicLong {

    public RedissonAtomicLong(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    @Override
    public long addAndGet(long delta) {
        return get(addAndGetAsync(delta));
    }

    @Override
    public RFuture<Long> addAndGetAsync(long delta) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.INCRBY, getRawName(), delta);
    }

    @Override
    public boolean compareAndSet(long expect, long update) {
        return get(compareAndSetAsync(expect, update));
    }

    @Override
    public RFuture<Boolean> compareAndSetAsync(long expect, long update) {
        return commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "local currValue = redis.call('get', KEYS[1]); "
                  + "if currValue == ARGV[1] "
                          + "or (tonumber(ARGV[1]) == 0 and currValue == false) then "
                     + "redis.call('set', KEYS[1], ARGV[2]); "
                     + "return 1 "
                   + "else "
                     + "return 0 "
                   + "end",
                Collections.<Object>singletonList(getRawName()), expect, update);
    }
    
    @Override
    public long getAndDelete() {
        return get(getAndDeleteAsync());
    }
    
    @Override
    public RFuture<Long> getAndDeleteAsync() {
        return commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_LONG_SAFE,
                   "local currValue = redis.call('get', KEYS[1]); "
                 + "redis.call('del', KEYS[1]); "
                 + "return currValue; ",
                Collections.<Object>singletonList(getRawName()));
    }

    @Override
    public long decrementAndGet() {
        return get(decrementAndGetAsync());
    }

    @Override
    public RFuture<Long> decrementAndGetAsync() {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.DECR, getRawName());
    }

    @Override
    public long get() {
        return get(getAsync());
    }

    @Override
    public RFuture<Long> getAsync() {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.GET_LONG, getRawName());
    }

    @Override
    public long getAndAdd(long delta) {
        return get(getAndAddAsync(delta));
    }

    @Override
    public RFuture<Long> getAndAddAsync(final long delta) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, new RedisStrictCommand<Long>("INCRBY", new Convertor<Long>() {
            @Override
            public Long convert(Object obj) {
                return ((Long) obj) - delta;
            }
        }), getRawName(), delta);
    }


    @Override
    public long getAndSet(long newValue) {
        return get(getAndSetAsync(newValue));
    }

    @Override
    public RFuture<Long> getAndSetAsync(long newValue) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.GETSET_LONG, getRawName(), newValue);
    }

    @Override
    public long incrementAndGet() {
        return get(incrementAndGetAsync());
    }

    @Override
    public RFuture<Long> incrementAndGetAsync() {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.INCR, getRawName());
    }

    @Override
    public long getAndIncrement() {
        return getAndAdd(1);
    }

    @Override
    public RFuture<Long> getAndIncrementAsync() {
        return getAndAddAsync(1);
    }

    @Override
    public long getAndDecrement() {
        return getAndAdd(-1);
    }

    @Override
    public RFuture<Long> getAndDecrementAsync() {
        return getAndAddAsync(-1);
    }

    @Override
    public void set(long newValue) {
        get(setAsync(newValue));
    }

    @Override
    public RFuture<Void> setAsync(long newValue) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.SET, getRawName(), newValue);
    }
    
    @Override
    public boolean setIfLess(long less, long value) {
        return get(setIfLessAsync(less, value));
    }
    
    @Override
    public RFuture<Boolean> setIfLessAsync(long less, long value) {
        return commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
        "local currValue = redis.call('get', KEYS[1]); "
                + "currValue = currValue == false and 0 or tonumber(currValue);"
                + "if currValue < tonumber(ARGV[1]) then "
                    + "redis.call('set', KEYS[1], ARGV[2]); "
                    + "return 1;"
                + "end; "
                + "return 0;",
                Collections.<Object>singletonList(getRawName()), less, value);
    }
    
    @Override
    public boolean setIfGreater(long greater, long value) {
        return get(setIfGreaterAsync(greater, value));
    }
    
    @Override
    public RFuture<Boolean> setIfGreaterAsync(long greater, long value) {
        return commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local currValue = redis.call('get', KEYS[1]); "
                        + "currValue = currValue == false and 0 or tonumber(currValue);"
                        + "if currValue > tonumber(ARGV[1]) then "
                            + "redis.call('set', KEYS[1], ARGV[2]); "
                            + "return 1;"
                        + "end; "
                        + "return 0;",
                Collections.<Object>singletonList(getRawName()), greater, value);
    }
    
    public String toString() {
        return Long.toString(get());
    }

    @Override
    public int addListener(ObjectListener listener) {
        if (listener instanceof IncrByListener) {
            return addListener("__keyevent@*:incrby", (IncrByListener) listener, IncrByListener::onChange);
        }
        return super.addListener(listener);
    }

    @Override
    public RFuture<Integer> addListenerAsync(ObjectListener listener) {
        if (listener instanceof IncrByListener) {
            return addListenerAsync("__keyevent@*:incrby", (IncrByListener) listener, IncrByListener::onChange);
        }
        return super.addListenerAsync(listener);
    }

    @Override
    public void removeListener(int listenerId) {
        removeListener(listenerId, "__keyevent@*:incrby");
        super.removeListener(listenerId);
    }

    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        return removeListenerAsync(listenerId, "__keyevent@*:incrby");
    }

}
