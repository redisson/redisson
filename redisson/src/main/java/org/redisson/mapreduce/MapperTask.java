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

import java.util.Map.Entry;

import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.mapreduce.RCollector;
import org.redisson.api.mapreduce.RMapper;
import org.redisson.client.codec.Codec;
import org.redisson.misc.Injector;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <KIn> input key type
 * @param <VIn> input key type
 * @param <KOut> output key type
 * @param <VOut> output key type
 */
public class MapperTask<KIn, VIn, KOut, VOut> extends BaseMapperTask<KOut, VOut> {

    private static final long serialVersionUID = 2441161019495880394L;
    
    protected RMapper<KIn, VIn, KOut, VOut> mapper;
    
    public MapperTask() {
    }
    
    public MapperTask(RMapper<KIn, VIn, KOut, VOut> mapper, Class<?> objectClass, Class<?> objectCodecClass) {
        super(objectClass, objectCodecClass);
        this.mapper = mapper;
    }

    @Override
    public void run() {
        Codec codec;
        try {
            codec = (Codec) objectCodecClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        
        Injector.inject(mapper, redisson);
        RCollector<KOut, VOut> collector = new Collector<KOut, VOut>(codec, redisson, collectorMapName, workersAmount, timeout);

        for (String objectName : objectNames) {
            RMap<KIn, VIn> map = null;
            if (RMapCache.class.isAssignableFrom(objectClass)) {
                map = redisson.getMapCache(objectName, codec);
            } else {
                map = redisson.getMap(objectName, codec);
            }
            
            for (Entry<KIn, VIn> entry : map.entrySet()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                
                mapper.map(entry.getKey(), entry.getValue(), collector);
            }
        }
    }

}
