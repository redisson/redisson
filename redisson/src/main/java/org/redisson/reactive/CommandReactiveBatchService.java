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

import org.redisson.api.BatchOptions;
import org.redisson.api.BatchResult;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.command.BatchService;
import org.redisson.command.CommandBatchService;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.NodeSource;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.misc.CompletableFutureWrapper;
import reactor.core.publisher.Mono;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class CommandReactiveBatchService extends CommandReactiveService implements BatchService {

    private final CommandBatchService batchService;

    public CommandReactiveBatchService(ConnectionManager connectionManager, CommandReactiveExecutor commandExecutor, BatchOptions options) {
        super(connectionManager, commandExecutor.getObjectBuilder());
        batchService = new CommandBatchService(commandExecutor, options, RedissonObjectBuilder.ReferenceType.REACTIVE);
    }

    @Override
    public <R> Mono<R> reactive(Callable<RFuture<R>> supplier) {
        Mono<R> mono = super.reactive(new Callable<RFuture<R>>() {
            final CompletableFuture<R> future = new CompletableFuture<>();
            final AtomicBoolean lock = new AtomicBoolean();
            @Override
            public RFuture<R> call() throws Exception {
                if (lock.compareAndSet(false, true)) {
                    transfer(supplier.call().toCompletableFuture(), future);
                }
                return new CompletableFutureWrapper<>(future);
            }
        });
        mono.subscribe();
        return mono;
    }
    
    @Override
    protected <R> CompletableFuture<R> createPromise() {
        return batchService.createPromise();
    }
    
    @Override
    protected <V, R> RFuture<R> async(boolean readOnlyMode, NodeSource nodeSource,
                                        Codec codec, RedisCommand<V> command, Object[] params, boolean ignoreRedirect, boolean noRetry) {
        return batchService.async(readOnlyMode, nodeSource, codec, command, params, ignoreRedirect, noRetry);
    }

    public RFuture<BatchResult<?>> executeAsync() {
        return batchService.executeAsync();
    }

    @Override
    protected boolean isEvalCacheActive() {
        return false;
    }

    public RFuture<Void> discardAsync() {
        return batchService.discardAsync();
    }
}
