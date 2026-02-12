/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.client.protocol.convertor.EmptyConvertor;
import org.redisson.client.protocol.decoder.MultiDecoder;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <R> return type
 */
public class RedisCommand<R> {

    private final String name;
    private final String subName;
    private String script;

    private final MultiDecoder<R> replayMultiDecoder;
    Convertor<R> convertor = new EmptyConvertor<R>();

    /**
     * Copy command and change name
     *
     * @param command - source command
     * @param name - new command name
     */
    public RedisCommand(RedisCommand<R> command, String name) {
        this.name = name;
        this.subName = command.subName;
        this.replayMultiDecoder = command.replayMultiDecoder;
        this.convertor = command.convertor;
    }

    public RedisCommand(RedisCommand<R> command, String name, String script) {
        this.name = name;
        this.subName = command.subName;
        this.replayMultiDecoder = command.replayMultiDecoder;
        this.convertor = command.convertor;
        this.script = script;
    }

    public RedisCommand(RedisCommand<R> command, String name, Convertor<R> convertor) {
        this.name = name;
        this.subName = command.subName;
        this.replayMultiDecoder = command.replayMultiDecoder;
        this.convertor = convertor;
    }

    public RedisCommand(String name) {
        this(name, (String) null);
    }

    public RedisCommand(String name, String subName) {
        this(name, subName, (MultiDecoder<R>) null);
    }

    public RedisCommand(String name, String subName, Convertor<R> convertor) {
        this(name, subName);
        this.convertor = convertor;
    }

    public RedisCommand(String name, Convertor<R> convertor) {
        this(name, null, (MultiDecoder<R>) null);
        this.convertor = convertor;
    }

    public RedisCommand(String name, MultiDecoder<R> replayMultiDecoder) {
        this(name, null, replayMultiDecoder);
    }

    public RedisCommand(String name, MultiDecoder<R> replayMultiDecoder, Convertor<R> convertor) {
        this(name, replayMultiDecoder);
        this.convertor = convertor;
    }

    public RedisCommand(String name, String subName, MultiDecoder<R> replayMultiDecoder) {
        super();
        this.name = name;
        this.subName = subName;
        if (replayMultiDecoder != null) {
            this.replayMultiDecoder = replayMultiDecoder;
        } else {
            this.replayMultiDecoder = (parts, state) -> (R) parts;
        }
    }

    public String getSubName() {
        return subName;
    }

    public String getName() {
        return name;
    }

    public MultiDecoder<R> getReplayMultiDecoder() {
        return replayMultiDecoder;
    }

    public Convertor<R> getConvertor() {
        return convertor;
    }

    public boolean isNoRetry() {
        return RedisCommands.NO_RETRY.contains(getName())
                || RedisCommands.NO_RETRY_COMMANDS.contains(this);
    }

    public boolean isBlockingCommand() {
        return RedisCommands.BLOCKING_COMMAND_NAMES.contains(getName())
                || RedisCommands.BLOCKING_COMMANDS.contains(this);
    }

    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("(").append(name);
        if (subName != null) {
            str.append(" ").append(subName);
        }
        if (script != null) {
            str.append(", cached script: ").append(script);
        }
        str.append(")");
        return str.toString();
    }

}
