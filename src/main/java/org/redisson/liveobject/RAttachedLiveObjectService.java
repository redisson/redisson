/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.liveobject;

//import java.util.concurrent.TimeUnit;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 * 
 */
public interface RAttachedLiveObjectService extends RLiveObjectService {
  
    /**
     * Finds the entity from Redis with the id. 
     * 
     * @param entityClass Entity class
     * @param id identifier
     * @param timeToLive sets the time to live on the object. Any calls to the accessor
     *              of this object will renew this. If it is not been accessed
     *              before the ttl reaches. This object is then expires and
     *              removed from redis. Think of it is been garbage collected.
     * @param timeUnit sets the time unit of the time to live balue on the object.
     * @param <T> Entity type
     * @param <K> Key type
     * @return In ATTACHED Mode, this always returns a proxy class. Even it does
     *              not exist in redis.
     */
//    public <T, K> T get(Class<T> entityClass, K id, long timeToLive, TimeUnit timeUnit);
}
