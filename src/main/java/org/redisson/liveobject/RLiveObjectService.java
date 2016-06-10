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

/**
 * The pre-registration of each entity class is not necessary.
 *
 * In ATTACHED Mode, entity's getters and setters propagate operations to Redis
 * automatically.
 *
 * In DETACHED Mode, entity's field values are kept local and only pushed to
 * Redis when update is called.
 *
 * @author Rui Gu (https://github.com/jackygurui)
 *
 */
public interface RLiveObjectService {

    /**
     * Finds the entity from Redis with the id.
     *
     * @param entityClass Entity class
     * @param id identifier
     * @param <T> Entity type
     * @param <K> Key type
     * @return In ATTACHED Mode, this always returns a proxy class. Even it does
     * not exist in redis. In DETACHED Mode, this returns an instance of the
     * entity class. IF it doesn't exist in redis, a runtime exception is
     * thrown.
     */
    public <T, K> T get(Class<T> entityClass, K id);

}
