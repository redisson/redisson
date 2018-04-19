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
import org.redisson.api.RFuture;
import org.redisson.api.RLockAsync;
import org.redisson.api.RLockReactive;
import org.redisson.command.CommandReactiveExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonLockReactive extends RedissonExpirableReactive implements RLockReactive {

    private final RLockAsync instance;
    
    public RedissonLockReactive(CommandReactiveExecutor connectionManager, String name) {
        this(connectionManager, name, new RedissonLock(connectionManager, name));
    }

    public RedissonLockReactive(CommandReactiveExecutor connectionManager, String name, RLockAsync instance) {
        super(connectionManager, name, instance);
        this.instance = instance;
    }
    
    @Override
    public Publisher<Boolean> forceUnlock() {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.forceUnlockAsync();
            }
        });
    }

    @Override
    public Publisher<Void> unlock() {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.unlockAsync();
            }
        });
    }

    @Override
    public Publisher<Void> unlock(final long threadId) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.unlockAsync(threadId);
            }
        });
    }

    @Override
    public Publisher<Boolean> tryLock() {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.tryLockAsync();
            }
        });
    }

    @Override
    public Publisher<Void> lock() {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.lockAsync();
            }
        });
    }

    @Override
    public Publisher<Void> lock(final long threadId) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.lockAsync(threadId);
            }
        });
    }

    @Override
    public Publisher<Void> lock(final long leaseTime, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.lockAsync(leaseTime, unit);
            }
        });
    }

    @Override
    public Publisher<Void> lock(final long leaseTime, final TimeUnit unit, final long threadId) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.lockAsync(leaseTime, unit, threadId);
            }
        });
    }

    @Override
    public Publisher<Boolean> tryLock(final long threadId) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.tryLockAsync(threadId);
            }
        });
    }

    @Override
    public Publisher<Boolean> tryLock(final long waitTime, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.tryLockAsync(waitTime, unit);
            }
        });
    }

    @Override
    public Publisher<Boolean> tryLock(final long waitTime, final long leaseTime, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.tryLockAsync(waitTime, leaseTime, unit);
            }
        });
    }

    @Override
    public Publisher<Boolean> tryLock(final long waitTime, final long leaseTime, final TimeUnit unit, final long threadId) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.tryLockAsync(waitTime, leaseTime, unit, threadId);
            }
        });
    }
    
}
