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

import org.redisson.api.RAtomicLong;
import org.redisson.api.RFuture;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.command.CommandAsyncExecutor;

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
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.INCRBY, getName(), delta);
    }

    @Override
    public boolean compareAndSet(long expect, long update) {
        return get(compareAndSetAsync(expect, update));
    }

    @Override
    public RFuture<Boolean> compareAndSetAsync(long expect, long update) {
        return commandExecutor.evalWriteAsync(getName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "local currValue = redis.call('get', KEYS[1]); "
                  + "if currValue == ARGV[1] "
                          + "or (tonumber(ARGV[1]) == 0 and currValue == false) then "
                     + "redis.call('set', KEYS[1], ARGV[2]); "
                     + "return 1 "
                   + "else "
                     + "return 0 "
                   + "end",
                Collections.<Object>singletonList(getName()), expect, update);
    }
    
    @Override
    public long getAndDelete() {
        return get(getAndDeleteAsync());
    }
    
    @Override
    public RFuture<Long> getAndDeleteAsync() {
        return commandExecutor.evalWriteAsync(getName(), StringCodec.INSTANCE, RedisCommands.EVAL_LONG_SAFE,
                   "local currValue = redis.call('get', KEYS[1]); "
                 + "redis.call('del', KEYS[1]); "
                 + "return currValue; ",
                Collections.<Object>singletonList(getName()));
    }

    @Override
    public long decrementAndGet() {
        return get(decrementAndGetAsync());
    }

    @Override
    public RFuture<Long> decrementAndGetAsync() {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.DECR, getName());
    }

    @Override
    public long get() {
        return get(getAsync());
    }

    @Override
    public RFuture<Long> getAsync() {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.GET_LONG, getName());
    }

    @Override
    public long getAndAdd(long delta) {
        return get(getAndAddAsync(delta));
    }

    @Override
    public RFuture<Long> getAndAddAsync(final long delta) {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, new RedisStrictCommand<Long>("INCRBY", new Convertor<Long>() {
            @Override
            public Long convert(Object obj) {
                return ((Long) obj) - delta;
            }
        }), getName(), delta);
    }


    @Override
    public long getAndSet(long newValue) {
        return get(getAndSetAsync(newValue));
    }

    @Override
    public RFuture<Long> getAndSetAsync(long newValue) {
        return commandExecutor.writeAsync(getName(), LongCodec.INSTANCE, RedisCommands.GETSET, getName(), newValue);
    }

    @Override
    public long incrementAndGet() {
        return get(incrementAndGetAsync());
    }

    @Override
    public RFuture<Long> incrementAndGetAsync() {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.INCR, getName());
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
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.SET, getName(), newValue);
    }

    public String toString() {
        return Long.toString(get());
    }

}
