package org.redisson.async;

import com.lambdaworks.redis.RedisConnection;

public interface SyncOperation<V, R> {

    R execute(RedisConnection<Object, V> conn);
    
}
