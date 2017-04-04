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

import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <KOut> output key
 * @param <VOut> output value
 */
public abstract class BaseMapperTask<KOut, VOut> implements Runnable, Serializable {

    private static final long serialVersionUID = 6224632826989873592L;

    @RInject
    protected RedissonClient redisson;
    
    protected Class<?> objectClass;
    protected String objectName;
    protected Class<?> objectCodecClass;
    
    protected int workersAmount;
    protected String collectorMapName;
    protected long timeout;
    
    public BaseMapperTask() {
    }
    
    public BaseMapperTask(Class<?> objectClass, String objectName, Class<?> objectCodecClass) {
        super();
        this.objectClass = objectClass;
        this.objectName = objectName;
        this.objectCodecClass = objectCodecClass;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
    
    public void setWorkersAmount(int workersAmount) {
        this.workersAmount = workersAmount;
    }

    public void setCollectorMapName(String collatorMapName) {
        this.collectorMapName = collatorMapName;
    }

}
