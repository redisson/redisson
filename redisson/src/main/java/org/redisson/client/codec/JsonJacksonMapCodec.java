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
package org.redisson.client.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.redisson.codec.JsonJacksonCodec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

/**
 * @deprecated Use org.redisson.codec.TypedJsonJacksonCodec instead
 * 
 * @author Nikita Koksharov
 * @author Andrej Kazakov
 *
 */
public class JsonJacksonMapCodec extends JsonJacksonCodec {
    
    private Class<?> keyClass;
    private Class<?> valueClass;
    private TypeReference<?> keyTypeReference;
    private TypeReference<?> valueTypeReference;

    private final Encoder encoder = new Encoder() {
        @Override
        public ByteBuf encode(Object in) throws IOException {
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
            try {
                ByteBufOutputStream os = new ByteBufOutputStream(out);
                mapObjectMapper.writeValue((OutputStream) os, in);
                return os.buffer();
            } catch (IOException e) {
                out.release();
                throw e;
            }
        }
    };
    
    private final Decoder<Object> valueDecoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            if (valueClass != null) {
                return mapObjectMapper.readValue((InputStream) new ByteBufInputStream(buf), valueClass);
            }
            return mapObjectMapper.readValue((InputStream) new ByteBufInputStream(buf), valueTypeReference);
        }
    };
    
    private final Decoder<Object> keyDecoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            if (keyClass != null) {
                return mapObjectMapper.readValue((InputStream) new ByteBufInputStream(buf), keyClass);
            }
            return mapObjectMapper.readValue((InputStream) new ByteBufInputStream(buf), keyTypeReference);
        }
    };

    public JsonJacksonMapCodec(Class<?> keyClass, Class<?> valueClass) {
        this(null, null, keyClass, valueClass, new ObjectMapper());
    }
    
    public JsonJacksonMapCodec(Class<?> keyClass, Class<?> valueClass, ObjectMapper mapper) {
        this(null, null, keyClass, valueClass, mapper);
    }
    
    public JsonJacksonMapCodec(TypeReference<?> keyTypeReference, TypeReference<?> valueTypeReference) {
        this(keyTypeReference, valueTypeReference, null, null, new ObjectMapper());
    }
    
    public JsonJacksonMapCodec(TypeReference<?> keyTypeReference, TypeReference<?> valueTypeReference, ObjectMapper mapper) {
        this(keyTypeReference, valueTypeReference, null, null, mapper);
    }
    
    public JsonJacksonMapCodec(ClassLoader classLoader, JsonJacksonMapCodec codec) {
        this(codec.keyTypeReference, codec.valueTypeReference, codec.keyClass, codec.valueClass, createObjectMapper(classLoader, codec.mapObjectMapper.copy()));
    }

    JsonJacksonMapCodec(TypeReference<?> keyTypeReference, TypeReference<?> valueTypeReference, Class<?> keyClass, Class<?> valueClass, ObjectMapper mapper) {
        super(mapper);
        this.keyTypeReference = keyTypeReference;
        this.valueTypeReference = valueTypeReference;
        this.keyClass = keyClass;
        this.valueClass = valueClass;
    }
    
    @Override
    protected void initTypeInclusion(ObjectMapper mapObjectMapper) {
        // avoid type inclusion
    }

    @Override
    public Decoder<Object> getMapKeyDecoder() {
        return keyDecoder;
    }
    
    @Override
    public Encoder getMapValueEncoder() {
        return encoder;
    }
    
    @Override
    public Encoder getMapKeyEncoder() {
        return encoder;
    }
    
    @Override
    public Decoder<Object> getMapValueDecoder() {
        return valueDecoder;
    }

}