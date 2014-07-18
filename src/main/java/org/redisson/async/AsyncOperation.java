package org.redisson.async;

import io.netty.util.concurrent.Promise;

import com.lambdaworks.redis.RedisAsyncConnection;

public interface AsyncOperation<V, R> {

    void execute(Promise<R> promise, RedisAsyncConnection<Object, V> async);
    
}
