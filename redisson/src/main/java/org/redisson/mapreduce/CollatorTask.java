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

import java.util.concurrent.Callable;

import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;
import org.redisson.api.mapreduce.RCollator;
import org.redisson.client.codec.Codec;
import org.redisson.misc.Injector;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <KOut> key type
 * @param <VOut> value type
 * @param <R> result type
 */
public class CollatorTask<KOut, VOut, R> implements Callable<R> {

    @RInject
    private RedissonClient redisson;
    
    private RCollator<KOut, VOut, R> collator;
    
    private String resultMapName;
    private Class<?> codecClass;
    
    private Codec codec;
    
    public CollatorTask() {
    }
    
    public CollatorTask(RedissonClient redisson, RCollator<KOut, VOut, R> collator, String resultMapName, Class<?> codecClass) {
        super();
        this.redisson = redisson;
        this.collator = collator;
        this.resultMapName = resultMapName;
        this.codecClass = codecClass;
    }

    @Override
    public R call() throws Exception {
        this.codec = (Codec) codecClass.getConstructor().newInstance();
        
        Injector.inject(collator, redisson);
        
        RMap<KOut, VOut> resultMap = redisson.getMap(resultMapName, codec);
        R result = collator.collate(resultMap);
        resultMap.delete();
        return result;
    }

}
