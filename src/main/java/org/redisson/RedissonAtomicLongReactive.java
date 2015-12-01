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

import org.reactivestreams.Publisher;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandReactiveExecutor;
import org.redisson.core.RAtomicLongReactive;

import reactor.rx.Streams;

/**
 * Distributed alternative to the {@link java.util.concurrent.atomic.AtomicLong}
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonAtomicLongReactive extends RedissonExpirableReactive implements RAtomicLongReactive {

    protected RedissonAtomicLongReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    @Override
    public Publisher<Long> addAndGet(long delta) {
        return commandExecutor.writeObservable(getName(), StringCodec.INSTANCE, RedisCommands.INCRBY, getName(), delta);
    }

    @Override
    public Publisher<Boolean> compareAndSet(long expect, long update) {
        return commandExecutor.evalWriteObservable(getName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('get', KEYS[1]) == ARGV[1] then "
                     + "redis.call('set', KEYS[1], ARGV[2]); "
                     + "return true "
                   + "else "
                     + "return false end",
                Collections.<Object>singletonList(getName()), expect, update);
    }

    @Override
    public Publisher<Long> decrementAndGet() {
        return commandExecutor.writeObservable(getName(), StringCodec.INSTANCE, RedisCommands.DECR, getName());
    }

    @Override
    public Publisher<Long> get() {
        return addAndGet(0);
    }

    @Override
    public Publisher<Long> getAndAdd(long delta) {
        return commandExecutor.evalWriteObservable(getName(),
                StringCodec.INSTANCE, RedisCommands.EVAL_LONG,
                "local v = redis.call('get', KEYS[1]) or 0; "
                + "redis.call('set', KEYS[1], v + ARGV[1]); "
                + "return tonumber(v)",
                Collections.<Object>singletonList(getName()), delta);
    }


    @Override
    public Publisher<Long> getAndSet(long newValue) {
        return commandExecutor.evalWriteObservable(getName(),
                StringCodec.INSTANCE, RedisCommands.EVAL_LONG,
                "local v = redis.call('get', KEYS[1]) or 0; redis.call('set', KEYS[1], ARGV[1]); return tonumber(v)",
                Collections.<Object>singletonList(getName()), newValue);
    }

    @Override
    public Publisher<Long> incrementAndGet() {
        return commandExecutor.writeObservable(getName(), StringCodec.INSTANCE, RedisCommands.INCR, getName());
    }

    @Override
    public Publisher<Long> getAndIncrement() {
        return getAndAdd(1);
    }

    @Override
    public Publisher<Long> getAndDecrement() {
        return getAndAdd(-1);
    }

    @Override
    public Publisher<Void> set(long newValue) {
        return commandExecutor.writeObservable(getName(), StringCodec.INSTANCE, RedisCommands.SET, getName(), newValue);
    }

    public String toString() {
        return Long.toString(Streams.create(get()).next().poll());
    }

}
