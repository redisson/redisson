package org.redisson;

import io.netty.util.concurrent.Promise;

import com.lambdaworks.redis.RedisAsyncConnection;

public interface AsyncOperation<V, T> {

    void execute(Promise<T> promise, RedisAsyncConnection<Object, V> async);
    
}
