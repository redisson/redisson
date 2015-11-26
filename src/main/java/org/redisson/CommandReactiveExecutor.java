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
package org.redisson;

import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.ConnectionManager;

import rx.Single;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface CommandReactiveExecutor {

    ConnectionManager getConnectionManager();

    <T, R> Single<R> evalWriteObservable(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params);

    <T, R> Single<R> evalReadObservable(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params);

    <T, R> Single<R> writeObservable(String key, RedisCommand<T> command, Object ... params);

    <T, R> Single<R> writeObservable(String key, Codec codec, RedisCommand<T> command, Object ... params);

    <T, R> Single<R> readObservable(String key, RedisCommand<T> command, Object ... params);

    <T, R> Single<R> readObservable(String key, Codec codec, RedisCommand<T> command, Object ... params);

}
