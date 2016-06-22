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
     * Find or create the entity from Redis with the id.
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
     * @param <T> Entity type
     * @return Always returns a proxied object. Even it does not exist in redis.
     */
    <T> T create(Class<T> entityClass);

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
     * @return a proxied object if it exists in redis, or null if not.
     */
    <T, K> T get(Class<T> entityClass, K id);

    /**
     * Find or create the entity from Redis with the id.
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
     * @return Always returns a proxied object. Even it does not exist in redis.
     */
    <T, K> T getOrCreate(Class<T> entityClass, K id);

    /**
     * Returns proxied object for the detached object. Discard all the
     * field values already in the detached instance.
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
     * @throws IllegalArgumentException if the object is is a RLiveObject instance.
     */
    <T> T attach(T detachedObject);

    /**
     * Returns proxied object for the detached object. Transfers all the
     * <b>NON NULL</b> field values to the redis server. It does not delete any
 existing data in redis in case of the field value is null.

 The class representing this object should have a field annotated with
 RId, and the object should hold a non null value in that field.

 If this object is not in redis then a new hash key will be created to
 store it.
     *
     * @param <T> Entity type
     * @param detachedObject
     * @return
     * @throws IllegalArgumentException if the object is is a RLiveObject instance.
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
    <T> void delete(T attachedObject);

    /**
     * Deletes object by class and id including all nested objects.
     *
     * @param <T> Entity type
     * @param <K> Key type
     * @param entityClass
     * @param id
     */
    <T, K> void delete(Class<T> entityClass, K id);

    /**
     * To cast the instance to RLiveObject instance.
     * 
     * @param <T>
     * @param instance
     * @return
     */
    <T> RLiveObject asLiveObject(T instance);

    /**
     * Returns true if the instance is a instance of RLiveObject.
     * 
     * @param <T>
     * @param instance
     * @return
     */
    <T> boolean isLiveObject(T instance);
    
    /**
     * Returns true if the RLiveObject does not yet exist in redis. Also true if
     * the passed object is not a RLiveObject.
     * 
     * @param <T>
     * @param instance
     * @return
     */
    <T> boolean isExists(T instance);
    
    /**
     * Pre register the class with the service, registering all the classes on
     * startup can speed up the instance creation. This is <b>NOT</b> mandatory
     * since the class will also be registered lazyly when it first is used.
     * 
     * All classed registered with the service is stored in a class cache.
     * 
     * The cache is independent between different RedissonClient instances. When
     * a class is registered in one RLiveObjectService instance it is also
     * accessible in another RLiveObjectService instance so long as they are 
     * created by the same RedissonClient instance.
     * 
     * @param cls 
     */
    void registerClass(Class cls);
    
    /**
     * Unregister the class with the service. This is useful after you decide
     * the class is no longer required. 
     * 
     * A class will be automatically unregistered if the service encountered any
     * errors during proxying or creating the object, since those errors are not
     * recoverable.
     * 
     * All classed registered with the service is stored in a class cache.
     * 
     * The cache is independent between different RedissonClient instances. When
     * a class is registered in one RLiveObjectService instance it is also 
     * accessible in another RLiveObjectService instance so long as they are 
     * created by the same RedissonClient instance.
     * 
     * @param cls It can be either the proxied class or the unproxied conterpart.
     */
    void unregisterClass(Class cls);
    
    /**
     * Check if the class is registered in the cache. 
     * 
     * All classed registered with the service is stored in a class cache.
     * 
     * The cache is independent between different RedissonClient instances. When
     * a class is registered in one RLiveObjectService instance it is also 
     * accessible in another RLiveObjectService instance so long as they are 
     * created by the same RedissonClient instance.
     * 
     * @param cls
     * @return 
     */
    boolean isClassRegistered(Class cls);
}
