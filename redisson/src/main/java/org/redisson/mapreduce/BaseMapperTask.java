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

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.redisson.api.RExecutorService;
import org.redisson.api.RScheduledExecutorService;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;
import org.redisson.api.mapreduce.RCollector;
import org.redisson.api.mapreduce.RReducer;
import org.redisson.client.codec.Codec;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <KOut> output key
 * @param <VOut> output value
 */
public abstract class BaseMapperTask<KOut, VOut> implements Callable<Integer>, Serializable {

    private static final long serialVersionUID = 7559371478909848610L;

    @RInject
    protected RedissonClient redisson;
    
    private RReducer<KOut, VOut> reducer;
    protected String objectName;
    protected Class<?> objectClass;
    private Class<?> objectCodecClass;
    private String semaphoreName;
    private String resultMapName;

    protected Codec codec;
    
    public BaseMapperTask() {
    }
    
    public BaseMapperTask(RReducer<KOut, VOut> reducer, 
            String mapName, String semaphoreName, String resultMapName, Class<?> mapCodecClass, Class<?> objectClass) {
        super();
        this.reducer = reducer;
        this.objectName = mapName;
        this.objectCodecClass = mapCodecClass;
        this.objectClass = objectClass;
        this.semaphoreName = semaphoreName;
        this.resultMapName = resultMapName;
    }

    @Override
    public Integer call() {
        try {
            this.codec = (Codec) objectCodecClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        
        RScheduledExecutorService executor = redisson.getExecutorService(RExecutorService.MAPREDUCE_NAME);
        int workersAmount = executor.countActiveWorkers();

        UUID id = UUID.randomUUID();
                
        RCollector<KOut, VOut> collector = new Collector<KOut, VOut>(codec, redisson, objectName + ":collector:" + id, workersAmount);

        map(collector);
        
        for (int i = 0; i < workersAmount; i++) {
            String name = objectName + ":collector:" + id + ":" + i;
            Runnable runnable = new ReducerTask<KOut, VOut>(name, reducer, objectCodecClass, semaphoreName, resultMapName);
            executor.submit(runnable);
        }
        
        return workersAmount;
    }

    protected abstract void map(RCollector<KOut, VOut> collector);
}
