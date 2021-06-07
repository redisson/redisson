/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import org.redisson.api.*;
import org.redisson.api.mapreduce.RCollator;
import org.redisson.api.mapreduce.RMapReduceExecutor;
import org.redisson.api.mapreduce.RReducer;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.misc.TransferListener;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

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
    final CommandAsyncExecutor commandExecutor;


    RReducer<KOut, VOut> reducer;
    M mapper;
    long timeout;
    
    MapReduceExecutor(RObject object, RedissonClient redisson, CommandAsyncExecutor commandExecutor) {
        this.objectName = object.getName();
        this.objectCodec = object.getCodec();
        this.objectClass = object.getClass();

        this.redisson = redisson;
        UUID id = UUID.randomUUID();
        this.resultMapName = object.getName() + ":result:" + id;
        this.executorService = redisson.getExecutorService(RExecutorService.MAPREDUCE_NAME);
        this.commandExecutor = commandExecutor;
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
        return commandExecutor.get(executeAsync());
    }
    
    @Override
    public RFuture<Map<KOut, VOut>> executeAsync() {
        RPromise<Map<KOut, VOut>> promise = new RedissonPromise<Map<KOut, VOut>>();
        RFuture<Void> future = executeMapperAsync(resultMapName, null);
        if (timeout > 0) {
            commandExecutor.getConnectionManager().newTimeout(task -> {
                promise.tryFailure(new MapReduceTimeoutException());
            }, timeout, TimeUnit.MILLISECONDS);
        }

        addCancelHandling(promise, future);
        future.onComplete((res, e) -> {
            if (e != null) {
                promise.tryFailure(e);
                return;
            }
            
            RBatch batch = redisson.createBatch();
            RMapAsync<KOut, VOut> resultMap = batch.getMap(resultMapName, objectCodec);
            resultMap.readAllMapAsync().onComplete(new TransferListener<>(promise));
            resultMap.deleteAsync();
            RFuture<BatchResult<?>> batchFuture = batch.executeAsync();
            addCancelHandling(promise, batchFuture);
        });
        return promise;
    }

    private <T> void addCancelHandling(RPromise<T> promise, RFuture<?> future) {
        promise.onComplete((res, e) -> {
            if (promise.isCancelled()) {
                future.cancel(true);
            }
        });
    }
    
    @Override
    public void execute(String resultMapName) {
        commandExecutor.get(executeAsync(resultMapName));
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
        return commandExecutor.get(executeAsync(collator));
    }
    
    @Override
    public <R> RFuture<R> executeAsync(RCollator<KOut, VOut, R> collator) {
        check(collator);
        
        return executeMapperAsync(resultMapName, collator);
    }
    
}
