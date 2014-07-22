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

import org.redisson.async.ResultOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RHyperLogLog;

import com.lambdaworks.redis.RedisAsyncConnection;

public class RedissonHyperLogLog<V> extends RedissonObject implements RHyperLogLog<V> {

    RedissonHyperLogLog(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public long add(V obj) {
        return connectionManager.get(addAsync(obj));
    }

    @Override
    public long addAll(Collection<V> objects) {
        return connectionManager.get(addAllAsync(objects));
    }

    @Override
    public long count() {
        return connectionManager.get(countAsync());
    }

    @Override
    public long countWith(String... otherLogNames) {
        return connectionManager.get(countWithAsync(otherLogNames));
    }

    @Override
    public long mergeWith(String... otherLogNames) {
        return connectionManager.get(mergeWithAsync(otherLogNames));
    }

    @Override
    public Future<Long> addAsync(final V obj) {
        return connectionManager.writeAsync(new ResultOperation<Long, V>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, V> async) {
                return async.pfadd(getName(), obj);
            }
        });
    }

    @Override
    public Future<Long> addAllAsync(final Collection<V> objects) {
        return connectionManager.writeAsync(new ResultOperation<Long, Object>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, Object> async) {
                return async.pfadd(getName(), objects.toArray());
            }
        });
    }

    @Override
    public Future<Long> countAsync() {
        return connectionManager.writeAsync(new ResultOperation<Long, Object>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, Object> async) {
                return async.pfcount(getName());
            }
        });
    }

    @Override
    public Future<Long> countWithAsync(final String... otherLogNames) {
        return connectionManager.writeAsync(new ResultOperation<Long, Object>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, Object> async) {
                return async.pfcount(getName(), otherLogNames);
            }
        });
    }

    @Override
    public Future<Long> mergeWithAsync(final String... otherLogNames) {
        return connectionManager.writeAsync(new ResultOperation<Long, Object>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, Object> async) {
                return async.pfmerge(getName(), otherLogNames);
            }
        });
    }

}
