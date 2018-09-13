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

import org.reactivestreams.Publisher;
import org.redisson.RedissonAtomicDouble;
import org.redisson.api.RAtomicDoubleAsync;
import org.redisson.api.RAtomicDoubleReactive;
import org.redisson.api.RFuture;
import org.redisson.command.CommandReactiveExecutor;

import java.util.function.Supplier;

/**
 * Distributed alternative to the {@link java.util.concurrent.atomic.AtomicLong}
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonAtomicDoubleReactive extends RedissonExpirableReactive implements RAtomicDoubleReactive {

    private final RAtomicDoubleAsync instance;
    
    public RedissonAtomicDoubleReactive(CommandReactiveExecutor commandExecutor, String name) {
        this(commandExecutor, name, new RedissonAtomicDouble(commandExecutor, name));
    }
    
    public RedissonAtomicDoubleReactive(CommandReactiveExecutor commandExecutor, String name, RAtomicDoubleAsync instance) {
        super(commandExecutor, name, instance);
        this.instance = instance;
    }


    @Override
    public Publisher<Double> addAndGet(final double delta) {
        return reactive(new Supplier<RFuture<Double>>() {
            @Override
            public RFuture<Double> get() {
                return instance.addAndGetAsync(delta);
            }
        });
    }

    @Override
    public Publisher<Boolean> compareAndSet(final double expect, final double update) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.compareAndSetAsync(expect, update);
            }
        });
    }

    @Override
    public Publisher<Double> decrementAndGet() {
        return reactive(new Supplier<RFuture<Double>>() {
            @Override
            public RFuture<Double> get() {
                return instance.decrementAndGetAsync();
            }
        });
    }

    @Override
    public Publisher<Double> get() {
        return addAndGet(0);
    }

    @Override
    public Publisher<Double> getAndAdd(final double delta) {
        return reactive(new Supplier<RFuture<Double>>() {
            @Override
            public RFuture<Double> get() {
                return instance.getAndAddAsync(delta);
            }
        });
    }


    @Override
    public Publisher<Double> getAndSet(final double newValue) {
        return reactive(new Supplier<RFuture<Double>>() {
            @Override
            public RFuture<Double> get() {
                return instance.getAndSetAsync(newValue);
            }
        });
    }

    @Override
    public Publisher<Double> incrementAndGet() {
        return reactive(new Supplier<RFuture<Double>>() {
            @Override
            public RFuture<Double> get() {
                return instance.incrementAndGetAsync();
            }
        });
    }

    @Override
    public Publisher<Double> getAndIncrement() {
        return getAndAdd(1);
    }

    @Override
    public Publisher<Double> getAndDecrement() {
        return getAndAdd(-1);
    }

    @Override
    public Publisher<Void> set(final double newValue) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.setAsync(newValue);
            }
        });
    }
    
    @Override
    public Publisher<Double> getAndDelete() {
        return reactive(new Supplier<RFuture<Double>>() {
            @Override
            public RFuture<Double> get() {
                return instance.getAndDeleteAsync();
            }
        });
    }

    public String toString() {
        return instance.toString();
    }

}
