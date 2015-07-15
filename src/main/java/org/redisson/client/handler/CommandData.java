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
package org.redisson.client.handler;

import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.client.protocol.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.decoder.MultiDecoder;

import io.netty.util.concurrent.Promise;

public class CommandData<T, R> {

    final Promise<R> promise;
    final RedisCommand<T> command;
    final Object[] params;
    final Codec codec;
    final AtomicBoolean sended = new AtomicBoolean();
    final MultiDecoder<Object> messageDecoder;

    public CommandData(Promise<R> promise, Codec codec, RedisCommand<T> command, Object[] params) {
        this(promise, null, codec, command, params);
    }

    public CommandData(Promise<R> promise, MultiDecoder<Object> messageDecoder, Codec codec, RedisCommand<T> command, Object[] params) {
        this.promise = promise;
        this.command = command;
        this.params = params;
        this.codec = codec;
        this.messageDecoder = messageDecoder;
    }

    public RedisCommand<T> getCommand() {
        return command;
    }

    public Object[] getParams() {
        return params;
    }

    public MultiDecoder<Object> getMessageDecoder() {
        return messageDecoder;
    }

    public Promise<R> getPromise() {
        return promise;
    }

    public AtomicBoolean getSended() {
        return sended;
    }

    public Codec getCodec() {
        return codec;
    }

}
