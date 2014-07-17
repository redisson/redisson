package org.redisson.async;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import com.lambdaworks.redis.RedisAsyncConnection;

public abstract class VoidOperation<V, K> implements AsyncOperation<V, Void> {

    @Override
    public void execute(Promise<Void> promise, RedisAsyncConnection<Object, V> async) {
        Future<K> future = execute(async);
        future.addListener(new VoidListener<V, K>(promise, async, this));
    }

    protected abstract Future<K> execute(RedisAsyncConnection<Object, V> async);

}
