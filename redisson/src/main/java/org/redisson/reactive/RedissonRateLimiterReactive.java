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

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonRateLimiter;
import org.redisson.api.RFuture;
import org.redisson.api.RRateLimiterAsync;
import org.redisson.api.RRateLimiterReactive;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.command.CommandReactiveExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonRateLimiterReactive extends RedissonObjectReactive implements RRateLimiterReactive {

    private final RRateLimiterAsync instance;
    
    public RedissonRateLimiterReactive(CommandReactiveExecutor connectionManager, String name) {
        this(connectionManager, name, new RedissonRateLimiter(connectionManager, name));
    }
    
    private RedissonRateLimiterReactive(CommandReactiveExecutor connectionManager, String name, RRateLimiterAsync instance) {
        super(connectionManager, name, instance);
        this.instance = instance;
    }

    @Override
    public Publisher<Boolean> trySetRate(final RateType mode, final long rate, final long rateInterval,
            final RateIntervalUnit rateIntervalUnit) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.trySetRateAsync(mode, rate, rateInterval, rateIntervalUnit);
            }
        });
    }

    @Override
    public Publisher<Boolean> tryAcquire() {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.tryAcquireAsync();
            }
        });
    }

    @Override
    public Publisher<Boolean> tryAcquire(final long permits) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.tryAcquireAsync(permits);
            }
        });
    }

    @Override
    public Publisher<Void> acquire() {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.acquireAsync();
            }
        });
    }

    @Override
    public Publisher<Void> acquire(final long permits) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.acquireAsync(permits);
            }
        });
    }

    @Override
    public Publisher<Boolean> tryAcquire(final long timeout, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.tryAcquireAsync(timeout, unit);
            }
        });
    }

    @Override
    public Publisher<Boolean> tryAcquire(final long permits, final long timeout, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.tryAcquireAsync(permits, timeout, unit);
            }
        });
    }
    
}
