package org.redisson.liveobject;

import io.netty.util.concurrent.Future;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 * 
 * @param <T> Entity type
 * @param <K> Key type
 */
public interface RDetachedLiveObjectService<T, K> extends RLiveObjectService {
    
    /**
     * Finds the entity from Redis with the id. 
     * 
     * @param entityClass Entity class
     * @param id identifier
     * @return In ATTACHED Mode, this always returns a proxy class. Even it does
     *              not exist in redis.
     *         In DETACHED Mode, this returns an instance of the entity class. 
     *              IF it doesn't exist in redis, a runtime exception is thrown.
     */
    public Future<T> getAsync(Class<T> entityClass, K id);
    
    /**
     * Persist the instance into redis
     * 
     * @param instance the instance to be persisted
     * @return K The id of the object.
     */
    public K persist(T instance);
    
    /**
     * Persist the instance into redis
     * 
     * @param instance the instance to be persisted
     * @return K The id of the object.
     */
    public Future<K> persistAsync(T instance);
    
    /**
     * Persist the instance into redis with specified time to live.
     * 
     * @param instance the instance to be persisted
     * @param ttl the time to live of the instance
     * @return K The id of the object.
     */
    public K persist(T instance, long ttl);
    
    /**
     * Persist the instance into redis with specified time to live.
     * 
     * @param instance
     * @param ttl the time to live of the instance
     * @return K The id of the object.
     */
    public Future<K> persistAsync(T instance, long ttl);
    
    /**
     * Remove the instance from redis by specifying the id
     * 
     * @param id
     */
    public void remove(K id);
    
    /**
     * Remove the instance from redis by specifying the id
     * 
     * @param id
     * @return Future.
     */
    public Future<Void> removeAsync(K id);
}
