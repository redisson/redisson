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
import java.util.concurrent.TimeUnit;

import org.redisson.api.RObject;
import org.redisson.api.RedissonClient;
import org.redisson.api.mapreduce.RCollator;
import org.redisson.api.mapreduce.RMapReduce;
import org.redisson.api.mapreduce.RMapper;
import org.redisson.api.mapreduce.RReducer;
import org.redisson.connection.ConnectionManager;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <KIn> input key type
 * @param <VIn> input value type
 * @param <KOut> output key type
 * @param <VOut> output value type
 */
public class RedissonMapReduce<KIn, VIn, KOut, VOut> extends MapReduceExecutor<RMapper<KIn, VIn, KOut, VOut>, VIn, KOut, VOut> 
                                                        implements RMapReduce<KIn, VIn, KOut, VOut> {

    public RedissonMapReduce(RObject object, RedissonClient redisson, ConnectionManager connectionManager) {
        super(object, redisson, connectionManager);
    }

    @Override
    public RMapReduce<KIn, VIn, KOut, VOut> timeout(long timeout, TimeUnit unit) {
        this.timeout = unit.toMillis(timeout);
        return this;
    }

    @Override
    public RMapReduce<KIn, VIn, KOut, VOut> mapper(RMapper<KIn, VIn, KOut, VOut> mapper) {
        check(mapper);
        this.mapper = mapper;
        return this;
    }

    @Override
    public RMapReduce<KIn, VIn, KOut, VOut> reducer(RReducer<KOut, VOut> reducer) {
        check(reducer);
        this.reducer = reducer;
        return this;
    }

    @Override
    protected Callable<Object> createTask(String resultMapName, RCollator<KOut, VOut, Object> collator) {
        MapperTask<KIn, VIn, KOut, VOut> mapperTask = new MapperTask<KIn, VIn, KOut, VOut>(mapper, objectClass, objectCodec.getClass());
        return new CoordinatorTask<KOut, VOut>(mapperTask, reducer, objectName, resultMapName, objectCodec.getClass(), objectClass, collator, timeout, System.currentTimeMillis());
    }

}
