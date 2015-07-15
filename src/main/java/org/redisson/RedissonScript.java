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

import java.util.Collections;
import java.util.List;

import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RScript;

import io.netty.util.concurrent.Future;

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
    public Future<String> scriptLoadAsync(String luaScript) {
        return connectionManager.writeAsync(RedisCommands.SCRIPT_LOAD, luaScript);
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
    public <R> Future<R> evalAsync(String luaScript, ReturnType returnType, List<Object> keys, Object... values) {
        return connectionManager.evalAsync(returnType.getCommand(), luaScript, keys, values);
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
    public <R> Future<R> evalShaAsync(String shaDigest, ReturnType returnType, List<Object> keys, Object... values) {
        return connectionManager.evalAsync(new RedisCommand(returnType.getCommand(), "EVALSHA"), shaDigest, keys, values);
    }

    @Override
    public boolean scriptKill() {
        return connectionManager.get(scriptKillAsync());
    }

    @Override
    public Future<Boolean> scriptKillAsync() {
        return connectionManager.writeAsync(RedisCommands.SCRIPT_KILL);
    }

    @Override
    public List<Boolean> scriptExists(String ... shaDigests) {
        return connectionManager.get(scriptExistsAsync(shaDigests));
    }

    @Override
    public Future<List<Boolean>> scriptExistsAsync(String ... shaDigests) {
        return connectionManager.writeAsync(RedisCommands.SCRIPT_EXISTS, shaDigests);
    }

    @Override
    public boolean scriptFlush() {
        return connectionManager.get(scriptFlushAsync());
    }

    @Override
    public Future<Boolean> scriptFlushAsync() {
        return connectionManager.writeAsync(RedisCommands.SCRIPT_FLUSH);
    }

}
