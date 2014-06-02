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

import java.util.Collection;
import java.util.concurrent.Future;

import org.redisson.connection.ConnectionManager;
import org.redisson.core.RHyperLogLog;

import com.lambdaworks.redis.RedisConnection;

public class RedissonHyperLogLog<V> extends RedissonObject implements RHyperLogLog<V> {

    private final ConnectionManager connectionManager;

    RedissonHyperLogLog(ConnectionManager connectionManager, String name) {
        super(name);
        this.connectionManager = connectionManager;
    }

    @Override
    public long add(V obj) {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        try {
            return conn.pfadd(getName(), obj);
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public long addAll(Collection<V> objects) {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        try {
            return conn.pfadd(getName(), objects.toArray());
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public long count() {
        RedisConnection<String, Object> conn = connectionManager.connectionReadOp();
        try {
            return conn.pfcount(getName());
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public long countWith(String... otherLogNames) {
        RedisConnection<String, Object> conn = connectionManager.connectionReadOp();
        try {
            return conn.pfcount(getName(), otherLogNames);
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public long mergeWith(String... otherLogNames) {
        RedisConnection<String, Object> conn = connectionManager.connectionReadOp();
        try {
            return conn.pfmerge(getName(), otherLogNames);
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public Future<Long> addAsync(V obj) {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        return conn.getAsync().pfadd(getName(), obj).addListener(connectionManager.createListener(conn));
    }

    @Override
    public Future<Long> addAllAsync(Collection<V> objects) {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        return conn.getAsync().pfadd(getName(), objects.toArray()).addListener(connectionManager.createListener(conn));
    }

    @Override
    public Future<Long> countAsync() {
        RedisConnection<String, Object> conn = connectionManager.connectionReadOp();
        return conn.getAsync().pfcount(getName()).addListener(connectionManager.createListener(conn));
    }

    @Override
    public Future<Long> countWithAsync(String... otherLogNames) {
        RedisConnection<String, Object> conn = connectionManager.connectionReadOp();
        return conn.getAsync().pfcount(getName(), otherLogNames).addListener(connectionManager.createListener(conn));
    }

    @Override
    public Future<Long> mergeWithAsync(String... otherLogNames) {
        RedisConnection<String, Object> conn = connectionManager.connectionWriteOp();
        return conn.getAsync().pfmerge(getName(), otherLogNames).addListener(connectionManager.createListener(conn));
    }

}
