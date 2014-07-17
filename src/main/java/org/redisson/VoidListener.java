package org.redisson;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import com.lambdaworks.redis.RedisAsyncConnection;

public class VoidListener<V, T> extends OperationListener<V, Void, String> {

    public VoidListener(Promise<Void> promise, RedisAsyncConnection<Object, V> async,
            AsyncOperation<V, Void> timeoutCallback) {
        super(promise, async, timeoutCallback);
    }

    @Override
    public void onOperationComplete(Future<String> future) throws Exception {
        promise.setSuccess(null);
    }

}
