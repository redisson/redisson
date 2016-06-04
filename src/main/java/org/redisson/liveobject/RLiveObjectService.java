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
 * @author ruigu
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
