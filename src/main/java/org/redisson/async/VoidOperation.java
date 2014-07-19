package org.redisson.async;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import com.lambdaworks.redis.RedisAsyncConnection;

public abstract class VoidOperation<V, R> implements AsyncOperation<V, Void> {

    @Override
    public void execute(Promise<Void> promise, RedisAsyncConnection<Object, V> async) {
        Future<R> future = execute(async);
        future.addListener(new VoidListener<V, R>(promise, async, this));
    }

    protected abstract Future<R> execute(RedisAsyncConnection<Object, V> async);

}
