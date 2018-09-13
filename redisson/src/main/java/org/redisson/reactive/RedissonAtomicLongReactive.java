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

import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonAtomicLong;
import org.redisson.api.RAtomicLongAsync;
import org.redisson.api.RAtomicLongReactive;
import org.redisson.api.RFuture;
import org.redisson.command.CommandReactiveExecutor;


/**
 * Distributed alternative to the {@link java.util.concurrent.atomic.AtomicLong}
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonAtomicLongReactive extends RedissonExpirableReactive implements RAtomicLongReactive {

    private final RAtomicLongAsync instance;
    
    public RedissonAtomicLongReactive(CommandReactiveExecutor commandExecutor, String name) {
        this(commandExecutor, name, new RedissonAtomicLong(commandExecutor, name));
    }

    public RedissonAtomicLongReactive(CommandReactiveExecutor commandExecutor, String name, RAtomicLongAsync instance) {
        super(commandExecutor, name, instance);
        this.instance = instance;
    }
    
    @Override
    public Publisher<Long> addAndGet(final long delta) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.addAndGetAsync(delta);
            }
        });
    }

    @Override
    public Publisher<Boolean> compareAndSet(final long expect, final long update) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.compareAndSetAsync(expect, update);
            }
        });
    }

    @Override
    public Publisher<Long> decrementAndGet() {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.decrementAndGetAsync();
            }
        });
    }

    @Override
    public Publisher<Long> get() {
        return addAndGet(0);
    }

    @Override
    public Publisher<Long> getAndAdd(final long delta) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.getAndAddAsync(delta);
            }
        });
    }

    @Override
    public Publisher<Long> getAndDelete() {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.getAndDeleteAsync();
            }
        });
    }

    @Override
    public Publisher<Long> getAndSet(final long newValue) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.getAndSetAsync(newValue);
            }
        });
    }

    @Override
    public Publisher<Long> incrementAndGet() {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.incrementAndGetAsync();
            }
        });
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
    public Publisher<Void> set(final long newValue) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.setAsync(newValue);
            }
        });
    }

    public String toString() {
        return instance.toString();
    }

}
