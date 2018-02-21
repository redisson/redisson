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
package org.redisson.command;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.SlotCallback;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.MasterSlaveEntry;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface CommandReactiveExecutor extends CommandAsyncExecutor {

    <R> Publisher<R> reactive(Supplier<RFuture<R>> supplier);

    <T, R> Publisher<R> evalWriteAllReactive(RedisCommand<T> command, SlotCallback<T, R> callback, String script, List<Object> keys, Object ... params);

    <T, R> Publisher<Collection<R>> readAllReactive(RedisCommand<T> command, Object ... params);

    <T, R> Publisher<R> readRandomReactive(RedisCommand<T> command, Object ... params);

    <T, R> Publisher<R> writeReactive(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object ... params);

    <T> Publisher<Void> writeAllReactive(RedisCommand<T> command, Object ... params);

    <R, T> Publisher<R> writeAllReactive(RedisCommand<T> command, SlotCallback<T, R> callback, Object ... params);

    <T, R> Publisher<R> readReactive(RedisClient client, String name, Codec codec, RedisCommand<T> command, Object ... params);

    <T, R> Publisher<R> evalWriteReactive(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params);

    <T, R> Publisher<R> evalReadReactive(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params);

    <T, R> Publisher<R> writeReactive(String key, RedisCommand<T> command, Object ... params);

    <T, R> Publisher<R> writeReactive(String key, Codec codec, RedisCommand<T> command, Object ... params);

    <T, R> Publisher<R> readReactive(String key, RedisCommand<T> command, Object ... params);

    <T, R> Publisher<R> readReactive(String key, Codec codec, RedisCommand<T> command, Object ... params);

}
