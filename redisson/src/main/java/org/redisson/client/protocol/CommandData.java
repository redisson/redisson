/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
package org.redisson.client.protocol;

import java.util.Collections;
import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.misc.LogHelper;
import org.redisson.misc.RPromise;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> input type
 * @param <R> output type
 */
public class CommandData<T, R> implements QueueCommand {

    final RPromise<R> promise;
    final RedisCommand<T> command;
    final Object[] params;
    final Codec codec;
    final MultiDecoder<Object> messageDecoder;

    public CommandData(RPromise<R> promise, Codec codec, RedisCommand<T> command, Object[] params) {
        this(promise, null, codec, command, params);
    }

    public CommandData(RPromise<R> promise, MultiDecoder<Object> messageDecoder, Codec codec, RedisCommand<T> command, Object[] params) {
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

    public RPromise<R> getPromise() {
        return promise;
    }
    
    public Throwable cause() {
        return promise.cause();
    }

    public boolean isSuccess() {
        return promise.isSuccess();
    }

    public boolean tryFailure(Throwable cause) {
        return promise.tryFailure(cause);
    }

    public Codec getCodec() {
        return codec;
    }

    @Override
    public String toString() {
        return "CommandData [promise=" + promise + ", command=" + command + ", params="
                + LogHelper.toString(params) + ", codec=" + codec + "]";
    }

    @Override
    public List<CommandData<Object, Object>> getPubSubOperations() {
        if (RedisCommands.PUBSUB_COMMANDS.contains(getCommand().getName())) {
            return Collections.singletonList((CommandData<Object, Object>) this);
        }
        return Collections.emptyList();
    }
    
    public boolean isBlockingCommand() {
        return RedisCommands.BLOCKING_COMMAND_NAMES.contains(command.getName()) 
                || RedisCommands.BLOCKING_COMMANDS.contains(command);
    }

    @Override
    public boolean isExecuted() {
        return promise.isDone();
    }

}
