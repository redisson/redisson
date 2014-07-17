package org.redisson.async;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import com.lambdaworks.redis.RedisAsyncConnection;

public class VoidListener<V, T> extends BaseResultListener<V, Void, T> {

    public VoidListener(Promise<Void> promise, RedisAsyncConnection<Object, V> async,
            AsyncOperation<V, Void> timeoutCallback) {
        super(promise, async, timeoutCallback);
    }

    @Override
    public void onOperationComplete(Future<T> future) throws Exception {
        promise.setSuccess(null);
    }

}
