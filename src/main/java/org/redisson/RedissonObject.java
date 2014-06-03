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

import io.netty.util.concurrent.Promise;

import org.redisson.connection.ConnectionManager;
import org.redisson.core.RObject;

import com.lambdaworks.redis.RedisConnection;

/**
 * Base Redisson object
 *
 * @author Nikita Koksharov
 *
 */
abstract class RedissonObject implements RObject {

    final ConnectionManager connectionManager;
    private final String name;

    public RedissonObject(ConnectionManager connectionManager, String name) {
        this.connectionManager = connectionManager;
        this.name = name;
    }

    protected <V> Promise<V> newPromise() {
        return connectionManager.getGroup().next().<V>newPromise();
    }

    public String getName() {
        return name;
    }

    public void delete() {
        delete(getName());
    }

    void delete(String name) {
        RedisConnection<String, Object> connection = connectionManager.connectionWriteOp();
        try {
            connection.del(getName());
        } finally {
            connectionManager.release(connection);
        }
    }

    public void close() {
    }

}
