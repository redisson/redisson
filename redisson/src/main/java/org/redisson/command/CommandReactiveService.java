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
package org.redisson.command;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.SlotCallback;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;

import reactor.core.publisher.Flux;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class CommandReactiveService extends CommandAsyncService implements CommandReactiveExecutor {

    public CommandReactiveService(ConnectionManager connectionManager) {
        super(connectionManager);
    }

    @Override
    public <T, R> Publisher<R> evalWriteAllReactive(final RedisCommand<T> command, final SlotCallback<T, R> callback, final String script, final List<Object> keys, final Object ... params) {
        return reactive(new Supplier<RFuture<R>>() {
            @Override
            public RFuture<R> get() {
                return evalWriteAllAsync(command, callback, script, keys, params);
            };
        });
    }

    @Override
    public <R> Publisher<R> reactive(Supplier<RFuture<R>> supplier) {
        return Flux.create(emitter -> {
            emitter.onRequest(n -> {
                supplier.get().whenComplete((v, e) -> {
                    if (e != null) {
                        emitter.error(e);
                        return;
                    } 
                    if (v != null) {
                        emitter.next(v);
                    }
                    emitter.complete();
                });
            });
        });
    }

    @Override
    public <T, R> Publisher<Collection<R>> readAllReactive(final RedisCommand<T> command, final Object ... params) {
        return reactive(new Supplier<RFuture<Collection<R>>>() {
            @Override
            public RFuture<Collection<R>> get() {
                return readAllAsync(command, params);
            };
        });
    }

    @Override
    public <T, R> Publisher<R> readRandomReactive(final RedisCommand<T> command, final Object ... params) {
        return reactive(new Supplier<RFuture<R>>() {
            @Override
            public RFuture<R> get() {
                return readRandomAsync(StringCodec.INSTANCE, command, params);
            };
        });
    }

    @Override
    public <T, R> Publisher<R> readReactive(final RedisClient client, final String name, final Codec codec, final RedisCommand<T> command, final Object ... params) {
        return reactive(new Supplier<RFuture<R>>() {
            @Override
            public RFuture<R> get() {
                return readAsync(client, name, codec, command, params);
            };
        });
    }

    @Override
    public <T, R> Publisher<R> writeReactive(String key, RedisCommand<T> command, Object ... params) {
        return writeReactive(key, connectionManager.getCodec(), command, params);
    }

    @Override
    public <T, R> Publisher<R> writeReactive(final String key, final Codec codec, final RedisCommand<T> command, final Object ... params) {
        return reactive(new Supplier<RFuture<R>>() {
            @Override
            public RFuture<R> get() {
                return writeAsync(key, codec, command, params);
            };
        });
    }

    @Override
    public <T, R> Publisher<R> writeReactive(final MasterSlaveEntry entry, final Codec codec, final RedisCommand<T> command, final Object ... params) {
        return reactive(new Supplier<RFuture<R>>() {
            @Override
            public RFuture<R> get() {
                return writeAsync(entry, codec, command, params);
            };
        });
    }

    @Override
    public <T, R> Publisher<R> readReactive(String key, RedisCommand<T> command, Object ... params) {
        return readReactive(key, connectionManager.getCodec(), command, params);
    }

    @Override
    public <T, R> Publisher<R> readReactive(final String key, final Codec codec, final RedisCommand<T> command, final Object ... params) {
        return reactive(new Supplier<RFuture<R>>() {
            @Override
            public RFuture<R> get() {
                return readAsync(key, codec, command, params);
            };
        });
    }

    @Override
    public <T, R> Publisher<R> evalReadReactive(final String key, final Codec codec, final RedisCommand<T> evalCommandType,
            final String script, final List<Object> keys, final Object... params) {
        return reactive(new Supplier<RFuture<R>>() {
            @Override
            public RFuture<R> get() {
                return evalReadAsync(key, codec, evalCommandType, script, keys, params);
            };
        });
    }

    @Override
    public <T, R> Publisher<R> evalWriteReactive(final String key, final Codec codec, final RedisCommand<T> evalCommandType,
            final String script, final List<Object> keys, final Object... params) {
        return reactive(new Supplier<RFuture<R>>() {
            @Override
            public RFuture<R> get() {
                return evalWriteAsync(key, codec, evalCommandType, script, keys, params);
            };
        });
    }

    @Override
    public <T> Publisher<Void> writeAllReactive(final RedisCommand<T> command, final Object ... params) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return writeAllAsync(command, params);
            }   
        });
    }

    @Override
    public <R, T> Publisher<R> writeAllReactive(final RedisCommand<T> command, final SlotCallback<T, R> callback, final Object ... params) {
        return reactive(new Supplier<RFuture<R>>() {
            @Override
            public RFuture<R> get() {
                return writeAllAsync(command, callback, params);
            };
        });
    }


}
