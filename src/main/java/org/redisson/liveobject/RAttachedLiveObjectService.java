package org.redisson.liveobject;

/**
 *
 * @author ruigu
 * 
 * @param <T> Entity type
 * @param <K> Key type
 */
public interface RAttachedLiveObjectService<T, K> extends RLiveObjectService<T, K> {
  
    /**
     * Finds the entity from Redis with the id. 
     * 
     * @param entityClass Entity class
     * @param id identifier
     * @param ttl sets the time to live on the object. Any calls to the accessor
     *              of this object will renew this. If it is not been accessed
     *              before the ttl reaches. This object is then expires and
     *              removed from redis. Think of it is been garbage collected.
     * @return In ATTACHED Mode, this always returns a proxy class. Even it does
     *              not exist in redis.
     */
    public T get(Class<T> entityClass, K id, long ttl);
}
