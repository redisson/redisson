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
package org.redisson.client.codec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.redisson.codec.JsonJacksonCodec;

import java.io.IOException;

public class JsonJacksonMapValueCodec<T> extends JsonJacksonCodec {
    private final ObjectMapper mapper;
    private TypeReference<T> typeReference;

    public JsonJacksonMapValueCodec(Class<T> klass) {
        this(new TypeReference<T>() {
        });
    }

    public JsonJacksonMapValueCodec(TypeReference<T> typeReference) {
        this.typeReference = typeReference;
        this.mapper = initObjectMapper();
    }

    @Override
    public Decoder<Object> getMapValueDecoder() {
        return new Decoder<Object>() {
            @Override
            public Object decode(ByteBuf buf, State state) throws IOException {
                return mapper.readValue(new ByteBufInputStream(buf), typeReference);
            }
        };
    }

    @Override
    public Encoder getMapValueEncoder() {
        return new Encoder() {
            @Override
            public byte[] encode(Object in) throws IOException {
                return mapper.writeValueAsBytes(in);
            }
        };
    }
}