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

import java.util.concurrent.TimeUnit;

import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RBucket;

import io.netty.util.concurrent.Future;

public class RedissonBucket<V> extends RedissonExpirable implements RBucket<V> {

    protected RedissonBucket(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public V get() {
        return connectionManager.get(getAsync());
    }

    @Override
    public Future<V> getAsync() {
        return connectionManager.readAsync(getName(), RedisCommands.GET, getName());
    }

    @Override
    public void set(V value) {
        connectionManager.get(setAsync(value));
    }

    @Override
    public Future<Void> setAsync(V value) {
        return connectionManager.writeAsyncVoid(getName(), RedisCommands.SET, getName(), value);
    }

    @Override
    public void set(V value, long timeToLive, TimeUnit timeUnit) {
        connectionManager.get(setAsync(value, timeToLive, timeUnit));
    }

    @Override
    public Future<Void> setAsync(V value, long timeToLive, TimeUnit timeUnit) {
        return connectionManager.writeAsyncVoid(getName(), RedisCommands.SETEX, getName(), timeUnit.toSeconds(timeToLive), value);
    }

    @Override
    public boolean exists() {
        return connectionManager.get(existsAsync());
    }

    @Override
    public Future<Boolean> existsAsync() {
        return connectionManager.readAsync(getName(), RedisCommands.EXISTS, getName());
    }

}
