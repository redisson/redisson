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

import java.util.Collections;
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
    public String scriptLoad(String luaScript) {
        return connectionManager.get(scriptLoadAsync(luaScript));
    }

    @Override
    public Future<String> scriptLoadAsync(final String luaScript) {
        return connectionManager.writeAsync(new ResultOperation<String, Object>() {
            @Override
            protected Future<String> execute(RedisAsyncConnection<Object, Object> async) {
                return async.scriptLoad(luaScript);
            }
        });
    }
    
    @Override
    public <R> R eval(String luaScript, ReturnType returnType) {
        return eval(luaScript, returnType, Collections.emptyList());
    }
    
    @Override
    public <R> R eval(String luaScript, ReturnType returnType, List<Object> keys, Object... values) {
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

    @Override
    public <R> R evalR(String luaScript, ReturnType returnType, List<Object> keys, List<?> values, List<?> rawValues) {
        return (R) connectionManager.get(evalAsyncR(luaScript, returnType, keys, values, rawValues));
    }

    @Override
    public <R> Future<R> evalAsyncR(final String luaScript, final ReturnType returnType, final List<Object> keys, final List<?> values, final List<?> rawValues) {
        return connectionManager.writeAsync(new ResultOperation<R, Object>() {
            @Override
            protected Future<R> execute(RedisAsyncConnection<Object, Object> async) {
                return async.evalR(luaScript, ScriptOutputType.valueOf(returnType.toString()), keys, values, rawValues);
            }
        });
    }

    @Override
    public <R> R evalSha(String shaDigest, ReturnType returnType) {
        return evalSha(shaDigest, returnType, Collections.emptyList());
    }
    
    @Override
    public <R> R evalSha(String shaDigest, ReturnType returnType, List<Object> keys, Object... values) {
        return (R) connectionManager.get(evalShaAsync(shaDigest, returnType, keys, values));
    }
    
    @Override
    public <R> Future<R> evalShaAsync(final String shaDigest, final ReturnType returnType, final List<Object> keys, final Object... values) {
        return connectionManager.writeAsync(new ResultOperation<R, Object>() {
            @Override
            protected Future<R> execute(RedisAsyncConnection<Object, Object> async) {
                return async.evalsha(shaDigest, ScriptOutputType.valueOf(returnType.toString()), keys, values);
            }
        });
    }

    @Override
    public String scriptKill() {
        return connectionManager.get(scriptKillAsync());
    }
    
    @Override
    public Future<String> scriptKillAsync() {
        return connectionManager.writeAsync(new ResultOperation<String, Object>() {
            @Override
            protected Future<String> execute(RedisAsyncConnection<Object, Object> async) {
                return async.scriptKill();
            }
        });
    }
    
    @Override
    public List<Boolean> scriptExists(String ... shaDigests) {
        return connectionManager.get(scriptExistsAsync(shaDigests));
    }

    @Override
    public Future<List<Boolean>> scriptExistsAsync(final String ... shaDigests) {
        return connectionManager.writeAsync(new ResultOperation<List<Boolean>, Object>() {
            @Override
            protected Future<List<Boolean>> execute(RedisAsyncConnection<Object, Object> async) {
                return async.scriptExists(shaDigests);
            }
        });
    }
    
    @Override
    public String scriptFlush() {
        return connectionManager.get(scriptFlushAsync());
    }
    
    @Override
    public Future<String> scriptFlushAsync() {
        return connectionManager.writeAsync(new ResultOperation<String, Object>() {
            @Override
            protected Future<String> execute(RedisAsyncConnection<Object, Object> async) {
                return async.scriptFlush();
            }
        });
    }
    
}
