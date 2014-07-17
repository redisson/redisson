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

import com.lambdaworks.redis.RedisAsyncConnection;
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
        return connectionManager.readAsync(new AsyncOperation<V, V>() {
            @Override
            public void execute(final Promise<V> promise, RedisAsyncConnection<Object, V> async) {
                async.get(getName()).addListener(new OperationListener<V, V, V>(promise, async, this) {
                    @Override
                    public void onOperationComplete(Future<V> future) throws Exception {
                        promise.setSuccess(future.get());
                    }
                });
            }
        });
    }

    @Override
    public void set(V value) {
        setAsync(value).awaitUninterruptibly().getNow();
    }

    @Override
    public Future<Void> setAsync(final V value) {
        return connectionManager.writeAsync(new AsyncOperation<V, Void>() {
            @Override
            public void execute(final Promise<Void> promise, RedisAsyncConnection<Object, V> async) {
                async.set(getName(), value)
                    .addListener(new VoidListener<V, String>(promise, async, this));
            }
        });
    }

    @Override
    public void set(V value, long timeToLive, TimeUnit timeUnit) {
        setAsync(value, timeToLive, timeUnit).awaitUninterruptibly().getNow();
    }

    @Override
    public Future<Void> setAsync(final V value, final long timeToLive, final TimeUnit timeUnit) {
        return connectionManager.writeAsync(new AsyncOperation<V, Void>() {
            @Override
            public void execute(final Promise<Void> promise, RedisAsyncConnection<Object, V> async) {
                async.setex(getName(), timeUnit.toSeconds(timeToLive), value)
                        .addListener(new VoidListener<V, String>(promise, async, this));
            }
        });
    }

}
