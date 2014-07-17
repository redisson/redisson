package org.redisson.async;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import com.lambdaworks.redis.RedisAsyncConnection;

public abstract class BaseResultListener<V, R, T> extends OperationListener<V, R, T> {

    public BaseResultListener(Promise<R> promise, RedisAsyncConnection<Object, V> async,
            AsyncOperation<V, R> timeoutCallback) {
        super(promise, async, timeoutCallback);
    }

    @Override
    public abstract void onOperationComplete(Future<T> future) throws Exception;

}
