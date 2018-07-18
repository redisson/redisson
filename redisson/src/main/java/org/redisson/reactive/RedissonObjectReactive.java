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

import java.io.IOException;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;
import org.redisson.RedissonReference;
import org.redisson.api.RFuture;
import org.redisson.api.RObjectAsync;
import org.redisson.api.RObjectReactive;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveExecutor;
import org.redisson.misc.RedissonObjectFactory;

import io.netty.buffer.ByteBuf;
import reactor.core.publisher.Mono;

/**
 * Base Redisson object
 *
 * @author Nikita Koksharov
 *
 */
abstract class RedissonObjectReactive implements RObjectReactive {

    final CommandReactiveExecutor commandExecutor;
    private final String name;
    final Codec codec;
    protected RObjectAsync instance;

    public RedissonObjectReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name, RObjectAsync instance) {
        this.codec = codec;
        this.name = name;
        this.commandExecutor = commandExecutor;
        this.instance = instance;
    }

    public <R> Publisher<R> reactive(Supplier<RFuture<R>> supplier) {
        return commandExecutor.reactive(supplier);
    }

    public RedissonObjectReactive(CommandReactiveExecutor commandExecutor, String name, RObjectAsync instance) {
        this(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name, instance);
    }

    protected <V> Mono<V> newSucceeded(V result) {
        return Mono.just(result);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Codec getCodec() {
        return codec;
    }
    
    protected void encode(Collection<Object> params, Collection<?> values) {
        for (Object object : values) {
            params.add(encode(object));
        }
    }
    
    protected ByteBuf encode(Object value) {
        if (commandExecutor.isRedissonReferenceSupportEnabled()) {
            RedissonReference reference = RedissonObjectFactory.toReference(commandExecutor.getConnectionManager().getCfg(), value);
            if (reference != null) {
                value = reference;
            }
        }
        
        try {
            return codec.getValueEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    protected ByteBuf encodeMapKey(Object value) {
        if (commandExecutor.isRedissonReferenceSupportEnabled()) {
            RedissonReference reference = RedissonObjectFactory.toReference(commandExecutor.getConnectionManager().getCfg(), value);
            if (reference != null) {
                value = reference;
            }
        }
        
        try {
            return codec.getMapKeyEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected ByteBuf encodeMapValue(Object value) {
        if (commandExecutor.isRedissonReferenceSupportEnabled()) {
            RedissonReference reference = RedissonObjectFactory.toReference(commandExecutor.getConnectionManager().getCfg(), value);
            if (reference != null) {
                value = reference;
            }
        }

        try {
            return codec.getMapValueEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    @Override
    public Publisher<Void> restore(final byte[] state) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.restoreAsync(state);
            }
        });
    }
    
    @Override
    public Publisher<Void> restore(final byte[] state, final long timeToLive, final TimeUnit timeUnit) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.restoreAsync(state, timeToLive, timeUnit);
            }
        });
    }
    
    @Override
    public Publisher<Void> restoreAndReplace(final byte[] state) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.restoreAndReplaceAsync(state);
            }
        });
    }

    @Override
    public Publisher<Void> restoreAndReplace(final byte[] state, final long timeToLive, final TimeUnit timeUnit) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.restoreAndReplaceAsync(state, timeToLive, timeUnit);
            }
        });
    }
    
    @Override
    public Publisher<byte[]> dump() {
        return reactive(new Supplier<RFuture<byte[]>>() {
            @Override
            public RFuture<byte[]> get() {
                return instance.dumpAsync();
            }
        });
    }
    
    @Override
    public Publisher<Boolean> touch() {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.touchAsync();
            }
        });
    }
    
    @Override
    public Publisher<Boolean> unlink() {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.unlinkAsync();
            }
        });
    }
    
    @Override
    public Publisher<Void> copy(final String host, final int port, final int database, final long timeout) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.copyAsync(host, port, database, timeout);
            }
        });
    }
    
    @Override
    public Publisher<Void> rename(final String newName) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.renameAsync(newName);
            }
        });
    }

    @Override
    public Publisher<Void> migrate(final String host, final int port, final int database, final long timeout) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.migrateAsync(host, port, database, timeout);
            }
        });
    }

    @Override
    public Publisher<Boolean> move(final int database) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.moveAsync(database);
            }
        });
    }

    @Override
    public Publisher<Boolean> renamenx(final String newName) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.renamenxAsync(newName);
            }
        });
    }

    @Override
    public Publisher<Boolean> delete() {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.deleteAsync();
            }
        });
    }

    @Override
    public Publisher<Boolean> isExists() {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.isExistsAsync();
            }
        });
    }

}
