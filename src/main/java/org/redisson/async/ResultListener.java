package org.redisson.async;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import com.lambdaworks.redis.RedisAsyncConnection;

public class ResultListener<V, R> extends BaseResultListener<V, R, R> {

    public ResultListener(Promise<R> promise, RedisAsyncConnection<Object, V> async,
            AsyncOperation<V, R> timeoutCallback) {
        super(promise, async, timeoutCallback);
    }

    @Override
    public void onOperationComplete(Future<R> future) throws Exception {
        promise.setSuccess(future.get());
    }

}
