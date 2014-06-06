/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.TimeUnit;

import org.redisson.connection.ConnectionManager;
import org.redisson.core.RBucket;

import com.lambdaworks.redis.RedisConnection;

public class RedissonBucket<V> extends RedissonExpirable implements RBucket<V> {

    RedissonBucket(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public V get() {
        return getAsync().awaitUninterruptibly().getNow();
    }

    @Override
    public Future<V> getAsync() {
        RedisConnection<String, V> conn = connectionManager.connectionReadOp();
        return conn.getAsync().get(getName()).addListener(connectionManager.createReleaseReadListener(conn));
    }

    @Override
    public void set(V value) {
        setAsync(value).awaitUninterruptibly().getNow();
    }

    @Override
    public Future<Void> setAsync(V value) {
        RedisConnection<Object, V> connection = connectionManager.connectionWriteOp();
        Promise<Void> promise = connectionManager.getGroup().next().newPromise();
        Future<String> f = connection.getAsync().set(getName(), value);
        addListener(f, promise);
        promise.addListener(connectionManager.createReleaseWriteListener(connection));
        return promise;
    }

    @Override
    public void set(V value, long timeToLive, TimeUnit timeUnit) {
        setAsync(value, timeToLive, timeUnit).awaitUninterruptibly().getNow();
    }

    private void addListener(Future<String> future, final Promise<Void> promise) {
        future.addListener(new FutureListener<String>() {
            @Override
            public void operationComplete(Future<String> future) throws Exception {
                if (promise.isCancelled()) {
                    return;
                }
                if (future.isSuccess()) {
                    promise.setSuccess(null);
                } else {
                    promise.setFailure(promise.cause());
                }
            }
        });
    }

    @Override
    public Future<Void> setAsync(V value, long timeToLive, TimeUnit timeUnit) {
        RedisConnection<Object, V> connection = connectionManager.connectionWriteOp();
        Promise<Void> promise = connectionManager.getGroup().next().newPromise();
        Future<String> f = connection.getAsync().setex(getName(), timeUnit.toSeconds(timeToLive), value);
        addListener(f, promise);
        promise.addListener(connectionManager.createReleaseWriteListener(connection));
        return promise;
    }

}
