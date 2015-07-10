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

    private final String name;
    private final String subName;
    private final int[] objectParamIndexes;

    private Encoder paramsEncoder = new StringParamsEncoder();
    private MultiDecoder<R> replayMultiDecoder;
    private Decoder<R> replayDecoder;
    private Convertor<R> convertor = new EmptyConvertor<R>();

    public RedisCommand(String name, String subName, int ... encodeParamIndexes) {
        this(name, subName, null, null, encodeParamIndexes);
    }

    public RedisCommand(String name, int ... encodeParamIndexes) {
        this(name, null, null, null, encodeParamIndexes);
    }

    public RedisCommand(String name, Convertor<R> convertor, int ... encodeParamIndexes) {
        this.name = name;
        this.subName = null;
        this.objectParamIndexes = encodeParamIndexes;
        this.convertor = convertor;
    }

    public RedisCommand(String name, Decoder<R> reponseDecoder, int ... encodeParamIndexes) {
        this(name, null, null, reponseDecoder, encodeParamIndexes);
    }

    public RedisCommand(String name, MultiDecoder<R> replayMultiDecoder, int ... encodeParamIndexes) {
        this.name = name;
        this.subName = null;
        this.objectParamIndexes = encodeParamIndexes;
        this.replayMultiDecoder = replayMultiDecoder;
    }

    public RedisCommand(String name, String subName, MultiDecoder<R> replayMultiDecoder,
            int... encodeParamIndexes) {
        this(name, subName, replayMultiDecoder, null, encodeParamIndexes);
    }

    public RedisCommand(String name, String subName, MultiDecoder<R> replayMultiDecoder, Decoder<R> reponseDecoder, int ... encodeParamIndexes) {
        super();
        this.name = name;
        this.subName = subName;
        this.replayMultiDecoder = replayMultiDecoder;
        this.replayDecoder = reponseDecoder;
        this.objectParamIndexes = encodeParamIndexes;
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

    public int[] getObjectParamIndexes() {
        return objectParamIndexes;
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

}
