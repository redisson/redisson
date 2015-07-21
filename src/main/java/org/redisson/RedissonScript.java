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
import org.redisson.core.RScript;

import io.netty.util.concurrent.Future;

public class RedissonScript implements RScript {

    private final CommandExecutor commandExecutor;

    protected RedissonScript(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @Override
    public String scriptLoad(String luaScript) {
        return scriptLoad(null, luaScript);
    }

    @Override
    public String scriptLoad(String key, String luaScript) {
        return commandExecutor.get(scriptLoadAsync(key, luaScript));
    }

    public Future<String> scriptLoadAsync(String luaScript) {
        return scriptLoadAsync(null, luaScript);
    }

    @Override
    public Future<String> scriptLoadAsync(String key, String luaScript) {
        return commandExecutor.writeAsync(key, RedisCommands.SCRIPT_LOAD, luaScript);
    }

    @Override
    public <R> R eval(Mode mode, String luaScript, ReturnType returnType) {
        return eval(null, mode, luaScript, returnType);
    }

    @Override
    public <R> R eval(String key, Mode mode, String luaScript, ReturnType returnType) {
        return eval(key, mode, luaScript, returnType, Collections.emptyList());
    }

    @Override
    public <R> R eval(Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values) {
        return eval(null, mode, luaScript, returnType, keys, values);
    }

    @Override
    public <R> R eval(String key, Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values) {
        return (R) commandExecutor.get(evalAsync(key, mode, luaScript, returnType, keys, values));
    }

    @Override
    public <R> Future<R> evalAsync(Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values) {
        return evalAsync(null, mode, luaScript, returnType, keys, values);
    }

    @Override
    public <R> Future<R> evalAsync(String key, Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values) {
        if (mode == Mode.READ_ONLY) {
            return commandExecutor.evalReadAsync(key, returnType.getCommand(), luaScript, keys, values);
        }
        return commandExecutor.evalWriteAsync(key, returnType.getCommand(), luaScript, keys, values);
    }

    @Override
    public <R> R evalSha(Mode mode, String shaDigest, ReturnType returnType) {
        return evalSha(null, mode, shaDigest, returnType);
    }

    @Override
    public <R> R evalSha(String key, Mode mode, String shaDigest, ReturnType returnType) {
        return evalSha(key, mode, shaDigest, returnType, Collections.emptyList());
    }

    @Override
    public <R> R evalSha(Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values) {
        return evalSha(null, mode, shaDigest, returnType, keys, values);
    }

    @Override
    public <R> R evalSha(String key, Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values) {
        return (R) commandExecutor.get(evalShaAsync(key, mode, shaDigest, returnType, keys, values));
    }

    @Override
    public <R> Future<R> evalShaAsync(Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values) {
        return evalShaAsync(null, mode, shaDigest, returnType, keys, values);
    }

    @Override
    public <R> Future<R> evalShaAsync(String key, Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values) {
        RedisCommand command = new RedisCommand(returnType.getCommand(), "EVALSHA");
        if (mode == Mode.READ_ONLY) {
            return commandExecutor.evalReadAsync(key, command, shaDigest, keys, values);
        }
        return commandExecutor.evalWriteAsync(key, command, shaDigest, keys, values);
    }

    @Override
    public boolean scriptKill() {
        return scriptKill(null);
    }

    @Override
    public boolean scriptKill(String key) {
        return commandExecutor.get(scriptKillAsync(key));
    }

    @Override
    public Future<Boolean> scriptKillAsync() {
        return scriptKillAsync(null);
    }

    @Override
    public Future<Boolean> scriptKillAsync(String key) {
        return commandExecutor.writeAsync(key, RedisCommands.SCRIPT_KILL);
    }

    @Override
    public List<Boolean> scriptExists(String key, String ... shaDigests) {
        return commandExecutor.get(scriptExistsAsync(key, shaDigests));
    }

    @Override
    public Future<List<Boolean>> scriptExistsAsync(String key, String ... shaDigests) {
        return commandExecutor.writeAsync(key, RedisCommands.SCRIPT_EXISTS, shaDigests);
    }

    @Override
    public boolean scriptFlush() {
        return scriptFlush(null);
    }

    @Override
    public boolean scriptFlush(String key) {
        return commandExecutor.get(scriptFlushAsync(key));
    }

    @Override
    public Future<Boolean> scriptFlushAsync() {
        return scriptFlushAsync(null);
    }

    @Override
    public Future<Boolean> scriptFlushAsync(String key) {
        return commandExecutor.writeAsync(key, RedisCommands.SCRIPT_FLUSH);
    }

}
