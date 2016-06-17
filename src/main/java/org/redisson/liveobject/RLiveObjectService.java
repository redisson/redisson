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
 * Entity's getters and setters operations gets redirected to Redis
 * automatically.
 *
 * @author Rui Gu (https://github.com/jackygurui)
 *
 */
public interface RLiveObjectService {

    /**
     * Finds the entity from Redis with the id.
     *
     * The entityClass should have a field annotated with RId, and the
     * entityClass itself should have REntity annotated. The type of the RId can
     * be anything <b>except</b> the followings:
     * <ol>
     * <li>An array i.e. byte[], int[], Integer[], etc.</li>
     * <li>or a RObject i.e. RedissonMap</li>
     * <li>or a Class with REntity annotation.</li>
     * </ol>
     *
     *
     * @param entityClass Entity class
     * @param id identifier
     * @param <T> Entity type
     * @param <K> Key type
     * @return Always returns a proxy class. Even it does not exist in redis.
     */
    <T, K> T get(Class<T> entityClass, K id);

    /**
     * Returns proxied attached object for the detached object. Discard all the
     * field values already in the instance.
     *
     * The class representing this object should have a field annotated with
     * RId, and the object should hold a non null value in that field.
     *
     * If this object is not in redis then a new <b>blank</b> proxied instance
     * with the same RId field value will be created.
     *
     * @param <T> Entity type
     * @param detachedObject
     * @return
     */
    <T> T attach(T detachedObject);

    /**
     * Returns proxied attached object for the detached object. Transfers all the
     * <b>NON NULL</b> field values to the redis server. It does not remove any
     * existing data in redis in case of the field value is null.
     *
     * The class representing this object should have a field annotated with
     * RId, and the object should hold a non null value in that field.
     *
     * If this object is not in redis then a new hash key will be created to
     * store it.
     *
     * @param <T> Entity type
     * @param detachedObject
     * @return
     */
    <T> T merge(T detachedObject);

    /**
     * Returns proxied attached object for the detached object. Transfers all the
     * <b>NON NULL</b> field values to the redis server. Only when the it does
     * not already exist.
     * 
     * The class representing this object should have a field annotated with
     * RId, and the object should hold a non null value in that field.
     *
     * If this object is not in redis then a new hash key will be created to
     * store it.
     *
     * @param <T> Entity type
     * @param detachedObject
     * @return
     */
    <T> T persist(T detachedObject);

    /**
     * Returns unproxied detached object for the attached object.
     *
     * @param <T> Entity type
     * @param attachedObject
     * @return
     */
    <T> T detach(T attachedObject);

    /**
     * Deletes attached object including all nested objects.
     *
     * @param <T> Entity type
     * @param attachedObject
     */
    <T> void remove(T attachedObject);

    /**
     * Deletes object by class and id including all nested objects
     *
     * @param <T> Entity type
     * @param <K> Key type
     * @param entityClass
     * @param id
     */
    <T, K> void remove(Class<T> entityClass, K id);

    /**
     *
     * @param <T>
     * @param instance
     * @return
     */
    <T> RLiveObject asLiveObject(T instance);

    /**
     *
     * @param <T>
     * @param instance
     * @return
     */
    <T> boolean isLiveObject(T instance);
}
