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
package org.redisson.client.protocol;

import java.util.Arrays;
import java.util.List;

import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.client.protocol.convertor.EmptyConvertor;
import org.redisson.client.protocol.decoder.MultiDecoder;

public class RedisCommand<R> {

    public enum ValueType {OBJECT, OBJECTS, MAP_VALUE, MAP_KEY, MAP}

    private ValueType outParamType = ValueType.OBJECT;
    private List<ValueType> inParamType = Arrays.asList(ValueType.OBJECT);
    private final int inParamIndex;

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
        this.inParamType = command.inParamType;
        this.inParamIndex = command.inParamIndex;
        this.name = name;
        this.subName = command.subName;
        this.replayMultiDecoder = command.replayMultiDecoder;
        this.replayDecoder = command.replayDecoder;
        this.convertor = command.convertor;
    }

    public RedisCommand(String name) {
        this(name, (String)null);
    }

    public RedisCommand(String name, ValueType outParamType) {
        this(name, (String)null);
        this.outParamType = outParamType;
    }

    public RedisCommand(String name, int objectParamIndex, ValueType inParamType) {
        this(name, null, null, null, objectParamIndex);
        this.inParamType = Arrays.asList(inParamType);
    }

    public RedisCommand(String name, ValueType inParamType, ValueType outParamType) {
        this(name, (String)null);
        this.inParamType = Arrays.asList(inParamType);
        this.outParamType = outParamType;
    }

    public RedisCommand(String name, String subName) {
        this(name, subName, null, null, -1);
    }

    public RedisCommand(String name, String subName, Convertor<R> convertor) {
        this(name, subName, null, null, -1);
        this.convertor = convertor;
    }

    public RedisCommand(String name, String subName, int objectParamIndex) {
        this(name, subName, null, null, objectParamIndex);
    }

    public RedisCommand(String name, int encodeParamIndex) {
        this(name, null, null, null, encodeParamIndex);
    }

    public RedisCommand(String name, int encodeParamIndex, ValueType inParamType, ValueType outParamType) {
        this(name, null, null, null, encodeParamIndex);
        this.inParamType = Arrays.asList(inParamType);
        this.outParamType = outParamType;
    }

    public RedisCommand(String name, int encodeParamIndex, List<ValueType> inParamType, ValueType outParamType) {
        this(name, null, null, null, encodeParamIndex);
        this.inParamType = inParamType;
        this.outParamType = outParamType;
    }

    public RedisCommand(String name, Decoder<R> reponseDecoder, int encodeParamIndex, List<ValueType> inParamType, ValueType outParamType) {
        this(name, null, null, reponseDecoder, encodeParamIndex);
        this.inParamType = inParamType;
        this.outParamType = outParamType;
    }

    public RedisCommand(String name, Decoder<R> reponseDecoder, int encodeParamIndex, List<ValueType> inParamType) {
        this(name, null, null, reponseDecoder, encodeParamIndex);
        this.inParamType = inParamType;
    }

    public RedisCommand(String name, Convertor<R> convertor, int encodeParamIndex, ValueType inParamType) {
        this(name, null, null, null, encodeParamIndex);
        this.convertor = convertor;
        this.inParamType = Arrays.asList(inParamType);
    }

    public RedisCommand(String name, Convertor<R> convertor, int encodeParamIndex, List<ValueType> inParamTypes) {
        this(name, null, null, null, encodeParamIndex);
        this.convertor = convertor;
        this.inParamType = inParamTypes;
    }

    public RedisCommand(String name, Convertor<R> convertor) {
        this(name, convertor, -1);
    }

    public RedisCommand(String name, Convertor<R> convertor, int encodeParamIndex) {
        this(name, null, null, null, encodeParamIndex);
        this.convertor = convertor;
    }

    public RedisCommand(String name, Decoder<R> reponseDecoder) {
        this(name, null, null, reponseDecoder, -1);
    }

    public RedisCommand(String name, Decoder<R> reponseDecoder, int objectParamIndex, ValueType inParamType) {
        this(name, null, null, reponseDecoder, objectParamIndex);
        this.inParamType = Arrays.asList(inParamType);
    }

    public RedisCommand(String name, Decoder<R> reponseDecoder, int objectParamIndex) {
        this(name, null, null, reponseDecoder, objectParamIndex);
    }

    public RedisCommand(String name, MultiDecoder<R> replayMultiDecoder, ValueType outParamType) {
        this(name, replayMultiDecoder, -1);
        this.outParamType = outParamType;
    }

    public RedisCommand(String name, MultiDecoder<R> replayMultiDecoder, int objectParamIndex, ValueType inParamType, ValueType outParamType) {
        this(name, replayMultiDecoder, objectParamIndex);
        this.outParamType = outParamType;
        this.inParamType = Arrays.asList(inParamType);
    }

    public RedisCommand(String name, MultiDecoder<R> replayMultiDecoder) {
        this(name, replayMultiDecoder, -1);
    }

    public RedisCommand(String name, MultiDecoder<R> replayMultiDecoder, Convertor<R> convertor) {
        this(name, replayMultiDecoder, -1);
        this.convertor = convertor;
    }


    public RedisCommand(String name, MultiDecoder<R> replayMultiDecoder, int objectParamIndex) {
        this(name, null, replayMultiDecoder, null, objectParamIndex);
    }

    public RedisCommand(String name, String subName, MultiDecoder<R> replayMultiDecoder,
            int objectParamIndex) {
        this(name, subName, replayMultiDecoder, null, objectParamIndex);
    }

    public RedisCommand(String name, String subName, MultiDecoder<R> replayMultiDecoder, Decoder<R> reponseDecoder, int objectParamIndex) {
        super();
        this.name = name;
        this.subName = subName;
        this.replayMultiDecoder = replayMultiDecoder;
        this.replayDecoder = reponseDecoder;
        this.inParamIndex = objectParamIndex;
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

    public int getInParamIndex() {
        return inParamIndex;
    }

    public MultiDecoder<R> getReplayMultiDecoder() {
        return replayMultiDecoder;
    }

    public Convertor<R> getConvertor() {
        return convertor;
    }

    public List<ValueType> getInParamType() {
        return inParamType;
    }

    public ValueType getOutParamType() {
        return outParamType;
    }

    @Override
    public String toString() {
        return "RedisCommand [name=" + name + ", subName=" + subName + "]";
    }

}
