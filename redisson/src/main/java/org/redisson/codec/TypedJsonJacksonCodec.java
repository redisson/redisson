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
package org.redisson.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

/**
 * Json Jackson Type codec. Doesn't include `@class` field during data encoding, and doesn't require it for data decoding. 
 * 
 * @author Nikita Koksharov
 * @author Andrej Kazakov
 *
 */
public class TypedJsonJacksonCodec extends JsonJacksonCodec {
    
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
    
    private Decoder<Object> createDecoder(final Class<?> valueClass, final TypeReference<?> valueTypeReference) {
        return new Decoder<Object>() {
            @Override
            public Object decode(ByteBuf buf, State state) throws IOException {
                if (valueClass != null) {
                    return mapObjectMapper.readValue((InputStream) new ByteBufInputStream(buf), valueClass);
                }
                if (valueTypeReference != null) {
                    return mapObjectMapper.readValue((InputStream) new ByteBufInputStream(buf), valueTypeReference);
                }
                return mapObjectMapper.readValue((InputStream) new ByteBufInputStream(buf), Object.class);
            }
        };
    }
    
    private final Decoder<Object> valueDecoder;
    private final Decoder<Object> mapValueDecoder;
    private final Decoder<Object> mapKeyDecoder;
    
    private final TypeReference<?> valueTypeReference;
    private final TypeReference<?> mapKeyTypeReference;
    private final TypeReference<?> mapValueTypeReference;
    
    private final Class<?> valueClass;
    private final Class<?> mapKeyClass; 
    private final Class<?> mapValueClass;

    public TypedJsonJacksonCodec(Class<?> valueClass) {
        this(valueClass, new ObjectMapper());
    }

    public TypedJsonJacksonCodec(Class<?> valueClass, ObjectMapper mapper) {
        this(valueClass, null, null, mapper);
    }
    
    public TypedJsonJacksonCodec(Class<?> mapKeyClass, Class<?> mapValueClass) {
        this(null, mapKeyClass, mapValueClass, new ObjectMapper());
    }

    public TypedJsonJacksonCodec(Class<?> mapKeyClass, Class<?> mapValueClass, ObjectMapper mapper) {
        this(null, mapKeyClass, mapValueClass, mapper);
    }
    
    public TypedJsonJacksonCodec(Class<?> valueClass, Class<?> mapKeyClass, Class<?> mapValueClass) {
        this(null, null, null, valueClass, mapKeyClass, mapValueClass, new ObjectMapper());
    }
    
    public TypedJsonJacksonCodec(Class<?> valueClass, Class<?> mapKeyClass, Class<?> mapValueClass, ObjectMapper mapper) {
        this(null, null, null, valueClass, mapKeyClass, mapValueClass, mapper);
    }

    public TypedJsonJacksonCodec(TypeReference<?> valueTypeReference) {
        this(valueTypeReference, new ObjectMapper());
    }

    public TypedJsonJacksonCodec(TypeReference<?> valueTypeReference, ObjectMapper mapper) {
        this(valueTypeReference, null, null, mapper);
    }

    public TypedJsonJacksonCodec(TypeReference<?> mapKeyTypeReference, TypeReference<?> mapValueTypeReference) {
        this(null, mapKeyTypeReference, mapValueTypeReference);
    }
    
    public TypedJsonJacksonCodec(TypeReference<?> mapKeyTypeReference, TypeReference<?> mapValueTypeReference, ObjectMapper mapper) {
        this(null, mapKeyTypeReference, mapValueTypeReference, mapper);
    }
    
    public TypedJsonJacksonCodec(TypeReference<?> valueTypeReference, TypeReference<?> mapKeyTypeReference, TypeReference<?> mapValueTypeReference) {
        this(valueTypeReference, mapKeyTypeReference, mapValueTypeReference, null, null, null, new ObjectMapper());
    }
    
    public TypedJsonJacksonCodec(TypeReference<?> valueTypeReference, TypeReference<?> mapKeyTypeReference, TypeReference<?> mapValueTypeReference, ObjectMapper mapper) {
        this(valueTypeReference, mapKeyTypeReference, mapValueTypeReference, null, null, null, mapper);
    }
    
    public TypedJsonJacksonCodec(ClassLoader classLoader, TypedJsonJacksonCodec codec) {
        this(codec.valueTypeReference, codec.mapKeyTypeReference, codec.mapValueTypeReference, 
              codec.valueClass, codec.mapKeyClass, codec.mapValueClass,
                createObjectMapper(classLoader, codec.mapObjectMapper.copy()));
    }

    TypedJsonJacksonCodec(
            TypeReference<?> valueTypeReference, TypeReference<?> mapKeyTypeReference, TypeReference<?> mapValueTypeReference, 
            Class<?> valueClass, Class<?> mapKeyClass, Class<?> mapValueClass, ObjectMapper mapper) {
        super(mapper);
        this.mapValueDecoder = createDecoder(mapValueClass, mapValueTypeReference);
        this.mapKeyDecoder = createDecoder(mapKeyClass, mapKeyTypeReference);
        this.valueDecoder = createDecoder(valueClass, valueTypeReference);
        
        this.mapValueClass = mapValueClass;
        this.mapValueTypeReference = mapValueTypeReference;
        this.mapKeyClass = mapKeyClass;
        this.mapKeyTypeReference = mapKeyTypeReference;
        this.valueClass = valueClass;
        this.valueTypeReference = valueTypeReference;
    }
    
    @Override
    protected void initTypeInclusion(ObjectMapper mapObjectMapper) {
        // avoid type inclusion
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return valueDecoder;
    }
    
    @Override
    public Encoder getValueEncoder() {
        return encoder;
    }
    
    @Override
    public Decoder<Object> getMapKeyDecoder() {
        return mapKeyDecoder;
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
        return mapValueDecoder;
    }

}