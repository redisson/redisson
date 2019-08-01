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

    public enum ValueType {OBJECT, MAP_VALUE, MAP_KEY, MAP}

    private ValueType outParamType = ValueType.OBJECT;

    private final String name;
    private final String subName;

    private MultiDecoder<R> replayMultiDecoder;
    private Decoder<R> replayDecoder;
    Convertor<R> convertor = new EmptyConvertor<R>();

    /**
     * Copy command and change name
     *
     * @param command - source command
     * @param name - new command name
     */
    public RedisCommand(RedisCommand<R> command, String name) {
        this.outParamType = command.outParamType;
        this.name = name;
        this.subName = command.subName;
        this.replayMultiDecoder = command.replayMultiDecoder;
        this.replayDecoder = command.replayDecoder;
        this.convertor = command.convertor;
    }
    
    public RedisCommand(RedisCommand<R> command, String name, Convertor<R> convertor) {
        this.outParamType = command.outParamType;
        this.name = name;
        this.subName = command.subName;
        this.replayMultiDecoder = command.replayMultiDecoder;
        this.replayDecoder = command.replayDecoder;
        this.convertor = convertor;
    }

    public RedisCommand(String name) {
        this(name, (String) null);
    }

    public RedisCommand(String name, ValueType outParamType) {
        this(name, (String) null);
        this.outParamType = outParamType;
    }

    public RedisCommand(String name, ValueType outParamType, Convertor<R> convertor) {
        this(name, (String) null);
        this.outParamType = outParamType;
        this.convertor = convertor;
    }
    
    public RedisCommand(String name, String subName) {
        this(name, subName, null, null);
    }

    public RedisCommand(String name, String subName, Convertor<R> convertor) {
        this(name, subName, null, null);
        this.convertor = convertor;
    }

    public RedisCommand(String name, Convertor<R> convertor) {
        this(name, null, null, null);
        this.convertor = convertor;
    }

    public RedisCommand(String name, Decoder<R> reponseDecoder) {
        this(name, null, null, reponseDecoder);
    }

    public RedisCommand(String name, String subName, MultiDecoder<R> replayMultiDecoder) {
        this(name, subName, replayMultiDecoder, null);
    }

    public RedisCommand(String name, MultiDecoder<R> replayMultiDecoder, ValueType outParamType) {
        this(name, null, replayMultiDecoder);
        this.outParamType = outParamType;
    }

    public RedisCommand(String name, MultiDecoder<R> replayMultiDecoder) {
        this(name, null, replayMultiDecoder);
    }

    public RedisCommand(String name, MultiDecoder<R> replayMultiDecoder, Convertor<R> convertor) {
        this(name, replayMultiDecoder);
        this.convertor = convertor;
    }

    RedisCommand(String name, String subName, MultiDecoder<R> replayMultiDecoder, Decoder<R> reponseDecoder) {
        super();
        this.name = name;
        this.subName = subName;
        this.replayMultiDecoder = replayMultiDecoder;
        this.replayDecoder = reponseDecoder;
    }

    public String getSubName() {
        return subName;
    }

    public String getName() {
        return name;
    }

    public Decoder<R> getReplayDecoder() {
        return replayDecoder;
    }

    public MultiDecoder<R> getReplayMultiDecoder() {
        return replayMultiDecoder;
    }

    public Convertor<R> getConvertor() {
        return convertor;
    }

    public ValueType getOutParamType() {
        return outParamType;
    }

    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("(").append(name);
        if (subName != null) {
            str.append(" ").append(subName);
        }
        str.append(")");
        return str.toString();
    }

}
