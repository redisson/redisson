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
import org.redisson.RedissonSemaphore;
import org.redisson.api.RFuture;
import org.redisson.api.RSemaphoreAsync;
import org.redisson.api.RSemaphoreReactive;
import org.redisson.command.CommandReactiveExecutor;
import org.redisson.pubsub.SemaphorePubSub;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonSemaphoreReactive extends RedissonExpirableReactive implements RSemaphoreReactive {

    private final RSemaphoreAsync instance;
    
    public RedissonSemaphoreReactive(CommandReactiveExecutor connectionManager, String name, SemaphorePubSub semaphorePubSub) {
        super(connectionManager, name, new RedissonSemaphore(connectionManager, name, semaphorePubSub));
        instance = (RSemaphoreAsync) super.instance;
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
    public Publisher<Boolean> tryAcquire(final int permits) {
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
    public Publisher<Void> acquire(final int permits) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.acquireAsync(permits);
            }
        });
    }

    @Override
    public Publisher<Void> release() {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.releaseAsync();
            }
        });
    }

    @Override
    public Publisher<Void> release(final int permits) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.releaseAsync(permits);
            }
        });
    }

    @Override
    public Publisher<Boolean> trySetPermits(final int permits) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.trySetPermitsAsync(permits);
            }
        });
    }

    @Override
    public Publisher<Boolean> tryAcquire(final long waitTime, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.tryAcquireAsync(waitTime, unit);
            }
        });
    }

    @Override
    public Publisher<Boolean> tryAcquire(final int permits, final long waitTime, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.tryAcquireAsync(permits, waitTime, unit);
            }
        });
    }

    @Override
    public Publisher<Void> reducePermits(final int permits) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.reducePermitsAsync(permits);
            }
        });
    }
    
}
