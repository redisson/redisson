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

import org.redisson.api.RLexSortedSet;
import org.redisson.api.RList;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSet;
import org.redisson.api.RSetCache;
import org.redisson.api.RSortedSet;
import org.redisson.api.mapreduce.RCollectionMapper;
import org.redisson.api.mapreduce.RCollector;
import org.redisson.client.codec.Codec;
import org.redisson.misc.Injector;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <VIn> input value type
 * @param <KOut> output key type
 * @param <VOut> output value type
 */
public class CollectionMapperTask<VIn, KOut, VOut> extends BaseMapperTask<KOut, VOut> {

    private static final long serialVersionUID = -2634049426877164580L;
    
    RCollectionMapper<VIn, KOut, VOut> mapper;
    
    public CollectionMapperTask() {
    }
    
    public CollectionMapperTask(RCollectionMapper<VIn, KOut, VOut> mapper, Class<?> objectClass, Class<?> objectCodecClass) {
        super(objectClass, objectCodecClass);
        this.mapper = mapper;
    }

    @Override
    public void run()  {
        Codec codec;
        try {
            codec = (Codec) objectCodecClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        
        Injector.inject(mapper, redisson);

        for (String objectName : objectNames) {
            Iterable<VIn> collection = null;
            if (RSetCache.class.isAssignableFrom(objectClass)) {
                collection = redisson.getSetCache(objectName, codec);
            } else if (RSet.class.isAssignableFrom(objectClass)) {
                collection = redisson.getSet(objectName, codec);
            } else if (RSortedSet.class.isAssignableFrom(objectClass)) {
                collection = redisson.getSortedSet(objectName, codec);
            } else if (RScoredSortedSet.class.isAssignableFrom(objectClass)) {
                collection = redisson.getScoredSortedSet(objectName, codec);
            } else if (RLexSortedSet.class.isAssignableFrom(objectClass)) {
                collection = (Iterable<VIn>) redisson.getLexSortedSet(objectName);
            } else if (RList.class.isAssignableFrom(objectClass)) {
                collection = redisson.getList(objectName, codec);
            } else {
                throw new IllegalStateException("Unable to work with " + objectClass);
            }
            
            RCollector<KOut, VOut> collector = new Collector<KOut, VOut>(codec, redisson, collectorMapName, workersAmount, timeout);
            
            for (VIn value : collection) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                
                mapper.map(value, collector);
            }
        }
    }

}
