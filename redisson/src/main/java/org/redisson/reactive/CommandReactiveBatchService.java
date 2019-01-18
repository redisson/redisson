/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.api.BatchOptions;
import org.redisson.api.BatchResult;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.NodeSource;
import org.redisson.misc.RPromise;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class CommandReactiveBatchService extends CommandReactiveService {

    private final CommandBatchService batchService;
    private final Queue<Publisher<?>> publishers = new ConcurrentLinkedQueue<Publisher<?>>();

    public CommandReactiveBatchService(ConnectionManager connectionManager) {
        super(connectionManager);
        batchService = new CommandBatchService(connectionManager);
    }

    @Override
    public <R> Mono<R> reactive(Supplier<RFuture<R>> supplier) {
        Mono<R> publisher = super.reactive(supplier);
        publishers.add(publisher);
        return publisher;
    }
    
    public <R> Publisher<R> superReactive(Supplier<RFuture<R>> supplier) {
        return super.reactive(supplier);
    }
    
    @Override
    public <V, R> void async(boolean readOnlyMode, NodeSource nodeSource,
            Codec codec, RedisCommand<V> command, Object[] params, RPromise<R> mainPromise, int attempt, boolean ignoreRedirect) {
        batchService.async(readOnlyMode, nodeSource, codec, command, params, mainPromise, attempt, ignoreRedirect);
    }

    public RFuture<BatchResult<?>> executeAsync(BatchOptions options) {
        for (Publisher<?> publisher : publishers) {
            Flux.from(publisher).subscribe();
        }

        return batchService.executeAsync(options);
    }

    @Override
    public CommandAsyncExecutor enableRedissonReferenceSupport(RedissonReactiveClient redissonReactive) {
        batchService.enableRedissonReferenceSupport(redissonReactive);
        return super.enableRedissonReferenceSupport(redissonReactive);
    }
    
}
