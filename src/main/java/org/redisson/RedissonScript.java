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

import java.util.List;

import org.redisson.async.ResultOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RScript;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.ScriptOutputType;

public class RedissonScript implements RScript {

    private final ConnectionManager connectionManager;
    
    public RedissonScript(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public <R> R eval(final String luaScript, final ReturnType returnType, final List<Object> keys, final Object... values) {
        return (R) connectionManager.get(evalAsync(luaScript, returnType, keys, values));
    }

    @Override
    public <R> Future<R> evalAsync(final String luaScript, final ReturnType returnType, final List<Object> keys, final Object... values) {
        return connectionManager.writeAsync(new ResultOperation<R, Object>() {
            @Override
            protected Future<R> execute(RedisAsyncConnection<Object, Object> async) {
                return async.eval(luaScript, ScriptOutputType.valueOf(returnType.toString()), keys, values);
            }
        });
    }
    
    
}
