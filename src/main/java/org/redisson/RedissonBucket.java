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

import java.util.concurrent.TimeUnit;

import org.redisson.async.ResultOperation;
import org.redisson.async.VoidOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RBucket;

import com.lambdaworks.redis.RedisAsyncConnection;

public class RedissonBucket<V> extends RedissonExpirable implements RBucket<V> {

    RedissonBucket(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public V get() {
        return connectionManager.get(getAsync());
    }

    @Override
    public Future<V> getAsync() {
        return connectionManager.readAsync(new ResultOperation<V, V>() {
            @Override
            public Future<V> execute(RedisAsyncConnection<Object, V> async) {
                return async.get(getName());
            }
        });
    }

    @Override
    public void set(V value) {
        connectionManager.get(setAsync(value));
    }

    @Override
    public Future<Void> setAsync(final V value) {
        return connectionManager.writeAsync(new VoidOperation<V, String>() {
            @Override
            public Future<String> execute(RedisAsyncConnection<Object, V> async) {
                return async.set(getName(), value);
            }
        });
    }

    @Override
    public void set(V value, long timeToLive, TimeUnit timeUnit) {
        connectionManager.get(setAsync(value, timeToLive, timeUnit));
    }

    @Override
    public Future<Void> setAsync(final V value, final long timeToLive, final TimeUnit timeUnit) {
        return connectionManager.writeAsync(new VoidOperation<V, String>() {
            @Override
            public Future<String> execute(RedisAsyncConnection<Object, V> async) {
                return async.setex(getName(), timeUnit.toSeconds(timeToLive), value);
            }
        });
    }

}
