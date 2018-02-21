/**
 * Copyright 2018 Nikita Koksharov
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
package org.redisson.reactive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.reactivestreams.Publisher;
import org.redisson.SlotCallback;
import org.redisson.api.RScript;
import org.redisson.api.RScriptReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandReactiveExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonScriptReactive implements RScriptReactive {

    private final CommandReactiveExecutor commandExecutor;

    public RedissonScriptReactive(CommandReactiveExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @Override
    public Publisher<String> scriptLoad(String luaScript) {
        return commandExecutor.writeAllReactive(RedisCommands.SCRIPT_LOAD, new SlotCallback<String, String>() {
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

    public Publisher<String> scriptLoad(String key, String luaScript) {
        return commandExecutor.writeReactive(key, RedisCommands.SCRIPT_LOAD, luaScript);
    }

    @Override
    public <R> Publisher<R> eval(RScript.Mode mode, String luaScript, RScript.ReturnType returnType, List<Object> keys, Object... values) {
        return eval(null, mode, commandExecutor.getConnectionManager().getCodec(), luaScript, returnType, keys, values);
    }

    @Override
    public <R> Publisher<R> eval(RScript.Mode mode, Codec codec, String luaScript, RScript.ReturnType returnType, List<Object> keys, Object... values) {
        return eval(null, mode, codec, luaScript, returnType, keys, values);
    }

    public <R> Publisher<R> eval(String key, RScript.Mode mode, Codec codec, String luaScript, RScript.ReturnType returnType, List<Object> keys, Object... values) {
        if (mode == RScript.Mode.READ_ONLY) {
            return commandExecutor.evalReadReactive(key, codec, returnType.getCommand(), luaScript, keys, values);
        }
        return commandExecutor.evalWriteReactive(key, codec, returnType.getCommand(), luaScript, keys, values);
    }

    @Override
    public <R> Publisher<R> evalSha(RScript.Mode mode, String shaDigest, RScript.ReturnType returnType, List<Object> keys, Object... values) {
        return evalSha(null, mode, commandExecutor.getConnectionManager().getCodec(), shaDigest, returnType, keys, values);
    }

    @Override
    public <R> Publisher<R> evalSha(RScript.Mode mode, Codec codec, String shaDigest, RScript.ReturnType returnType, List<Object> keys, Object... values) {
        return evalSha(null, mode, codec, shaDigest, returnType, keys, values);
    }

    public <R> Publisher<R> evalSha(String key, RScript.Mode mode, Codec codec, String shaDigest, RScript.ReturnType returnType, List<Object> keys, Object... values) {
        RedisCommand command = new RedisCommand(returnType.getCommand(), "EVALSHA");
        if (mode == RScript.Mode.READ_ONLY) {
            return commandExecutor.evalReadReactive(key, codec, command, shaDigest, keys, values);
        }
        return commandExecutor.evalWriteReactive(key, codec, command, shaDigest, keys, values);
    }

    @Override
    public Publisher<Void> scriptKill() {
        return commandExecutor.writeAllReactive(RedisCommands.SCRIPT_KILL);
    }

    @Override
    public Publisher<List<Boolean>> scriptExists(final String ... shaDigests) {
         return commandExecutor.writeAllReactive(RedisCommands.SCRIPT_EXISTS, new SlotCallback<List<Boolean>, List<Boolean>>() {
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
        }, (Object[])shaDigests);
    }

    public Publisher<List<Boolean>> scriptExists(String key, String ... shaDigests) {
        return commandExecutor.writeReactive(key, RedisCommands.SCRIPT_EXISTS, (Object[])shaDigests);
    }

    @Override
    public Publisher<Void> scriptFlush() {
        return commandExecutor.writeAllReactive(RedisCommands.SCRIPT_FLUSH);
    }

    @Override
    public <R> Publisher<R> evalSha(RScript.Mode mode, String shaDigest, RScript.ReturnType returnType) {
        return evalSha(null, mode, commandExecutor.getConnectionManager().getCodec(), shaDigest, returnType, Collections.emptyList());
    }

    @Override
    public <R> Publisher<R> evalSha(RScript.Mode mode, Codec codec, String shaDigest, RScript.ReturnType returnType) {
        return evalSha(null, mode, codec, shaDigest, returnType, Collections.emptyList());
    }

    @Override
    public <R> Publisher<R> eval(RScript.Mode mode, String luaScript, RScript.ReturnType returnType) {
        return eval(null, mode, commandExecutor.getConnectionManager().getCodec(), luaScript, returnType, Collections.emptyList());
    }

    @Override
    public <R> Publisher<R> eval(RScript.Mode mode, Codec codec, String luaScript, RScript.ReturnType returnType) {
        return eval(null, mode, codec, luaScript, returnType, Collections.emptyList());
    }

}
