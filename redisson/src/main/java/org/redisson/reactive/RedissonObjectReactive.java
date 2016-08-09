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
package org.redisson.reactive;

import org.reactivestreams.Publisher;
import org.redisson.api.RObjectReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandReactiveExecutor;

import io.netty.util.concurrent.Future;
import reactor.rx.Stream;
import reactor.rx.Streams;

/**
 * Base Redisson object
 *
 * @author Nikita Koksharov
 *
 */
abstract class RedissonObjectReactive implements RObjectReactive {

    final CommandReactiveExecutor commandExecutor;
    private final String name;
    final Codec codec;

    public RedissonObjectReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        this.codec = codec;
        this.name = name;
        this.commandExecutor = commandExecutor;
    }

    public <R> Publisher<R> reactive(Future<R> future) {
        return commandExecutor.reactive(future);
    }

    public RedissonObjectReactive(CommandReactiveExecutor commandExecutor, String name) {
        this(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name);
    }

    protected <V> Stream<V> newSucceeded(V result) {
        return Streams.just(result);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Publisher<Void> rename(String newName) {
        return commandExecutor.writeReactive(getName(), RedisCommands.RENAME, getName(), newName);
    }

    @Override
    public Publisher<Void> migrate(String host, int port, int database) {
        return commandExecutor.writeReactive(getName(), RedisCommands.MIGRATE, host, port, getName(), database);
    }

    @Override
    public Publisher<Boolean> move(int database) {
        return commandExecutor.writeReactive(getName(), RedisCommands.MOVE, getName(), database);
    }

    @Override
    public Publisher<Boolean> renamenx(String newName) {
        return commandExecutor.writeReactive(getName(), RedisCommands.RENAMENX, getName(), newName);
    }

    @Override
    public Publisher<Boolean> delete() {
        return commandExecutor.writeReactive(getName(), RedisCommands.DEL_BOOL, getName());
    }

    @Override
    public Publisher<Boolean> isExists() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.EXISTS, getName());
    }

}
