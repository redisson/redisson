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

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.core.RExpirableReactive;


abstract class RedissonExpirableReactive extends RedissonObjectReactive implements RExpirableReactive {

    RedissonExpirableReactive(CommandReactiveExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    RedissonExpirableReactive(Codec codec, CommandReactiveExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
    }

    @Override
    public Publisher<Boolean> expire(long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.writeObservable(getName(), StringCodec.INSTANCE, RedisCommands.EXPIRE, getName(), timeUnit.toSeconds(timeToLive));
    }

    @Override
    public Publisher<Boolean> expireAt(long timestamp) {
        return commandExecutor.writeObservable(getName(), StringCodec.INSTANCE, RedisCommands.EXPIREAT, getName(), timestamp);
    }

    @Override
    public Publisher<Boolean> expireAt(Date timestamp) {
        return expireAt(timestamp.getTime() / 1000);
    }

    @Override
    public Publisher<Boolean> clearExpire() {
        return commandExecutor.writeObservable(getName(), StringCodec.INSTANCE, RedisCommands.PERSIST, getName());
    }

    @Override
    public Publisher<Long> remainTimeToLive() {
        return commandExecutor.readObservable(getName(), StringCodec.INSTANCE, RedisCommands.TTL, getName());
    }

}
