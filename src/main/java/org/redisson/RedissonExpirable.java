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

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.redisson.connection.ConnectionManager;
import org.redisson.core.RExpirable;

import com.lambdaworks.redis.RedisConnection;

abstract class RedissonExpirable extends RedissonObject implements RExpirable {

    RedissonExpirable(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public boolean expire(long timeToLive, TimeUnit timeUnit) {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            return connection.expire(getName(), timeUnit.toSeconds(timeToLive));
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean expireAt(long timestamp) {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            return connection.expireat(getName(), timestamp);
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean expireAt(Date timestamp) {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            return connection.expireat(getName(), timestamp);
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean clearExpire() {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            return connection.persist(getName());
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public long remainTimeToLive() {
        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            return connection.ttl(getName());
        } finally {
            connectionManager.release(connection);
        }
    }

}
