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

import org.redisson.client.protocol.pubsub.MultiDecoder;

public class RedisCommand<R> {

    public enum ValueType {OBJECT, MAP_VALUE, MAP_KEY, MAP}

    private ValueType outParamType = ValueType.OBJECT;
    private ValueType inParamType = ValueType.OBJECT;
    private final int inParamIndex;

    private final String name;
    private final String subName;

    private Encoder paramsEncoder = new StringParamsEncoder();
    private MultiDecoder<R> replayMultiDecoder;
    private Decoder<R> replayDecoder;
    private Convertor<R> convertor = new EmptyConvertor<R>();

    public RedisCommand(String name) {
        this(name, (String)null);
    }

    public RedisCommand(String name, ValueType outParamType) {
        this(name, (String)null);
        this.outParamType = outParamType;
    }

    public RedisCommand(String name, String subName) {
        this(name, subName, null, null, -1);
    }

    public RedisCommand(String name, String subName, int objectParamIndex) {
        this(name, subName, null, null, objectParamIndex);
    }

    public RedisCommand(String name, int encodeParamIndex) {
        this(name, null, null, null, encodeParamIndex);
    }

    public RedisCommand(String name, int encodeParamIndex, ValueType inParamType, ValueType outParamType) {
        this(name, null, null, null, encodeParamIndex);
        this.inParamType = inParamType;
        this.outParamType = outParamType;
    }


    public RedisCommand(String name, Convertor<R> convertor, int encodeParamIndex, ValueType inParamType) {
        this(name, null, null, null, encodeParamIndex);
        this.convertor = convertor;
        this.inParamType = inParamType;
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
        this.inParamType = inParamType;
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
        this.inParamType = inParamType;
    }

    public RedisCommand(String name, MultiDecoder<R> replayMultiDecoder) {
        this(name, replayMultiDecoder, -1);
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

    public Encoder getParamsEncoder() {
        return paramsEncoder;
    }

    public ValueType getInParamType() {
        return inParamType;
    }

    public ValueType getOutParamType() {
        return outParamType;
    }

}
