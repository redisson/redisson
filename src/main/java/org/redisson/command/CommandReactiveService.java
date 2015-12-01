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
package org.redisson.command;

import java.net.InetSocketAddress;
import java.util.List;

import org.reactivestreams.Publisher;
import org.redisson.NettyFuturePublisher;
import org.redisson.SlotCallback;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.ConnectionManager;

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
    public <T, R> Publisher<R> readObservable(InetSocketAddress client, String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> f = readAsync(client, key, codec, command, params);
        return new NettyFuturePublisher<R>(f);
    }

    @Override
    public <T, R> Publisher<R> writeObservable(String key, RedisCommand<T> command, Object ... params) {
        return writeObservable(key, connectionManager.getCodec(), command, params);
    }

    @Override
    public <T, R> Publisher<R> writeObservable(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> f = writeAsync(key, codec, command, params);
        return new NettyFuturePublisher<R>(f);
    }

    @Override
    public <T, R> Publisher<R> readObservable(String key, RedisCommand<T> command, Object ... params) {
        return readObservable(key, connectionManager.getCodec(), command, params);
    }

    @Override
    public <T, R> Publisher<R> readObservable(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> f = readAsync(key, codec, command, params);
        return new NettyFuturePublisher<R>(f);
    }

    @Override
    public <T, R> Publisher<R> evalReadObservable(String key, Codec codec, RedisCommand<T> evalCommandType,
            String script, List<Object> keys, Object... params) {
        Future<R> f = evalReadAsync(key, codec, evalCommandType, script, keys, params);
        return new NettyFuturePublisher<R>(f);
    }

    @Override
    public <T, R> Publisher<R> evalWriteObservable(String key, Codec codec, RedisCommand<T> evalCommandType,
            String script, List<Object> keys, Object... params) {
        Future<R> f = evalWriteAsync(key, codec, evalCommandType, script, keys, params);
        return new NettyFuturePublisher<R>(f);
    }

    @Override
    public <T> Publisher<Void> writeAllObservable(RedisCommand<T> command, Object ... params) {
        Future<Void> f = writeAllAsync(command, params);
        return new NettyFuturePublisher<Void>(f);
    }

    @Override
    public <R, T> Publisher<R> writeAllObservable(RedisCommand<T> command, SlotCallback<T, R> callback, Object ... params) {
        Future<R> f = writeAllAsync(command, callback, params);
        return new NettyFuturePublisher<R>(f);
    }


}
