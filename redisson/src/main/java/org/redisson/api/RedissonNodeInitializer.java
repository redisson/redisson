package org.redisson.api;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public interface RedissonNodeInitializer {

    /**
     * Invoked during Redisson Node startup
     * 
     * @param redisson
     */
    void onStartup(RedissonClient redisson);
    
}
