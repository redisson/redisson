/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * API for Redis Lua scripts execution.
 * 
 * @author Nikita Koksharov
 *
 */
public interface RScript extends RScriptAsync {

    enum Mode {
        /**
         * Execute script as read operation
         */
        READ_ONLY,

        /**
         * Execute script as write operation
         */
        READ_WRITE
    }

    enum ReturnType {
        BOOLEAN(RedisCommands.EVAL_BOOLEAN_SAFE),
        INTEGER(RedisCommands.EVAL_LONG),
        LIST(RedisCommands.EVAL_LIST),
        STRING(RedisCommands.EVAL_STRING),
        VALUE(RedisCommands.EVAL_OBJECT),
        MAPVALUE(RedisCommands.EVAL_MAP_VALUE),
        MAPVALUELIST(RedisCommands.EVAL_MAP_VALUE_LIST);

        private final RedisCommand<?> command;

        ReturnType(RedisCommand<?> command) {
            this.command = command;
        }

        public RedisCommand<?> getCommand() {
            return command;
        }

    };

    /**
     * Executes Lua script stored in Redis scripts cache by SHA-1 digest
     * 
     * @param <R> - type of result
     * @param mode - execution mode
     * @param shaDigest - SHA-1 digest
     * @param returnType - return type
     * @param keys - keys available through KEYS param in script
     * @param values - values available through ARGV param in script
     * @return result object
     */
    <R> R evalSha(Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values);

    /**
     * Executes a Lua script stored in Redis scripts cache by SHA-1 digest <code>shaDigest</code>.
     * The script is executed over all Redis master or slave nodes in cluster depending on <code>mode</code> value.
     * <code>resultMapper</code> function reduces all results from Redis nodes into one.
     *
     * @param mode - execution mode
     * @param shaDigest - SHA-1 digest
     * @param returnType - return type
     * @param resultMapper - function for reducing multiple results into one
     * @param values - values available through ARGV param in script
     * @return result object
     * @param <R> - type of result
     */
    <R> R evalSha(Mode mode, String shaDigest, ReturnType returnType, Function<Collection<R>, R> resultMapper, Object... values);

    /**
     * Executes Lua script stored in Redis scripts cache by SHA-1 digest
     * 
     * @param <R> - type of result
     * @param key - used to locate Redis node in Cluster which stores cached Lua script 
     * @param mode - execution mode
     * @param shaDigest - SHA-1 digest
     * @param returnType - return type
     * @param keys - keys available through KEYS param in script
     * @param values - values available through ARGV param in script
     * @return result object
     */
    <R> R evalSha(String key, Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values);
    
    /**
     * Executes Lua script stored in Redis scripts cache by SHA-1 digest
     * 
     * @param <R> - type of result
     * @param mode - execution mode
     * @param shaDigest - SHA-1 digest
     * @param returnType - return type
     * @return result object
     */
    <R> R evalSha(Mode mode, String shaDigest, ReturnType returnType);

    /**
     * Executes Lua script
     * 
     * @param key - used to locate Redis node in Cluster which stores cached Lua script
     * @param mode - execution mode
     * @param luaScript - lua script
     * @param returnType - return type
     * @param keys - keys available through KEYS param in script
     * @param values - values available through ARGV param in script
     * @return result object
     * @param <R> - type of result
     */
    <R> R eval(String key, Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values);

    /**
     * Executes a Lua script.
     * The script is executed over all Redis master or slave nodes in cluster depending on <code>mode</code> value.
     * <code>resultMapper</code> function reduces all results from Redis nodes into one.
     *
     * @param mode - execution mode
     * @param luaScript - lua script
     * @param returnType - return type
     * @param resultMapper - function for reducing multiple results into one
     * @param values - values available through ARGV param in script
     * @return result object
     * @param <R> - type of result
     */
    <R> R eval(Mode mode, String luaScript, ReturnType returnType, Function<Collection<R>, R> resultMapper, Object... values);

    /**
     * Executes Lua script
     * 
     * @param <R> - type of result
     * @param mode - execution mode
     * @param luaScript - lua script
     * @param returnType - return type
     * @param keys - keys available through KEYS param in script 
     * @param values - values available through ARGV param in script
     * @return result object
     */
    <R> R eval(Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values);

    /**
     * Executes Lua script
     * 
     * @param <R> - type of result
     * @param mode - execution mode
     * @param luaScript - lua script
     * @param returnType - return type
     * @return result object
     */
    <R> R eval(Mode mode, String luaScript, ReturnType returnType);

    /**
     * Loads Lua script into Redis scripts cache and returns its SHA-1 digest
     * 
     * @param luaScript - lua script
     * @return SHA-1 digest
     */
    String scriptLoad(String luaScript);

    /**
     * Checks for presence Lua scripts in Redis script cache by SHA-1 digest.
     * 
     * @param shaDigests - collection of SHA-1 digests
     * @return list of booleans corresponding to collection SHA-1 digests
     */
    List<Boolean> scriptExists(String... shaDigests);

    /**
     * Kills currently executed Lua script
     * 
     */
    void scriptKill();

    /**
     * Flushes Lua script cache.
     * 
     */
    void scriptFlush();

}
