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

public class RedisStrictCommand<T> extends RedisCommand<T> {

    public RedisStrictCommand(String name, MultiDecoder<T> replayMultiDecoder, int... encodeParamIndexes) {
        super(name, replayMultiDecoder, encodeParamIndexes);
    }

    public RedisStrictCommand(String name, int... encodeParamIndexes) {
        super(name, encodeParamIndexes);
    }

    public RedisStrictCommand(String name, Convertor<T> convertor, int ... encodeParamIndexes) {
        super(name, convertor, encodeParamIndexes);
    }

    public RedisStrictCommand(String name, String subName, Decoder<T> reponseDecoder,
            int... encodeParamIndexes) {
        super(name, subName, reponseDecoder, encodeParamIndexes);
    }

    public RedisStrictCommand(String name, String subName, int... encodeParamIndexes) {
        super(name, subName, encodeParamIndexes);
    }

    public RedisStrictCommand(String name, Decoder<T> reponseDecoder, int... encodeParamIndexes) {
        super(name, reponseDecoder, encodeParamIndexes);
    }

}
