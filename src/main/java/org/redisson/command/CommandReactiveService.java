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
package org.redisson.command;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;

import org.reactivestreams.Publisher;
import org.redisson.SlotCallback;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.reactive.NettyFuturePublisher;

import io.netty.util.concurrent.Future;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class CommandReactiveService extends CommandAsyncService implements CommandReactiveExecutor {

    public CommandReactiveService(ConnectionManager connectionManager) {
        super(connectionManager);
    }

    @Override
    public <T, R> Publisher<R> evalWriteAllReactive(RedisCommand<T> command, SlotCallback<T, R> callback, String script, List<Object> keys, Object ... params) {
        Future<R> f = evalWriteAllAsync(command, callback, script, keys, params);
        return new NettyFuturePublisher<R>(f);
    }

    public <R> Publisher<R> reactive(Future<R> future) {
        return new NettyFuturePublisher<R>(future);
    }

    @Override
    public <T, R> Publisher<Collection<R>> readAllReactive(RedisCommand<T> command, Object ... params) {
        Future<Collection<R>> f = readAllAsync(command, params);
        return new NettyFuturePublisher<Collection<R>>(f);
    }

    @Override
    public <T, R> Publisher<R> readRandomReactive(RedisCommand<T> command, Object ... params) {
        Future<R> f = readRandomAsync(command, params);
        return new NettyFuturePublisher<R>(f);
    }

    @Override
    public <T, R> Publisher<R> readReactive(InetSocketAddress client, String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> f = readAsync(client, key, codec, command, params);
        return new NettyFuturePublisher<R>(f);
    }

    @Override
    public <T, R> Publisher<R> writeReactive(String key, RedisCommand<T> command, Object ... params) {
        return writeReactive(key, connectionManager.getCodec(), command, params);
    }

    @Override
    public <T, R> Publisher<R> writeReactive(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> f = writeAsync(key, codec, command, params);
        return new NettyFuturePublisher<R>(f);
    }

    @Override
    public <T, R> Publisher<R> writeReactive(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> f = writeAsync(entry, codec, command, params);
        return new NettyFuturePublisher<R>(f);
    }

    @Override
    public <T, R> Publisher<R> readReactive(String key, RedisCommand<T> command, Object ... params) {
        return readReactive(key, connectionManager.getCodec(), command, params);
    }

    @Override
    public <T, R> Publisher<R> readReactive(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> f = readAsync(key, codec, command, params);
        return new NettyFuturePublisher<R>(f);
    }

    @Override
    public <T, R> Publisher<R> evalReadReactive(String key, Codec codec, RedisCommand<T> evalCommandType,
            String script, List<Object> keys, Object... params) {
        Future<R> f = evalReadAsync(key, codec, evalCommandType, script, keys, params);
        return new NettyFuturePublisher<R>(f);
    }

    @Override
    public <T, R> Publisher<R> evalReadReactive(InetSocketAddress client, String key, Codec codec, RedisCommand<T> evalCommandType,
            String script, List<Object> keys, Object ... params) {
        Future<R> f = evalReadAsync(client, key, codec, evalCommandType, script, keys, params);
        return new NettyFuturePublisher<R>(f);
    }


    @Override
    public <T, R> Publisher<R> evalWriteReactive(String key, Codec codec, RedisCommand<T> evalCommandType,
            String script, List<Object> keys, Object... params) {
        Future<R> f = evalWriteAsync(key, codec, evalCommandType, script, keys, params);
        return new NettyFuturePublisher<R>(f);
    }

    @Override
    public <T> Publisher<Void> writeAllReactive(RedisCommand<T> command, Object ... params) {
        Future<Void> f = writeAllAsync(command, params);
        return new NettyFuturePublisher<Void>(f);
    }

    @Override
    public <R, T> Publisher<R> writeAllReactive(RedisCommand<T> command, SlotCallback<T, R> callback, Object ... params) {
        Future<R> f = writeAllAsync(command, callback, params);
        return new NettyFuturePublisher<R>(f);
    }


}
