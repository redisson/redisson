/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.redisson.api.RFuture;
import org.redisson.api.RScript;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

import io.netty.buffer.ByteBuf;

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
        this.codec = commandExecutor.getConnectionManager().getCodec();
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
        return commandExecutor.writeAllAsync(StringCodec.INSTANCE, RedisCommands.SCRIPT_LOAD, new SlotCallback<String, String>() {
            volatile String result;
            @Override
            public void onSlotResult(String result) {
                this.result = result;
            }

            @Override
            public String onFinish() {
                return result;
            }
        }, luaScript);
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
    public <R> R eval(Mode mode, Codec codec, String luaScript, ReturnType returnType) {
        RedissonScript script = new RedissonScript(commandExecutor, codec);
        return script.eval(mode, luaScript, returnType);
    }

    @Override
    public <R> R eval(Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values) {
        return eval(null, mode, luaScript, returnType, keys, values);
    }

    @Override
    public <R> R eval(Mode mode, Codec codec, String luaScript, ReturnType returnType, List<Object> keys, Object... values) {
        RedissonScript script = new RedissonScript(commandExecutor, codec);
        return script.eval(mode, luaScript, returnType, keys, values);
    }

    @Override
    public <R> RFuture<R> evalAsync(Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values) {
        return evalAsync(null, mode, luaScript, returnType, keys, values);
    }

    @Override
    public <R> RFuture<R> evalAsync(Mode mode, Codec codec, String luaScript, ReturnType returnType, List<Object> keys, Object... values) {
        RedissonScript script = new RedissonScript(commandExecutor, codec);
        return script.evalAsync(mode, luaScript, returnType, keys, values);
    }

    @Override
    public <R> RFuture<R> evalAsync(String key, Mode mode, Codec codec, String luaScript, ReturnType returnType, List<Object> keys, Object... values) {
        RedissonScript script = new RedissonScript(commandExecutor, codec);
        return script.evalAsync(key, mode, luaScript, returnType, keys, values);
    }

    @Override
    public <R> R evalSha(Mode mode, String shaDigest, ReturnType returnType) {
        return evalSha(null, mode, shaDigest, returnType, Collections.emptyList());
    }

    @Override
    public <R> R evalSha(Mode mode, Codec codec, String shaDigest, ReturnType returnType) {
        return evalSha(mode, codec, shaDigest, returnType, Collections.emptyList());
    }

    @Override
    public <R> R evalSha(Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values) {
        return evalSha(null, mode, shaDigest, returnType, keys, values);
    }

    @Override
    public <R> R evalSha(Mode mode, Codec codec, String shaDigest, ReturnType returnType, List<Object> keys, Object... values) {
        return (R) commandExecutor.get(evalShaAsync(null, mode, codec, shaDigest, returnType, keys, values));
    }

    @Override
    public <R> RFuture<R> evalShaAsync(Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values) {
        return evalShaAsync(null, mode, codec, shaDigest, returnType, keys, values);
    }

    @Override
    public <R> RFuture<R> evalShaAsync(Mode mode, Codec codec, String shaDigest, ReturnType returnType, List<Object> keys, Object... values) {
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
        return commandExecutor.writeAllAsync(RedisCommands.SCRIPT_KILL);
    }

    public RFuture<Void> scriptKillAsync(String key) {
        return commandExecutor.writeAsync(key, RedisCommands.SCRIPT_KILL);
    }

    @Override
    public List<Boolean> scriptExists(String... shaDigests) {
        return commandExecutor.get(scriptExistsAsync(shaDigests));
    }

    @Override
    public RFuture<List<Boolean>> scriptExistsAsync(final String... shaDigests) {
         return commandExecutor.writeAllAsync(RedisCommands.SCRIPT_EXISTS, new SlotCallback<List<Boolean>, List<Boolean>>() {
            volatile List<Boolean> result = new ArrayList<Boolean>(shaDigests.length);
            @Override
            public synchronized void onSlotResult(List<Boolean> result) {
                for (int i = 0; i < result.size(); i++) {
                    if (this.result.size() == i) {
                        this.result.add(false);
                    }
                    this.result.set(i, this.result.get(i) | result.get(i));
                }
            }

            @Override
            public List<Boolean> onFinish() {
                return new ArrayList<Boolean>(result);
            }
        }, (Object[]) shaDigests);
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
        return commandExecutor.writeAllAsync(RedisCommands.SCRIPT_FLUSH);
    }

    public RFuture<Void> scriptFlushAsync(String key) {
        return commandExecutor.writeAsync(key, RedisCommands.SCRIPT_FLUSH);
    }

    @Override
    public <R> RFuture<R> evalShaAsync(Mode mode, String shaDigest, ReturnType returnType) {
        return evalShaAsync(null, mode, codec, shaDigest, returnType, Collections.emptyList());
    }

    @Override
    public <R> RFuture<R> evalShaAsync(Mode mode, Codec codec, String shaDigest, ReturnType returnType) {
        return evalShaAsync(null, mode, codec, shaDigest, returnType, Collections.emptyList());
    }

    @Override
    public <R> RFuture<R> evalAsync(Mode mode, String luaScript, ReturnType returnType) {
        return evalAsync(null, mode, codec, luaScript, returnType, Collections.emptyList());
    }

    @Override
    public <R> RFuture<R> evalAsync(Mode mode, Codec codec, String luaScript, ReturnType returnType) {
        return evalAsync(null, mode, codec, luaScript, returnType, Collections.emptyList());
    }

    private List<Object> encode(Collection<?> values, Codec codec) {
        List<Object> result = new ArrayList<Object>(values.size());
        for (Object object : values) {
            result.add(encode(object, codec));
        }
        return result;
    }
    
    private ByteBuf encode(Object value, Codec codec) {
        if (commandExecutor.isRedissonReferenceSupportEnabled()) {
            RedissonReference reference = commandExecutor.getObjectBuilder().toReference(value);
            if (reference != null) {
                value = reference;
            }
        }
        
        try {
            return codec.getValueEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public <R> RFuture<R> evalShaAsync(String key, Mode mode, String shaDigest, ReturnType returnType,
            List<Object> keys, Object... values) {
        RedisCommand command = new RedisCommand(returnType.getCommand(), "EVALSHA");
        if (mode == Mode.READ_ONLY) {
            return commandExecutor.evalReadAsync(key, codec, command, shaDigest, keys, encode(Arrays.asList(values), codec).toArray());
        }
        return commandExecutor.evalWriteAsync(key, codec, command, shaDigest, keys, encode(Arrays.asList(values), codec).toArray());
    }

    @Override
    public <R> RFuture<R> evalAsync(String key, Mode mode, String luaScript, ReturnType returnType, List<Object> keys,
            Object... values) {
        if (mode == Mode.READ_ONLY) {
            return commandExecutor.evalReadAsync(key, codec, returnType.getCommand(), luaScript, keys, encode(Arrays.asList(values), codec).toArray());
        }
        return commandExecutor.evalWriteAsync(key, codec, returnType.getCommand(), luaScript, keys, encode(Arrays.asList(values), codec).toArray());
    }

    @Override
    public <R> R evalSha(String key, Mode mode, String shaDigest, ReturnType returnType, List<Object> keys,
            Object... values) {
        return commandExecutor.get((RFuture<R>) evalShaAsync(key, mode, shaDigest, returnType, keys, values));
    }

    @Override
    public <R> R eval(String key, Mode mode, String luaScript, ReturnType returnType, List<Object> keys,
            Object... values) {
        return commandExecutor.get((RFuture<R>) evalAsync(key, mode, luaScript, returnType, keys, values));
    }

}
