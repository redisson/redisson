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
package org.redisson.mapreduce;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.redisson.api.RBatch;
import org.redisson.api.RExecutorService;
import org.redisson.api.RFuture;
import org.redisson.api.RMapAsync;
import org.redisson.api.RObject;
import org.redisson.api.RedissonClient;
import org.redisson.api.mapreduce.RCollator;
import org.redisson.api.mapreduce.RMapReduceExecutor;
import org.redisson.api.mapreduce.RReducer;
import org.redisson.client.codec.Codec;
import org.redisson.connection.ConnectionManager;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.misc.TransferListener;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <M> mapper type
 * @param <VIn> input value type
 * @param <KOut> output key type
 * @param <VOut> output value type
 */
abstract class MapReduceExecutor<M, VIn, KOut, VOut> implements RMapReduceExecutor<VIn, KOut, VOut> {

    private final RedissonClient redisson;
    private final RExecutorService executorService;
    final String resultMapName;
    
    final Codec objectCodec;
    final String objectName;
    final Class<?> objectClass;
    

    private ConnectionManager connectionManager;
    RReducer<KOut, VOut> reducer;
    M mapper;
    long timeout;
    
    public MapReduceExecutor(RObject object, RedissonClient redisson, ConnectionManager connectionManager) {
        this.objectName = object.getName();
        this.objectCodec = object.getCodec();
        this.objectClass = object.getClass();

        this.redisson = redisson;
        UUID id = UUID.randomUUID();
        this.resultMapName = object.getName() + ":result:" + id;
        this.executorService = redisson.getExecutorService(RExecutorService.MAPREDUCE_NAME);
        this.connectionManager = connectionManager;
    }

    protected void check(Object task) {
        if (task == null) {
            throw new NullPointerException("Task is not defined");
        }
        if (task.getClass().isAnonymousClass()) {
            throw new IllegalArgumentException("Task can't be created using anonymous class");
        }
        if (task.getClass().isMemberClass()
                && !Modifier.isStatic(task.getClass().getModifiers())) {
            throw new IllegalArgumentException("Task class is an inner class and it should be static");
        }
    }
    
    @Override
    public Map<KOut, VOut> execute() {
        return connectionManager.getCommandExecutor().get(executeAsync());
    }
    
    @Override
    public RFuture<Map<KOut, VOut>> executeAsync() {
        final RPromise<Map<KOut, VOut>> promise = new RedissonPromise<Map<KOut, VOut>>();
        final RFuture<Void> future = executeMapperAsync(resultMapName, null);
        addCancelHandling(promise, future);
        future.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }
                
                RBatch batch = redisson.createBatch();
                RMapAsync<KOut, VOut> resultMap = batch.getMap(resultMapName, objectCodec);
                resultMap.readAllMapAsync().addListener(new TransferListener<Map<KOut, VOut>>(promise));
                resultMap.deleteAsync();
                batch.executeAsync();
            }
        });        
        return promise;
    }

    private <T> void addCancelHandling(final RPromise<T> promise, final RFuture<?> future) {
        promise.addListener(new FutureListener<T>() {
            @Override
            public void operationComplete(Future<T> f) throws Exception {
                if (promise.isCancelled()) {
                    future.cancel(true);
                }
            }
        });
    }
    
    @Override
    public void execute(String resultMapName) {
        connectionManager.getCommandExecutor().get(executeAsync(resultMapName));
    }
    
    @Override
    public RFuture<Void> executeAsync(String resultMapName) {
        return executeMapperAsync(resultMapName, null);
    }


    private <R> RFuture<R> executeMapperAsync(String resultMapName, RCollator<KOut, VOut, R> collator) {
        if (mapper == null) {
            throw new NullPointerException("Mapper is not defined");
        }
        if (reducer == null) {
            throw new NullPointerException("Reducer is not defined");
        }
        
        Callable<Object> task = createTask(resultMapName, (RCollator<KOut, VOut, Object>) collator);
        return (RFuture<R>) executorService.submit(task);
    }

    protected abstract Callable<Object> createTask(String resultMapName, RCollator<KOut, VOut, Object> collator);
    
    @Override
    public <R> R execute(RCollator<KOut, VOut, R> collator) {
        return connectionManager.getCommandExecutor().get(executeAsync(collator));
    }
    
    @Override
    public <R> RFuture<R> executeAsync(final RCollator<KOut, VOut, R> collator) {
        check(collator);
        
        return executeMapperAsync(resultMapName, collator);
    }
    
}
