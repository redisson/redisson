/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.reactive;

import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;
import org.redisson.api.RBucketReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandReactiveExecutor;

public class RedissonBucketReactive<V> extends RedissonExpirableReactive implements RBucketReactive<V> {

    public RedissonBucketReactive(CommandReactiveExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    public RedissonBucketReactive(Codec codec, CommandReactiveExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
    }

    @Override
    public Publisher<V> get() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.GET, getName());
    }

    @Override
    public Publisher<Void> set(V value) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SET, getName(), value);
    }

    @Override
    public Publisher<Void> set(V value, long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SETEX, getName(), timeUnit.toSeconds(timeToLive), value);
    }

}
