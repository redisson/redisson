/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import org.redisson.api.RFuture;
import org.redisson.api.RScript;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonScript implements RScript {

    private final Codec codec;
    private final CommandAsyncExecutor commandExecutor;

    public RedissonScript(CommandAsyncExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
        this.codec = commandExecutor.getServiceManager().getCfg().getCodec();
    }
    
    public RedissonScript(CommandAsyncExecutor commandExecutor, Codec codec) {
        this.commandExecutor = commandExecutor;
        this.codec = codec;
    }

    @Override
    public String scriptLoad(String luaScript) {
        return commandExecutor.get(scriptLoadAsync(luaScript));
    }

    public String scriptLoad(String key, String luaScript) {
        return commandExecutor.get(scriptLoadAsync(key, luaScript));
    }

    @Override
    public RFuture<String> scriptLoadAsync(String luaScript) {
        List<CompletableFuture<String>> futures = commandExecutor.executeAllAsync(RedisCommands.SCRIPT_LOAD, luaScript);
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        CompletableFuture<String> s = f.thenApply(r -> futures.get(0).getNow(null));
        return new CompletableFutureWrapper<>(s);
    }

    @Override
    public RFuture<String> scriptLoadAsync(String key, String luaScript) {
        return commandExecutor.writeAsync(key, StringCodec.INSTANCE, RedisCommands.SCRIPT_LOAD, luaScript);
    }

    @Override
    public <R> R eval(Mode mode, String luaScript, ReturnType returnType) {
        return eval(mode, luaScript, returnType, Collections.emptyList());
    }

    @Override
    public <R> R eval(Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values) {
        String key = null;
        if (!keys.isEmpty()) {
            key = (String) keys.get(0);
        }
        return eval(key, mode, luaScript, returnType, keys, values);
    }

    @Override
    public <R> RFuture<R> evalAsync(Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values) {
        String key = null;
        if (!keys.isEmpty()) {
            key = (String) keys.get(0);
        }
        return evalAsync(key, mode, luaScript, returnType, keys, values);
    }

    @Override
    public <R> R evalSha(Mode mode, String shaDigest, ReturnType returnType) {
        return evalSha(null, mode, shaDigest, returnType, Collections.emptyList());
    }

    @Override
    public <R> R evalSha(Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values) {
        return evalSha(null, mode, shaDigest, returnType, keys, values);
    }

    @Override
    public <R> RFuture<R> evalShaAsync(Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values) {
        return evalShaAsync(null, mode, codec, shaDigest, returnType, keys, values);
    }

    public <R> RFuture<R> evalShaAsync(String key, Mode mode, Codec codec, String shaDigest, ReturnType returnType, List<Object> keys, Object... values) {
        RedissonScript script = new RedissonScript(commandExecutor, codec);
        return script.evalShaAsync(key, mode, shaDigest, returnType, keys, values);
    }

    @Override
    public void scriptKill() {
        commandExecutor.get(scriptKillAsync());
    }

    public void scriptKill(String key) {
        commandExecutor.get(scriptKillAsync(key));
    }

    @Override
    public RFuture<Void> scriptKillAsync() {
        return commandExecutor.writeAllVoidAsync(RedisCommands.SCRIPT_KILL);
    }

    public RFuture<Void> scriptKillAsync(String key) {
        return commandExecutor.writeAsync(key, RedisCommands.SCRIPT_KILL);
    }

    @Override
    public List<Boolean> scriptExists(String... shaDigests) {
        return commandExecutor.get(scriptExistsAsync(shaDigests));
    }

    @Override
    public RFuture<List<Boolean>> scriptExistsAsync(String... shaDigests) {
        List<CompletableFuture<List<Boolean>>> futures = commandExecutor.executeAllAsync(RedisCommands.SCRIPT_EXISTS, (Object[]) shaDigests);
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        CompletableFuture<List<Boolean>> s = f.thenApply(r -> {
            List<Boolean> result = futures.get(0).getNow(new ArrayList<>());
            for (CompletableFuture<List<Boolean>> future : futures.subList(1, futures.size())) {
                List<Boolean> l = future.getNow(new ArrayList<>());
                for (int i = 0; i < l.size(); i++) {
                    result.set(i, result.get(i) | l.get(i));
                }
            }
            return result;
        });
        return new CompletableFutureWrapper<>(s);
    }

    public List<Boolean> scriptExists(String key, String... shaDigests) {
        return commandExecutor.get(scriptExistsAsync(key, shaDigests));
    }

    public RFuture<List<Boolean>> scriptExistsAsync(String key, String... shaDigests) {
        return commandExecutor.writeAsync(key, RedisCommands.SCRIPT_EXISTS, shaDigests);
    }

    @Override
    public void scriptFlush() {
        commandExecutor.get(scriptFlushAsync());
    }

    public void scriptFlush(String key) {
        commandExecutor.get(scriptFlushAsync(key));
    }

    @Override
    public RFuture<Void> scriptFlushAsync() {
        return commandExecutor.writeAllVoidAsync(RedisCommands.SCRIPT_FLUSH);
    }

    public RFuture<Void> scriptFlushAsync(String key) {
        return commandExecutor.writeAsync(key, RedisCommands.SCRIPT_FLUSH);
    }

    @Override
    public <R> RFuture<R> evalShaAsync(Mode mode, String shaDigest, ReturnType returnType) {
        return evalShaAsync(null, mode, codec, shaDigest, returnType, Collections.emptyList());
    }

    @Override
    public <R> RFuture<R> evalAsync(Mode mode, String luaScript, ReturnType returnType) {
        return evalAsync(null, mode, luaScript, returnType, Collections.emptyList());
    }

    private List<Object> encode(Collection<?> values, Codec codec) {
        List<Object> result = new ArrayList<Object>(values.size());
        for (Object object : values) {
            result.add(commandExecutor.encode(codec, object));
        }
        return result;
    }
    
    @Override
    public <R> RFuture<R> evalShaAsync(String key, Mode mode, String shaDigest, ReturnType returnType,
            List<Object> keys, Object... values) {
        RedisCommand command = new RedisCommand(returnType.getCommand(), "EVALSHA");
        String mappedKey = commandExecutor.getServiceManager().getConfig().getNameMapper().map((String) key);
        List<Object> mappedKeys = keys.stream()
                                        .map(k -> commandExecutor.getServiceManager().getConfig().getNameMapper().map((String) k))
                                        .collect(Collectors.toList());
        if (mode == Mode.READ_ONLY && commandExecutor.isEvalShaROSupported()) {
            RedisCommand cmd = new RedisCommand(returnType.getCommand(), "EVALSHA_RO");
            RFuture<R> f = commandExecutor.evalReadAsync(mappedKey, codec, cmd, shaDigest, mappedKeys, encode(Arrays.asList(values), codec).toArray());
            CompletableFuture<R> result = new CompletableFuture<>();
            f.whenComplete((r, e) -> {
                if (e != null && e.getMessage().startsWith("ERR unknown command")) {
                    commandExecutor.setEvalShaROSupported(false);
                    RFuture<R> s = evalShaAsync(mappedKey, mode, shaDigest, returnType, mappedKeys, values);
                    commandExecutor.transfer(s.toCompletableFuture(), result);
                    return;
                }
                commandExecutor.transfer(f.toCompletableFuture(), result);
            });
            return new CompletableFutureWrapper<>(result);
        }
        return commandExecutor.evalWriteAsync(mappedKey, codec, command, shaDigest, mappedKeys, encode(Arrays.asList(values), codec).toArray());
    }

    @Override
    public <R> RFuture<R> evalAsync(String key, Mode mode, String luaScript, ReturnType returnType, List<Object> keys,
            Object... values) {
        String mappedKey = commandExecutor.getServiceManager().getConfig().getNameMapper().map((String) key);
        List<Object> mappedKeys = keys.stream()
                .map(k -> commandExecutor.getServiceManager().getConfig().getNameMapper().map((String) k))
                .collect(Collectors.toList());
        if (mode == Mode.READ_ONLY) {
            return commandExecutor.evalReadAsync(mappedKey, codec, returnType.getCommand(), luaScript, mappedKeys, encode(Arrays.asList(values), codec).toArray());
        }
        return commandExecutor.evalWriteAsync(mappedKey, codec, returnType.getCommand(), luaScript, mappedKeys, encode(Arrays.asList(values), codec).toArray());
    }

    @Override
    public <R> R evalSha(String key, Mode mode, String shaDigest, ReturnType returnType, List<Object> keys,
            Object... values) {
        return commandExecutor.get(evalShaAsync(key, mode, shaDigest, returnType, keys, values));
    }

    @Override
    public <R> R eval(String key, Mode mode, String luaScript, ReturnType returnType, List<Object> keys,
            Object... values) {
        return commandExecutor.get(evalAsync(key, mode, luaScript, returnType, keys, values));
    }

}
