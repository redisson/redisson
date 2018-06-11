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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.redisson.api.BatchOptions;
import org.redisson.api.BatchResult;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.NodeSource;
import org.redisson.misc.RPromise;
import org.redisson.reactive.NettyFuturePublisher;

import reactor.fn.Supplier;
import reactor.rx.action.support.DefaultSubscriber;

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
        NettyFuturePublisher<R> publisher = new NettyFuturePublisher<R>(supplier);
        publishers.add(publisher);
        return publisher;
    }
    
    @Override
    protected <V, R> void async(boolean readOnlyMode, NodeSource nodeSource,
            Codec codec, RedisCommand<V> command, Object[] params, RPromise<R> mainPromise, int attempt, boolean ignoreRedirect, RFuture<RedisConnection> connFuture) {
        batchService.async(readOnlyMode, nodeSource, codec, command, params, mainPromise, attempt, ignoreRedirect, connFuture);
    }

    public RFuture<BatchResult<?>> executeAsync(BatchOptions options) {
        for (Publisher<?> publisher : publishers) {
            publisher.subscribe(new DefaultSubscriber<Object>() {
                @Override
                public void onSubscribe(Subscription s) {
                    s.request(1);
                }
            });
        }

        return batchService.executeAsync(options);
    }

    @Override
    public CommandAsyncExecutor enableRedissonReferenceSupport(RedissonReactiveClient redissonReactive) {
        batchService.enableRedissonReferenceSupport(redissonReactive);
        return super.enableRedissonReferenceSupport(redissonReactive);
    }
    
}
