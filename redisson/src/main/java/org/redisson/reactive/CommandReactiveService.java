/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.api.RFuture;
import org.redisson.api.options.ObjectParams;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandAsyncService;
import org.redisson.connection.ConnectionManager;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class CommandReactiveService extends CommandAsyncService implements CommandReactiveExecutor {

    public CommandReactiveService(CommandAsyncExecutor executor, boolean trackChanges) {
        super(executor, trackChanges);
    }

    public CommandReactiveService(ConnectionManager connectionManager, RedissonObjectBuilder objectBuilder) {
        super(connectionManager, objectBuilder, RedissonObjectBuilder.ReferenceType.REACTIVE);
    }

    public CommandReactiveService(CommandAsyncExecutor executor, ObjectParams objectParams) {
        super(executor, objectParams);
    }

    @Override
    public CommandReactiveExecutor copy(boolean trackChanges) {
        return new CommandReactiveService(this, trackChanges);
    }

    @Override
    public CommandReactiveExecutor copy(ObjectParams objectParams) {
        return new CommandReactiveService(this, objectParams);
    }

    @Override
    public <R> Mono<R> reactive(Callable<RFuture<R>> supplier) {
        return Flux.<R>create(emitter -> {
            emitter.onRequest(n -> {
                RFuture<R> future;
                try {
                    future = supplier.call();
                } catch (Exception e) {
                    emitter.error(e);
                    return;
                }
                
                emitter.onDispose(() -> {
                    future.cancel(true);
                });

                future.whenComplete((v, e) -> {
                    if (e != null) {
                        if (e instanceof CompletionException) {
                            e = e.getCause();
                        }
                        emitter.error(e);
                        return;
                    }

                    if (v != null) {
                        emitter.next(v);
                    }
                    emitter.complete();
                });
            });
        }).next();
    }

    }
