package org.redisson.liveobject;

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
     * @param ttl sets the time to live on the object. Any calls to the accessor
     *              of this object will renew this. If it is not been accessed
     *              before the ttl reaches. This object is then expires and
     *              removed from redis. Think of it is been garbage collected.
     * @param <T> Entity type
     * @param <K> Key type
     * @return In ATTACHED Mode, this always returns a proxy class. Even it does
     *              not exist in redis.
     */
    public <T, K> T get(Class<T> entityClass, K id, long ttl);
}
