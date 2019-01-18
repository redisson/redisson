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
package org.redisson.rx;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.redisson.api.BatchOptions;
import org.redisson.api.BatchResult;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonRxClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.NodeSource;
import org.redisson.misc.RPromise;

import io.reactivex.Flowable;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class CommandRxBatchService extends CommandRxService {

    private final CommandBatchService batchService;
    private final Queue<Publisher<?>> publishers = new ConcurrentLinkedQueue<Publisher<?>>();

    public CommandRxBatchService(ConnectionManager connectionManager) {
        super(connectionManager);
        batchService = new CommandBatchService(connectionManager);
    }

    @Override
    public <R> Flowable<R> flowable(Callable<RFuture<R>> supplier) {
        Flowable<R> flowable = super.flowable(supplier);
        publishers.add(flowable);
        return flowable;
    }
    
    public <R> Flowable<R> superReactive(Callable<RFuture<R>> supplier) {
        return super.flowable(supplier);
    }
    
    @Override
    public <V, R> void async(boolean readOnlyMode, NodeSource nodeSource,
            Codec codec, RedisCommand<V> command, Object[] params, RPromise<R> mainPromise, int attempt, boolean ignoreRedirect) {
        batchService.async(readOnlyMode, nodeSource, codec, command, params, mainPromise, attempt, ignoreRedirect);
    }

    public RFuture<BatchResult<?>> executeAsync(BatchOptions options) {
        return batchService.executeAsync(options);
    }

    @Override
    public CommandAsyncExecutor enableRedissonReferenceSupport(RedissonRxClient redissonReactive) {
        batchService.enableRedissonReferenceSupport(redissonReactive);
        return super.enableRedissonReferenceSupport(redissonReactive);
    }
    
}
