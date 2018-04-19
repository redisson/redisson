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
import org.redisson.RedissonLock;
import org.redisson.RedissonPermitExpirableSemaphore;
import org.redisson.api.RFuture;
import org.redisson.api.RLockAsync;
import org.redisson.api.RPermitExpirableSemaphoreAsync;
import org.redisson.api.RPermitExpirableSemaphoreReactive;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandReactiveExecutor;
import org.redisson.pubsub.SemaphorePubSub;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonPermitExpirableSemaphoreReactive extends RedissonExpirableReactive implements RPermitExpirableSemaphoreReactive {

    private final RPermitExpirableSemaphoreAsync instance;
    
    public RedissonPermitExpirableSemaphoreReactive(CommandReactiveExecutor connectionManager, String name, SemaphorePubSub semaphorePubSub) {
        super(connectionManager, name, new RedissonPermitExpirableSemaphore(connectionManager, name, semaphorePubSub));
        instance = (RPermitExpirableSemaphoreAsync) super.instance;
    }

    protected RLockAsync createLock(CommandAsyncExecutor connectionManager, String name) {
        return new RedissonLock(commandExecutor, name);
    }

    @Override
    public Publisher<String> acquire() {
        return reactive(new Supplier<RFuture<String>>() {
            @Override
            public RFuture<String> get() {
                return instance.acquireAsync();
            }
        });
    }

    @Override
    public Publisher<String> acquire(final long leaseTime, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<String>>() {
            @Override
            public RFuture<String> get() {
                return instance.acquireAsync(leaseTime, unit);
            }
        });
    }

    @Override
    public Publisher<String> tryAcquire() {
        return reactive(new Supplier<RFuture<String>>() {
            @Override
            public RFuture<String> get() {
                return instance.tryAcquireAsync();
            }
        });
    }

    @Override
    public Publisher<String> tryAcquire(final long waitTime, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<String>>() {
            @Override
            public RFuture<String> get() {
                return instance.tryAcquireAsync(waitTime, unit);
            }
        });
    }

    @Override
    public Publisher<String> tryAcquire(final long waitTime, final long leaseTime, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<String>>() {
            @Override
            public RFuture<String> get() {
                return instance.tryAcquireAsync(waitTime, leaseTime, unit);
            }
        });
    }

    @Override
    public Publisher<Boolean> tryRelease(final String permitId) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.tryReleaseAsync(permitId);
            }
        });
    }

    @Override
    public Publisher<Void> release(final String permitId) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.releaseAsync(permitId);
            }
        });
    }

    @Override
    public Publisher<Integer> availablePermits() {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.availablePermitsAsync();
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
    public Publisher<Void> addPermits(final int permits) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.addPermitsAsync(permits);
            }
        });
    }
    
}
