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

import java.math.BigDecimal;
import java.util.Collections;

import org.redisson.api.RAtomicDouble;
import org.redisson.api.RFuture;
import org.redisson.client.codec.DoubleCodec;
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
public class RedissonAtomicDouble extends RedissonExpirable implements RAtomicDouble {

    public RedissonAtomicDouble(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    @Override
    public double addAndGet(double delta) {
        return get(addAndGetAsync(delta));
    }

    @Override
    public RFuture<Double> addAndGetAsync(double delta) {
        if (delta == 0) {
            return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.INCRBYFLOAT, getName(), 0);
        }
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.INCRBYFLOAT, getName(), BigDecimal.valueOf(delta).toPlainString());
    }

    @Override
    public boolean compareAndSet(double expect, double update) {
        return get(compareAndSetAsync(expect, update));
    }

    @Override
    public RFuture<Boolean> compareAndSetAsync(double expect, double update) {
        return commandExecutor.evalWriteAsync(getName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "local value = redis.call('get', KEYS[1]);"
                + "if (value == false and tonumber(ARGV[1]) == 0) or (tonumber(value) == tonumber(ARGV[1])) then "
                     + "redis.call('set', KEYS[1], ARGV[2]); "
                     + "return 1 "
                   + "else "
                     + "return 0 end",
                Collections.<Object>singletonList(getName()), BigDecimal.valueOf(expect).toPlainString(), BigDecimal.valueOf(update).toPlainString());
    }

    @Override
    public double decrementAndGet() {
        return get(decrementAndGetAsync());
    }

    @Override
    public RFuture<Double> decrementAndGetAsync() {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.INCRBYFLOAT, getName(), -1);
    }

    @Override
    public double get() {
        return get(getAsync());
    }

    @Override
    public double getAndDelete() {
        return get(getAndDeleteAsync());
    }
    
    @Override
    public RFuture<Double> getAndDeleteAsync() {
        return commandExecutor.evalWriteAsync(getName(), StringCodec.INSTANCE, RedisCommands.EVAL_DOUBLE,
                   "local currValue = redis.call('get', KEYS[1]); "
                 + "redis.call('del', KEYS[1]); "
                 + "return currValue; ",
                Collections.<Object>singletonList(getName()));
    }
    
    @Override
    public RFuture<Double> getAsync() {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.GET_DOUBLE, getName());
    }

    @Override
    public double getAndAdd(double delta) {
        return get(getAndAddAsync(delta));
    }

    @Override
    public RFuture<Double> getAndAddAsync(final double delta) {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, new RedisStrictCommand<Double>("INCRBYFLOAT", new Convertor<Double>() {
            @Override
            public Double convert(Object obj) {
                return Double.valueOf(obj.toString()) - delta;
            }
        }), getName(), BigDecimal.valueOf(delta).toPlainString());
    }


    @Override
    public double getAndSet(double newValue) {
        return get(getAndSetAsync(newValue));
    }

    @Override
    public RFuture<Double> getAndSetAsync(double newValue) {
        return commandExecutor.writeAsync(getName(), DoubleCodec.INSTANCE, RedisCommands.GETSET, getName(), BigDecimal.valueOf(newValue).toPlainString());
    }

    @Override
    public double incrementAndGet() {
        return get(incrementAndGetAsync());
    }

    @Override
    public RFuture<Double> incrementAndGetAsync() {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.INCRBYFLOAT, getName(), 1);
    }

    @Override
    public double getAndIncrement() {
        return getAndAdd(1);
    }

    @Override
    public RFuture<Double> getAndIncrementAsync() {
        return getAndAddAsync(1);
    }

    @Override
    public double getAndDecrement() {
        return getAndAdd(-1);
    }

    @Override
    public RFuture<Double> getAndDecrementAsync() {
        return getAndAddAsync(-1);
    }

    @Override
    public void set(double newValue) {
        get(setAsync(newValue));
    }

    @Override
    public RFuture<Void> setAsync(double newValue) {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.SET, getName(), BigDecimal.valueOf(newValue).toPlainString());
    }

    public String toString() {
        return Double.toString(get());
    }

}
