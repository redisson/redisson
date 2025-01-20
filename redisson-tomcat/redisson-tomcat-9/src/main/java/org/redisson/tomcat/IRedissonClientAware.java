package org.redisson.tomcat;

import org.redisson.api.RedissonClient;

public interface IRedissonClientAware {
    /**
     * method called from {@code RedissonSessionManager} to set redisson client
     * object.
     */
    void setRedissonClient(RedissonClient redissonClient);
}
