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

import org.reactivestreams.Publisher;
import org.redisson.api.RScript.Mode;
import org.redisson.api.RScript.ReturnType;
import org.redisson.client.codec.Codec;

public interface RScriptReactive {

    Publisher<Void> scriptFlush();

    <R> Publisher<R> evalSha(Mode mode, String shaDigest, ReturnType returnType, List<Object> keys, Object... values);

    <R> Publisher<R> evalSha(Mode mode, Codec codec, String shaDigest, ReturnType returnType, List<Object> keys, Object... values);

    <R> Publisher<R> evalSha(Mode mode, String shaDigest, ReturnType returnType);

    <R> Publisher<R> evalSha(Mode mode, Codec codec, String shaDigest, ReturnType returnType);

    <R> Publisher<R> eval(Mode mode, String luaScript, ReturnType returnType, List<Object> keys, Object... values);

    <R> Publisher<R> eval(Mode mode, Codec codec, String luaScript, ReturnType returnType, List<Object> keys, Object... values);

    <R> Publisher<R> eval(Mode mode, String luaScript, ReturnType returnType);

    <R> Publisher<R> eval(Mode mode, Codec codec, String luaScript, ReturnType returnType);

    Publisher<String> scriptLoad(String luaScript);

    Publisher<List<Boolean>> scriptExists(String ... shaDigests);

    Publisher<Void> scriptKill();

}
