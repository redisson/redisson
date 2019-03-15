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
package org.redisson.api;

import java.util.Collection;

import org.redisson.api.condition.Condition;
import org.redisson.api.condition.Conditions;

/**
 * The pre-registration of each entity class is not necessary.
 *
 * Entity's getters and setters operations gets redirected to Redis
 * automatically.
 *
 * @author Rui Gu (https://github.com/jackygurui)
 * @author Nikita Koksharov
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
     * @param entityClass - entity class
     * @param id identifier
     * @param <T> Entity type
     * @return a proxied object if it exists in redis, or null if not.
     */
    <T> T get(Class<T> entityClass, Object id);
    
    /**
     * Finds the entities matches specified <code>condition</code>.
     * Usage example:
     * <pre>
     * Collection objects = liveObjectService.find(MyObject.class, Conditions.or(Conditions.in("field", "value1", "value2"), 
     *                          Conditions.and(Conditions.eq("field2", "value2"), Conditions.eq("field3", "value5"))));
     * </pre>
     * 
     * @see Conditions
     * 
     * @param <T> Entity type
     * @param entityClass - entity class
     * @param condition - condition object 
     * @return collection of live objects or empty collection.
     */
    <T> Collection<T> find(Class<T> entityClass, Condition condition);

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
     * @param detachedObject - not proxied object
     * @return proxied object
     * @throws IllegalArgumentException if the object is is a RLiveObject instance.
     */
    <T> T attach(T detachedObject);

    /**
     * Returns proxied object for the detached object. Transfers all the
     * <b>NON NULL</b> field values to the redis server. It does not delete any
     * existing data in redis in case of the field value is null.
     * 
     * The class representing this object should have a field annotated with
     * RId, and the object should hold a non null value in that field.
     * 
     * If this object is not in redis then a new hash key will be created to
     * store it.
     *
     * @param <T> Entity type
     * @param detachedObject - not proxied object
     * @return proxied object
     * @throws IllegalArgumentException if the object is is a RLiveObject instance.
     */
    <T> T merge(T detachedObject);

    /**
     * Returns proxied attached object for the detached object. Transfers all the
     * <b>NON NULL</b> field values to the redis server. Only when the it does
     * not already exist.
     * 
     * If this object is not in redis then a new hash key will be created to
     * store it.
     *
     * @param <T> Entity type
     * @param detachedObject - not proxied object
     * @return proxied object
     */
    <T> T persist(T detachedObject);

    /**
     * Returns unproxied detached object for the attached object.
     *
     * @param <T> Entity type
     * @param attachedObject - proxied object
     * @return proxied object
     */
    <T> T detach(T attachedObject);

    /**
     * Deletes attached object including all nested objects.
     *
     * @param <T> Entity type
     * @param attachedObject - proxied object
     */
    <T> void delete(T attachedObject);

    /**
     * Deletes object by class and id including all nested objects.
     *
     * @param <T> Entity type
     * @param entityClass - object class
     * @param id - object id
     * 
     * @return <code>true</code> if entity was deleted successfully, <code>false</code> otherwise 
     */
    <T> boolean delete(Class<T> entityClass, Object id);
    
    /**
     * To cast the instance to RLiveObject instance.
     * 
     * @param <T> type of instance
     * @param instance - live object
     * @return RLiveObject compatible object
     */
    <T> RLiveObject asLiveObject(T instance);

    /**
     * To cast the instance to RExpirable instance.
     * 
     * @param <T> type of instance
     * @param instance - live object
     * @return RExpirable compatible object
     */
    <T> RExpirable asRExpirable(T instance);

    /**
     * To cast the instance to RMap instance.
     * 
     * @param <T> type of instance
     * @param <K> type of key
     * @param <V> type of value
     * @param instance - live object
     * @return RMap compatible object
     */
    <T, K, V> RMap<K, V> asRMap(T instance);

    /**
     * Returns true if the instance is a instance of RLiveObject.
     * 
     * @param <T> type of instance
     * @param instance - live object
     * @return <code>true</code> object is RLiveObject
     */
    <T> boolean isLiveObject(T instance);
    
    /**
     * Returns true if the RLiveObject already exists in redis. It will return false if
     * the passed object is not a RLiveObject.
     * 
     * @param <T> type of instance
     * @param instance - live object
     * @return <code>true</code> object exists
     */
    <T> boolean isExists(T instance);
    
    /**
     * Pre register the class with the service, registering all the classes on
     * startup can speed up the instance creation. This is <b>NOT</b> mandatory
     * since the class will also be registered lazily when it is first used.
     * 
     * All classed registered with the service is stored in a class cache.
     * 
     * The cache is independent between different RedissonClient instances. When
     * a class is registered in one RLiveObjectService instance it is also
     * accessible in another RLiveObjectService instance so long as they are 
     * created by the same RedissonClient instance.
     * 
     * @param cls - class 
     */
    void registerClass(Class<?> cls);
    
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
    void unregisterClass(Class<?> cls);
    
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
     * @param cls - type of instance
     * @return <code>true</code> if class already registered
     */
    boolean isClassRegistered(Class<?> cls);
}
