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
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RListMultimap;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;
import org.redisson.api.mapreduce.RReducer;
import org.redisson.client.codec.Codec;
import org.redisson.misc.Injector;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <KOut> key
 * @param <VOut> value
 */
public class ReducerTask<KOut, VOut> implements Runnable, Serializable {

    private static final long serialVersionUID = 3556632668150314703L;

    @RInject
    private RedissonClient redisson;
    
    private String name;
    private String resultMapName;
    private RReducer<KOut, VOut> reducer;
    private Class<?> codecClass;
    private Codec codec;
    private long timeout;

    public ReducerTask() {
    }
    
    public ReducerTask(String name, RReducer<KOut, VOut> reducer, Class<?> codecClass, String resultMapName, long timeout) {
        this.name = name;
        this.reducer = reducer;
        this.resultMapName = resultMapName;
        this.codecClass = codecClass;
        this.timeout = timeout;
    }

    @Override
    public void run() {
        try {
            this.codec = (Codec) codecClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        
        Injector.inject(reducer, redisson);
        
        RMap<KOut, VOut> map = redisson.getMap(resultMapName);
        RListMultimap<KOut, VOut> multimap = redisson.getListMultimap(name, codec);
        for (KOut key : multimap.keySet()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            List<VOut> values = multimap.get(key);
            VOut out = reducer.reduce(key, values.iterator());
            map.put(key, out);
        }
        if (timeout > 0) {
            map.expire(timeout, TimeUnit.MILLISECONDS);
        }
        multimap.delete();
    }

}
