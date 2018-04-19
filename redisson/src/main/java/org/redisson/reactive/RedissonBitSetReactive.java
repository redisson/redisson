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

import java.util.BitSet;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonBitSet;
import org.redisson.api.RBitSetAsync;
import org.redisson.api.RBitSetReactive;
import org.redisson.api.RFuture;
import org.redisson.client.codec.BitSetCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandReactiveExecutor;

import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonBitSetReactive extends RedissonExpirableReactive implements RBitSetReactive {

    private final RBitSetAsync instance;
    
    public RedissonBitSetReactive(CommandReactiveExecutor connectionManager, String name) {
        this(connectionManager, name, new RedissonBitSet(connectionManager, name));
    }

    public RedissonBitSetReactive(CommandReactiveExecutor connectionManager, String name, RBitSetAsync instance) {
        super(connectionManager, name, instance);
        this.instance = instance;
    }

    public Publisher<Boolean> get(final long bitIndex) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.getAsync(bitIndex);
            }
        });
    }

    public Publisher<Boolean> set(final long bitIndex, final boolean value) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.setAsync(bitIndex, value);
            }
        });
    }

    public Publisher<byte[]> toByteArray() {
        return reactive(new Supplier<RFuture<byte[]>>() {
            @Override
            public RFuture<byte[]> get() {
                return instance.toByteArrayAsync();
            }
        });
    }

    public Publisher<BitSet> asBitSet() {
        return commandExecutor.readReactive(getName(), BitSetCodec.INSTANCE, RedisCommands.GET, getName());
    }

    @Override
    public Publisher<Long> length() {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.lengthAsync();
            }
        });
    }

    @Override
    public Publisher<Void> set(final long fromIndex, final long toIndex, final boolean value) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.setAsync(fromIndex, toIndex, value);
            }
        });
    }

    @Override
    public Publisher<Void> clear(final long fromIndex, final long toIndex) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.clearAsync(fromIndex, toIndex);
            }
        });
    }

    @Override
    public Publisher<Void> set(final BitSet bs) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.setAsync(bs);
            }
        });
    }

    @Override
    public Publisher<Void> not() {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.notAsync();
            }
        });
    }

    @Override
    public Publisher<Void> set(final long fromIndex, final long toIndex) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.setAsync(fromIndex, toIndex);
            }
        });
    }

    @Override
    public Publisher<Long> size() {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.sizeAsync();
            }
        });
    }

    @Override
    public Publisher<Boolean> set(final long bitIndex) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.setAsync(bitIndex);
            }
        });
    }

    @Override
    public Publisher<Long> cardinality() {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.cardinalityAsync();
            }
        });
    }

    @Override
    public Publisher<Boolean> clear(final long bitIndex) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.clearAsync(bitIndex);
            }
        });
    }

    @Override
    public Publisher<Void> clear() {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.clearAsync();
            }
        });
    }

    @Override
    public Publisher<Void> or(final String... bitSetNames) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.orAsync(bitSetNames);
            }
        });
    }

    @Override
    public Publisher<Void> and(final String... bitSetNames) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.andAsync(bitSetNames);
            }
        });
    }

    @Override
    public Publisher<Void> xor(final String... bitSetNames) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.xorAsync(bitSetNames);
            }
        });
    }

    @Override
    public String toString() {
        return Mono.from(asBitSet()).block().toString();
    }

}
