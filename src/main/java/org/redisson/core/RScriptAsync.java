/**
 * Copyright 2016 Nikita Koksharov
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

import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.core.RScript.Mode;
import org.redisson.core.RScript.ReturnType;

import io.netty.util.concurrent.Future;

public interface RScriptAsync {

    Future<Void> scriptFlushAsync();

    <R> Future<R> evalShaAsync(Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values);

    <R> Future<R> evalShaAsync(Mode mode, Codec codec, String shaDigest, ReturnType returnType, List<Object> keys, Object... values);

    <R> Future<R> evalShaAsync(Mode mode, String shaDigest, ReturnType returnType);

    <R> Future<R> evalShaAsync(Mode mode, Codec codec, String shaDigest, ReturnType returnType);

    <R> Future<R> evalAsync(Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values);

    <R> Future<R> evalAsync(Mode mode, Codec codec, String luaScript, ReturnType returnType, List<Object> keys, Object... values);
    
    <R> Future<R> evalAsync(String key, Mode mode, Codec codec, String luaScript, ReturnType returnType, List<Object> keys, Object... values);

    <R> Future<R> evalAsync(Mode mode, String luaScript, ReturnType returnType);

    <R> Future<R> evalAsync(Mode mode, Codec codec, String luaScript, ReturnType returnType);

    Future<String> scriptLoadAsync(String luaScript);

    Future<List<Boolean>> scriptExistsAsync(String ... shaDigests);

    Future<Void> scriptKillAsync();

}
