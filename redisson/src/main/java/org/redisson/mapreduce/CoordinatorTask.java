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

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.redisson.Redisson;
import org.redisson.api.RExecutorService;
import org.redisson.api.RFuture;
import org.redisson.api.RScheduledExecutorService;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;
import org.redisson.api.mapreduce.RCollator;
import org.redisson.api.mapreduce.RReducer;
import org.redisson.client.codec.Codec;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <KOut> output key
 * @param <VOut> output value
 */
public class CoordinatorTask<KOut, VOut> implements Callable<Object>, Serializable {

    private static final long serialVersionUID = 7559371478909848610L;

    @RInject
    protected RedissonClient redisson;
    
    private BaseMapperTask<KOut, VOut> mapperTask;
    private RCollator<KOut, VOut, Object> collator;
    private RReducer<KOut, VOut> reducer;
    protected String objectName;
    protected Class<?> objectClass;
    private Class<?> objectCodecClass;
    private String resultMapName;
    private long timeout;
    private long startTime;

    protected Codec codec;
    
    public CoordinatorTask() {
    }
    
    public CoordinatorTask(BaseMapperTask<KOut, VOut> mapperTask, RReducer<KOut, VOut> reducer, 
            String mapName, String resultMapName, Class<?> mapCodecClass, Class<?> objectClass,
            RCollator<KOut, VOut, Object> collator, long timeout, long startTime) {
        super();
        this.mapperTask = mapperTask;
        this.reducer = reducer;
        this.objectName = mapName;
        this.objectCodecClass = mapCodecClass;
        this.objectClass = objectClass;
        this.resultMapName = resultMapName;
        this.collator = collator;
        this.timeout = timeout;
        this.startTime = startTime;
    }

    @Override
    public Object call() throws Exception {
        long timeSpent = System.currentTimeMillis() - startTime;
        if (isTimeoutExpired(timeSpent)) {
            throw new MapReduceTimeoutException();
        }
        
        this.codec = (Codec) objectCodecClass.getConstructor().newInstance();
        
        RScheduledExecutorService executor = redisson.getExecutorService(RExecutorService.MAPREDUCE_NAME);
        int workersAmount = executor.countActiveWorkers();
        
        UUID id = UUID.randomUUID();
        String collectorMapName = objectName + ":collector:" + id;

        mapperTask.setCollectorMapName(collectorMapName);
        mapperTask.setWorkersAmount(workersAmount);
        timeSpent = System.currentTimeMillis() - startTime;
        if (isTimeoutExpired(timeSpent)) {
            throw new MapReduceTimeoutException();
        }
        if (timeout > 0) {
            mapperTask.setTimeout(timeout - timeSpent);
        }

        mapperTask.addObjectName(objectName);
        RFuture<?> mapperFuture = executor.submitAsync(mapperTask);
        try {
            if (timeout > 0 && !mapperFuture.await(timeout - timeSpent)) {
                mapperFuture.cancel(true);
                throw new MapReduceTimeoutException();
            }
            if (timeout == 0) {
                mapperFuture.await();
            }
        } catch (InterruptedException e) {
            mapperFuture.cancel(true);
            return null;
        }

        SubTasksExecutor reduceExecutor = new SubTasksExecutor(executor, workersAmount, startTime, timeout);
        for (int i = 0; i < workersAmount; i++) {
            String name = collectorMapName + ":" + i;
            Runnable runnable = new ReducerTask<KOut, VOut>(name, reducer, objectCodecClass, resultMapName, timeout - timeSpent);
            reduceExecutor.submit(runnable);
        }

        if (!reduceExecutor.await()) {
            return null;
        }
        
        return executeCollator();
    }

    private Object executeCollator() throws ExecutionException, Exception {
        if (collator == null) {
            if (timeout > 0) {
                redisson.getMap(resultMapName).clearExpire();
            }
            return null;
        }
        
        Callable<Object> collatorTask = new CollatorTask<KOut, VOut, Object>(redisson, collator, resultMapName, objectCodecClass);
        long timeSpent = System.currentTimeMillis() - startTime;
        if (isTimeoutExpired(timeSpent)) {
            throw new MapReduceTimeoutException();
        }

        if (timeout > 0) {
            ExecutorService executor = ((Redisson) redisson).getConnectionManager().getExecutor();
            java.util.concurrent.Future<?> collatorFuture = executor.submit(collatorTask);
            try {
                return collatorFuture.get(timeout - timeSpent, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            } catch (TimeoutException e) {
                collatorFuture.cancel(true);
                throw new MapReduceTimeoutException();
            }
        } else {
            return collatorTask.call();
        }
    }

    private boolean isTimeoutExpired(long timeSpent) {
        return timeSpent > timeout && timeout > 0;
    }

}
