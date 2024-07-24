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

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.redisson.api.RScript.Mode;
import org.redisson.api.RScript.ReturnType;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * RxJava2 interface for Redis Script feature
 * 
 * @author Nikita Koksharov
 *
 */
public interface RScriptRx {

    /**
     * Flushes Lua script cache.
     * 
     * @return void
     */
    Completable scriptFlush();

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
    <R> Maybe<R> evalSha(Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values);

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
    <R> Maybe<R> evalSha(String key, Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values);

    /**
     * Executes Lua script stored in Redis scripts cache by SHA-1 digest
     * 
     * @param <R> - type of result
     * @param mode - execution mode
     * @param shaDigest - SHA-1 digest
     * @param returnType - return type
     * @return result object
     */
    <R> Maybe<R> evalSha(Mode mode, String shaDigest, ReturnType returnType);

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
    <R> Maybe<R> evalSha(Mode mode, String shaDigest, ReturnType returnType, Function<Collection<R>, R> resultMapper, Object... values);

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
    <R> Maybe<R> eval(Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values);

    /**
     * Executes Lua script
     * 
     * @param <R> - type of result
     * @param mode - execution mode
     * @param luaScript - lua script
     * @param returnType - return type
     * @return result object
     */
    <R> Maybe<R> eval(Mode mode, String luaScript, ReturnType returnType);
    
    /**
     * Executes Lua script
     * 
     * @param <R> - type of result
     * @param key - used to locate Redis node in Cluster which stores cached Lua script 
     * @param mode - execution mode
     * @param luaScript - lua script
     * @param returnType - return type
     * @param keys - keys available through KEYS param in script
     * @param values - values available through ARGV param in script
     * @return result object
     */
    <R> Maybe<R> eval(String key, Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values);

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
    <R> Maybe<R> eval(Mode mode, String luaScript, ReturnType returnType, Function<Collection<R>, R> resultMapper, Object... values);

    /**
     * Loads Lua script into Redis scripts cache and returns its SHA-1 digest
     * 
     * @param luaScript - lua script
     * @return SHA-1 digest
     */
    Single<String> scriptLoad(String luaScript);

    /**
     * Checks for presence Lua scripts in Redis script cache by SHA-1 digest.
     * 
     * @param shaDigests - collection of SHA-1 digests
     * @return list of booleans corresponding to collection SHA-1 digests
     */
    Single<List<Boolean>> scriptExists(String... shaDigests);

    /**
     * Kills currently executed Lua script
     * 
     * @return void
     */
    Completable scriptKill();

}
