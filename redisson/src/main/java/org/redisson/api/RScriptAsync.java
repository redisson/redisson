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
package org.redisson.api;

import java.util.List;

import org.redisson.api.RScript.Mode;
import org.redisson.api.RScript.ReturnType;
import org.redisson.client.codec.Codec;

/**
 * Async interface for Redis Script feature
 * 
 * @author Nikita Koksharov
 *
 */
public interface RScriptAsync {

    /**
     * Flushes Lua script cache.
     * 
     * @return void
     */
    RFuture<Void> scriptFlushAsync();

    /**
     * Executes Lua script stored in Redis scripts cache by SHA-1 digest
     * 
     * @param <R> - type of result
     * @param mode - execution mode
     * @param shaDigest - SHA-1 digest
     * @param returnType - return type
     * @param keys - keys available through KEYS param in script
     * @param values - values available through VALUES param in script
     * @return result object
     */
    <R> RFuture<R> evalShaAsync(Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values);
    
    /*
     * Use getScript(Codec) instead
     */
    @Deprecated
    <R> RFuture<R> evalShaAsync(Mode mode, Codec codec, String shaDigest, ReturnType returnType, List<Object> keys, Object... values);

    /**
     * Executes Lua script stored in Redis scripts cache by SHA-1 digest
     * 
     * @param <R> - type of result
     * @param key - used to locate Redis node in Cluster which stores cached Lua script 
     * @param mode - execution mode
     * @param shaDigest - SHA-1 digest
     * @param returnType - return type
     * @param keys - keys available through KEYS param in script
     * @param values - values available through VALUES param in script
     * @return result object
     */
    <R> RFuture<R> evalShaAsync(String key, Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values);
    
    /*
     * Use getScript(Codec) instead
     */
    @Deprecated
    <R> RFuture<R> evalShaAsync(String key, Mode mode, Codec codec, String shaDigest, ReturnType returnType, List<Object> keys, Object... values);

    /**
     * Executes Lua script stored in Redis scripts cache by SHA-1 digest
     * 
     * @param <R> - type of result
     * @param mode - execution mode
     * @param shaDigest - SHA-1 digest
     * @param returnType - return type
     * @return result object
     */
    <R> RFuture<R> evalShaAsync(Mode mode, String shaDigest, ReturnType returnType);
    
    /*
     * Use getScript(Codec) instead
     */
    @Deprecated
    <R> RFuture<R> evalShaAsync(Mode mode, Codec codec, String shaDigest, ReturnType returnType);

    /**
     * Executes Lua script
     * 
     * @param <R> - type of result
     * @param mode - execution mode
     * @param luaScript - lua script
     * @param returnType - return type
     * @param keys - keys available through KEYS param in script 
     * @param values - values available through VALUES param in script
     * @return result object
     */
    <R> RFuture<R> evalAsync(Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values);
    
    /*
     * Use getScript(Codec) instead
     */
    @Deprecated
    <R> RFuture<R> evalAsync(Mode mode, Codec codec, String luaScript, ReturnType returnType, List<Object> keys, Object... values);

    /*
     * Use getScript(Codec) instead
     */
    @Deprecated
    <R> RFuture<R> evalAsync(String key, Mode mode, Codec codec, String luaScript, ReturnType returnType, List<Object> keys, Object... values);

    /**
     * Executes Lua script
     * 
     * @param <R> - type of result
     * @param key - used to locate Redis node in Cluster which stores cached Lua script 
     * @param mode - execution mode
     * @param luaScript - lua script
     * @param returnType - return type
     * @param keys - keys available through KEYS param in script
     * @param values - values available through VALUES param in script
     * @return result object
     */
    <R> RFuture<R> evalAsync(String key, Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values);

    /**
     * Executes Lua script
     * 
     * @param <R> - type of result
     * @param mode - execution mode
     * @param luaScript - lua script
     * @param returnType - return type
     * @return result object
     */
    <R> RFuture<R> evalAsync(Mode mode, String luaScript, ReturnType returnType);

    /*
     * Use getScript(Codec) instead
     */
    @Deprecated
    <R> RFuture<R> evalAsync(Mode mode, Codec codec, String luaScript, ReturnType returnType);

    /**
     * Loads Lua script into Redis scripts cache and returns its SHA-1 digest
     * 
     * @param luaScript - lua script
     * @return SHA-1 digest
     */
    RFuture<String> scriptLoadAsync(String luaScript);

    /**
     * Loads Lua script into Redis scripts cache and returns its SHA-1 digest
     * 
     * @param key - used to locate Redis node in Cluster which stores cached Lua script
     * @param luaScript - lua script
     * @return SHA-1 digest
     */
    RFuture<String> scriptLoadAsync(String key, String luaScript);

    /**
     * Checks for presence Lua scripts in Redis script cache by SHA-1 digest.
     * 
     * @param shaDigests - collection of SHA-1 digests
     * @return list of booleans corresponding to collection SHA-1 digests
     */
    RFuture<List<Boolean>> scriptExistsAsync(String... shaDigests);

    /**
     * Checks for presence Lua scripts in Redis script cache by SHA-1 digest.
     * 
     * @param key - used to locate Redis node in Cluster which stores cached Lua script
     * @param shaDigests - collection of SHA-1 digests
     * @return list of booleans corresponding to collection SHA-1 digests
     */
    RFuture<List<Boolean>> scriptExistsAsync(String key, String... shaDigests);

    /**
     * Kills currently executed Lua script
     * 
     * @return void
     */
    RFuture<Void> scriptKillAsync();

}
