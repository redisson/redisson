/**
 * Copyright 2018 Nikita Koksharov
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

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.api.RExpirableAsync;
import org.redisson.api.RExpirableReactive;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
abstract class RedissonExpirableReactive extends RedissonObjectReactive implements RExpirableReactive {

    protected final RExpirableAsync instance;
    
    RedissonExpirableReactive(CommandReactiveExecutor connectionManager, String name, RExpirableAsync instance) {
        super(connectionManager, name, instance);
        this.instance = instance;
    }

    RedissonExpirableReactive(Codec codec, CommandReactiveExecutor connectionManager, String name, RExpirableAsync instance) {
        super(codec, connectionManager, name, instance);
        this.instance = instance;
    }

    @Override
    public Publisher<Boolean> expire(final long timeToLive, final TimeUnit timeUnit) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.expireAsync(timeToLive, timeUnit);
            }
        });
    }

    @Override
    public Publisher<Boolean> expireAt(final long timestamp) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.expireAtAsync(timestamp);
            }
        });
    }

    @Override
    public Publisher<Boolean> expireAt(Date timestamp) {
        return expireAt(timestamp.getTime());
    }

    @Override
    public Publisher<Boolean> clearExpire() {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.clearExpireAsync();
            }
        });
    }

    @Override
    public Publisher<Long> remainTimeToLive() {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.remainTimeToLiveAsync();
            }
        });
    }

}
