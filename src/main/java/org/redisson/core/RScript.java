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
package org.redisson.core;

import io.netty.util.concurrent.Future;

import java.util.List;

public interface RScript {
    
    enum ReturnType {BOOLEAN, INTEGER, MULTI, STATUS, VALUE};

    List<Boolean> scriptExists(String ... shaDigests);
    
    Future<List<Boolean>> scriptExistsAsync(String ... shaDigests);
    
    String scriptFlush();
    
    Future<String> scriptFlushAsync();
    
    String scriptKill();
    
    Future<String> scriptKillAsync();
    
    String scriptLoad(String luaScript);
    
    Future<String> scriptLoadAsync(String luaScript);
    
    <R> R evalSha(String shaDigest, ReturnType returnType);
    
    <R> R evalSha(String shaDigest, ReturnType returnType, List<Object> keys, Object... values);
    
    <R> Future<R> evalShaAsync(String shaDigest, ReturnType returnType, List<Object> keys, Object... values);
    
    <R> Future<R> evalAsync(String luaScript, ReturnType returnType, List<Object> keys, Object... values);
    
    <R> R eval(String luaScript, ReturnType returnType);
    
    <R> R eval(String luaScript, ReturnType returnType, List<Object> keys, Object... values);
    
}
