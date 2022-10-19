/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.codec;

import org.redisson.client.codec.BaseCodec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class JsonCodecWrapper extends BaseCodec {

    private JsonCodec<?> innerCodec;

    public JsonCodecWrapper(JsonCodec<?> innerCodec) {
        this.innerCodec = innerCodec;
    }

    public JsonCodecWrapper(ClassLoader classLoader, JsonCodecWrapper codec) throws ReflectiveOperationException {
        this(copy(classLoader, codec.innerCodec));
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return innerCodec.getValueDecoder();
    }

    @Override
    public Encoder getValueEncoder() {
        return innerCodec.getValueEncoder();
    }
}
