package org.redisson.async;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import com.lambdaworks.redis.RedisAsyncConnection;

public abstract class ResultOperation<R, V> implements AsyncOperation<V, R> {

    @Override
    public void execute(Promise<R> promise, RedisAsyncConnection<Object, V> async) {
        Future<R> future = execute(async);
        future.addListener(new ResultListener<V, R>(promise, async, this));
    }

    protected abstract Future<R> execute(RedisAsyncConnection<Object, V> async);

}
