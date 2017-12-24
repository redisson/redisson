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

import java.io.IOException;
import java.util.Collection;

import org.reactivestreams.Publisher;
import org.redisson.RedissonReference;
import org.redisson.api.RFuture;
import org.redisson.api.RObjectReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandReactiveExecutor;
import org.redisson.misc.RedissonObjectFactory;

import io.netty.buffer.ByteBuf;
import reactor.fn.Supplier;
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

    public <R> Publisher<R> reactive(Supplier<RFuture<R>> supplier) {
        return commandExecutor.reactive(supplier);
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
    public Codec getCodec() {
        return codec;
    }
    
    protected void encode(Collection<Object> params, Collection<?> values) {
        for (Object object : values) {
            params.add(encode(object));
        }
    }
    
    protected ByteBuf encode(Object value) {
        if (commandExecutor.isRedissonReferenceSupportEnabled()) {
            RedissonReference reference = RedissonObjectFactory.toReference(commandExecutor.getConnectionManager().getCfg(), value);
            if (reference != null) {
                value = reference;
            }
        }
        
        try {
            return codec.getValueEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    protected ByteBuf encodeMapKey(Object value) {
        if (commandExecutor.isRedissonReferenceSupportEnabled()) {
            RedissonReference reference = RedissonObjectFactory.toReference(commandExecutor.getConnectionManager().getCfg(), value);
            if (reference != null) {
                value = reference;
            }
        }
        
        try {
            return codec.getMapKeyEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected ByteBuf encodeMapValue(Object value) {
        if (commandExecutor.isRedissonReferenceSupportEnabled()) {
            RedissonReference reference = RedissonObjectFactory.toReference(commandExecutor.getConnectionManager().getCfg(), value);
            if (reference != null) {
                value = reference;
            }
        }

        try {
            return codec.getMapValueEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
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
