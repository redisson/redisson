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

import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.core.RBucketReactive;

import rx.Single;

public class RedissonBucketReactive<V> extends RedissonExpirableReactive implements RBucketReactive<V> {

    protected RedissonBucketReactive(CommandReactiveExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    protected RedissonBucketReactive(Codec codec, CommandReactiveExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
    }

    @Override
    public Single<V> get() {
        return commandExecutor.readObservable(getName(), codec, RedisCommands.GET, getName());
    }

    @Override
    public Single<Void> set(V value) {
        return commandExecutor.writeObservable(getName(), codec, RedisCommands.SET, getName(), value);
    }

    @Override
    public Single<Void> set(V value, long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.writeObservable(getName(), codec, RedisCommands.SETEX, getName(), timeUnit.toSeconds(timeToLive), value);
    }

    @Override
    public Single<Boolean> exists() {
        return commandExecutor.readObservable(getName(), codec, RedisCommands.EXISTS, getName());
    }

}
