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
package org.redisson.api;

import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;

public interface RScript extends RScriptAsync {

    enum Mode {READ_ONLY, READ_WRITE}

    enum ReturnType {
        BOOLEAN(RedisCommands.EVAL_BOOLEAN_SAFE),
        INTEGER(RedisCommands.EVAL_LONG),
        MULTI(RedisCommands.EVAL_LIST),
        STATUS(RedisCommands.EVAL_STRING),
        VALUE(RedisCommands.EVAL_OBJECT),
        MAPVALUE(RedisCommands.EVAL_MAP_VALUE),
        MAPVALUELIST(RedisCommands.EVAL_MAP_VALUE_LIST);

        RedisCommand<?> command;

        ReturnType(RedisCommand<?> command) {
            this.command = command;
        }

        public RedisCommand<?> getCommand() {
            return command;
        }

    };

    <R> R evalSha(Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values);

    <R> R evalSha(Mode mode, Codec codec, String shaDigest, ReturnType returnType, List<Object> keys, Object... values);

    <R> R evalSha(Mode mode, String shaDigest, ReturnType returnType);

    <R> R evalSha(Mode mode, Codec codec, String shaDigest, ReturnType returnType);

    <R> R eval(Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values);

    <R> R eval(Mode mode, Codec codec, String luaScript, ReturnType returnType, List<Object> keys, Object... values);

    <R> R eval(Mode mode, String luaScript, ReturnType returnType);

    <R> R eval(Mode mode, Codec codec, String luaScript, ReturnType returnType);

    String scriptLoad(String luaScript);

    List<Boolean> scriptExists(String ... shaDigests);

    void scriptKill();

    void scriptFlush();

}
