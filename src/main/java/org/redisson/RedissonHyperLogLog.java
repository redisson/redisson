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

import java.util.Collection;

import org.redisson.connection.ConnectionManager;
import org.redisson.core.RHyperLogLog;

import com.lambdaworks.redis.RedisConnection;

public class RedissonHyperLogLog<V> extends RedissonObject implements RHyperLogLog<V> {

    RedissonHyperLogLog(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public long add(V obj) {
        return addAsync(obj).awaitUninterruptibly().getNow();
    }

    @Override
    public long addAll(Collection<V> objects) {
        return addAllAsync(objects).awaitUninterruptibly().getNow();
    }

    @Override
    public long count() {
        return countAsync().awaitUninterruptibly().getNow();
    }

    @Override
    public long countWith(String... otherLogNames) {
        return countWithAsync(otherLogNames).awaitUninterruptibly().getNow();
    }

    @Override
    public long mergeWith(String... otherLogNames) {
        return mergeWithAsync(otherLogNames).awaitUninterruptibly().getNow();
    }

    @Override
    public Future<Long> addAsync(V obj) {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        return conn.getAsync().pfadd(getName(), obj).addListener(connectionManager.createReleaseWriteListener(conn));
    }

    @Override
    public Future<Long> addAllAsync(Collection<V> objects) {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        return conn.getAsync().pfadd(getName(), objects.toArray()).addListener(connectionManager.createReleaseWriteListener(conn));
    }

    @Override
    public Future<Long> countAsync() {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        return conn.getAsync().pfcount(getName()).addListener(connectionManager.createReleaseWriteListener(conn));
    }

    @Override
    public Future<Long> countWithAsync(String... otherLogNames) {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        return conn.getAsync().pfcount(getName(), otherLogNames).addListener(connectionManager.createReleaseWriteListener(conn));
    }

    @Override
    public Future<Long> mergeWithAsync(String... otherLogNames) {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        return conn.getAsync().pfmerge(getName(), otherLogNames).addListener(connectionManager.createReleaseWriteListener(conn));
    }

}
