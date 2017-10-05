/**
 * Copyright 2016 Nikita Koksharov
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

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.NodeSource;
import org.redisson.misc.RPromise;

import reactor.core.publisher.Flux;
import reactor.core.publisher.MonoProcessor;

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
    public <R> Publisher<R> reactive(Supplier<RFuture<R>> supplier) {
        Publisher<R> publisher = super.reactive(supplier);
        publishers.add(publisher);
        return publisher;
    }
    
    @Override
    protected <V, R> void async(boolean readOnlyMode, NodeSource nodeSource,
            Codec codec, RedisCommand<V> command, Object[] params, RPromise<R> mainPromise, int attempt) {
        batchService.async(readOnlyMode, nodeSource, codec, command, params, mainPromise, attempt);
    }

    public List<?> execute() {
        return get(executeAsync(0, 0, 0));
    }
    
    public List<?> execute(long responseTimeout, int retryAttempts, long retryInterval) {
        return get(executeAsync(responseTimeout, retryAttempts, retryInterval));
    }

    public RFuture<Void> executeAsyncVoid() {
        return executeAsyncVoid(false, 0, 0, 0);
    }
    
    private RFuture<Void> executeAsyncVoid(boolean noResult, long responseTimeout, int retryAttempts, long retryInterval) {
        for (Publisher<?> publisher : publishers) {
            Flux.from(publisher).subscribe();
//            publisher.subscribe(new Subscriber<Object>() {
//
//                @Override
//                public void onSubscribe(Subscription s) {
//                    s.request(1);
//                }
//
//                @Override
//                public void onError(Throwable t) {
//                }
//
//                @Override
//                public void onComplete() {
//                }
//
//                @Override
//                public void onNext(Object t) {
//                }
//            });
        }
        return batchService.executeAsyncVoid(noResult, responseTimeout, retryAttempts, retryInterval);
    }
    
    public void executeSkipResult(long timeout, int retryAttempts, long retryInterval) {
        get(executeSkipResultAsync(timeout, retryAttempts, retryInterval));
    }
    
    public RFuture<Void> executeSkipResultAsync(long timeout, int retryAttempts, long retryInterval) {
        return executeAsyncVoid(true, timeout, retryAttempts, retryInterval);
    }
    
    public RFuture<List<?>> executeAsync() {
        return executeAsync(0, 0, 0);
    }
    
    public RFuture<List<?>> executeAsync(long responseTimeout, int retryAttempts, long retryInterval) {
        for (Publisher<?> publisher : publishers) {
            Flux.from(publisher).subscribe();
//            publisher.subscribe(new Subscriber<Object>() {
//
//                @Override
//                public void onSubscribe(Subscription s) {
//                    s.request(1);
//                }
//
//                @Override
//                public void onError(Throwable t) {
//                }
//
//                @Override
//                public void onComplete() {
//                }
//
//                @Override
//                public void onNext(Object t) {
//                }
//            });
        }

        return batchService.executeAsync(responseTimeout, retryAttempts, retryInterval);
    }

    @Override
    public CommandAsyncExecutor enableRedissonReferenceSupport(RedissonReactiveClient redissonReactive) {
        batchService.enableRedissonReferenceSupport(redissonReactive);
        return super.enableRedissonReferenceSupport(redissonReactive);
    }
    
}
