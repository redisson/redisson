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
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.redisson.api.mapreduce.RCollator;
import org.redisson.api.mapreduce.RMapReduceExecutor;
import org.redisson.api.mapreduce.RReducer;
import org.redisson.client.codec.Codec;
import org.redisson.connection.ConnectionManager;
import org.redisson.misc.RPromise;
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
    final String semaphoreName;
    final String resultMapName;
    
    final Codec objectCodec;
    final String objectName;
    final Class<?> objectClass;
    

    private ConnectionManager connectionManager;
    RReducer<KOut, VOut> reducer;
    M mapper;
    
    public MapReduceExecutor(RObject object, RedissonClient redisson, ConnectionManager connectionManager) {
        this.objectName = object.getName();
        this.objectCodec = object.getCodec();
        this.objectClass = object.getClass();

        this.redisson = redisson;
        UUID id = UUID.randomUUID();
        this.semaphoreName = object.getName() + ":semaphore:" + id;
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
        final RPromise<Map<KOut, VOut>> promise = connectionManager.newPromise();
        executeMapperAsync(resultMapName).addListener(new FutureListener<Void>() {
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
    
    @Override
    public void execute(String resultMapName) {
        connectionManager.getCommandExecutor().get(executeAsync(resultMapName));
    }
    
    @Override
    public RFuture<Void> executeAsync(String resultMapName) {
        final RPromise<Void> promise = connectionManager.newPromise();
        executeMapperAsync(resultMapName).addListener(new FutureListener<Void>() {

            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }

                promise.trySuccess(null);
            }
        });
        
        return promise;
    }


    private RPromise<Void> executeMapperAsync(String resultMapName) {
        if (mapper == null) {
            throw new NullPointerException("Mapper is not defined");
        }
        if (reducer == null) {
            throw new NullPointerException("Reducer is not defined");
        }
        
        final RPromise<Void> promise = connectionManager.newPromise();
        Callable<Integer> task = createTask(resultMapName);
        executorService.submit(task).addListener(new FutureListener<Integer>() {
            @Override
            public void operationComplete(Future<Integer> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }
                
                Integer workers = future.getNow();
                final RSemaphore semaphore = redisson.getSemaphore(semaphoreName);
                semaphore.acquireAsync(workers).addListener(new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        if (!future.isSuccess()) {
                            promise.tryFailure(future.cause());
                            return;
                        }
                        
                        semaphore.deleteAsync().addListener(new FutureListener<Boolean>() {
                            @Override
                            public void operationComplete(Future<Boolean> future) throws Exception {
                                if (!future.isSuccess()) {
                                    promise.tryFailure(future.cause());
                                    return;
                                }
                                
                                promise.trySuccess(null);
                            }
                        });
                    }
                });
            }
        });
        return promise;
    }

    protected abstract Callable<Integer> createTask(String resultMapName);
    
    @Override
    public <R> R execute(RCollator<KOut, VOut, R> collator) {
        return connectionManager.getCommandExecutor().get(executeAsync(collator));
    }
    
    @Override
    public <R> RFuture<R> executeAsync(final RCollator<KOut, VOut, R> collator) {
        check(collator);
        
        final RPromise<R> promise = connectionManager.newPromise();
        executeMapperAsync(resultMapName).addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }

                Callable<R> collatorTask = new CollatorTask<KOut, VOut, R>(collator, resultMapName, objectCodec.getClass()); 
                executorService.submit(collatorTask).addListener(new FutureListener<R>() {
                    @Override
                    public void operationComplete(Future<R> future) throws Exception {
                        if (!future.isSuccess()) {
                            promise.tryFailure(future.cause());
                            return;
                        }
                        
                        promise.trySuccess(future.getNow());
                    }
                });
            }
        });

        return promise;
    }
    
}
