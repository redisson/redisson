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

import java.util.Collection;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonHyperLogLog;
import org.redisson.api.RFuture;
import org.redisson.api.RHyperLogLogAsync;
import org.redisson.api.RHyperLogLogReactive;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonHyperLogLogReactive<V> extends RedissonExpirableReactive implements RHyperLogLogReactive<V> {

    private final RHyperLogLogAsync<V> instance;
    
    public RedissonHyperLogLogReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name, new RedissonHyperLogLog<V>(commandExecutor, name));
        this.instance = (RHyperLogLogAsync<V>) super.instance;
    }
    
    public RedissonHyperLogLogReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name, new RedissonHyperLogLog<V>(commandExecutor, name));
        this.instance = (RHyperLogLogAsync<V>) super.instance;
    }

    @Override
    public Publisher<Boolean> add(final V obj) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.addAsync(obj);
            }
        });
    }

    @Override
    public Publisher<Boolean> addAll(final Collection<V> objects) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.addAllAsync(objects);
            }
        });
    }

    @Override
    public Publisher<Long> count() {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.countAsync();
            }
        });
    }

    @Override
    public Publisher<Long> countWith(final String... otherLogNames) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.countWithAsync(otherLogNames);
            }
        });
    }

    @Override
    public Publisher<Void> mergeWith(final String... otherLogNames) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.mergeWithAsync(otherLogNames);
            }
        });
    }

}
