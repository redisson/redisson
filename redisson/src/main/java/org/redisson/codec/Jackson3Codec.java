/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Jackson 3 JSON codec.
 * <p>
 * Fully thread-safe.
 *
 * @author Nikita Koksharov
 *
 */
public class Jackson3Codec<T> implements JsonCodec {

    private final Encoder encoder = new Encoder() {
        @Override
        public ByteBuf encode(Object in) {
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
            try {
                ByteBufOutputStream os = new ByteBufOutputStream(out);
                mapObjectMapper.writeValue((OutputStream) os, in);
                return os.buffer();
            } catch (JacksonException e) {
                out.release();
                throw e;
            }
        }
    };

    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) {
            if (valueClass != null) {
                return mapObjectMapper.readValue((InputStream) new ByteBufInputStream(buf), valueClass);
            }
            return mapObjectMapper.readValue((InputStream) new ByteBufInputStream(buf), valueTypeReference);
        }
    };

    private Class<T> valueClass;
    private TypeReference<T> valueTypeReference;

    private final ObjectMapper mapObjectMapper;

    public Jackson3Codec(Class<T> valueClass) {
        if (valueClass == null) {
            throw new NullPointerException("valueClass isn't defined");
        }
        this.valueClass = valueClass;
        this.mapObjectMapper = createDefaultMapper();
    }

    public Jackson3Codec(TypeReference<T> valueTypeReference) {
        if (valueTypeReference == null) {
            throw new NullPointerException("valueTypeReference isn't defined");
        }
        this.valueTypeReference = valueTypeReference;
        this.mapObjectMapper = createDefaultMapper();
    }

    public Jackson3Codec(ObjectMapper mapObjectMapper, TypeReference<T> valueTypeReference) {
        if (mapObjectMapper == null) {
            throw new NullPointerException("mapObjectMapper isn't defined");
        }
        if (valueTypeReference == null) {
            throw new NullPointerException("valueTypeReference isn't defined");
        }
        this.mapObjectMapper = mapObjectMapper;
        this.valueTypeReference = valueTypeReference;
    }

    public Jackson3Codec(ObjectMapper mapObjectMapper, Class<T> valueClass) {
        if (mapObjectMapper == null) {
            throw new NullPointerException("mapObjectMapper isn't defined");
        }
        if (valueClass == null) {
            throw new NullPointerException("valueClass isn't defined");
        }
        this.mapObjectMapper = mapObjectMapper;
        this.valueClass = valueClass;
    }

    public Jackson3Codec(ClassLoader classLoader, Jackson3Codec<T> codec) {
        this.valueClass = codec.valueClass;
        this.valueTypeReference = codec.valueTypeReference;
        this.mapObjectMapper = createObjectMapper(classLoader, codec.mapObjectMapper.rebuild().build());
    }

    protected static ObjectMapper createObjectMapper(ClassLoader classLoader, ObjectMapper sourceMapper) {
        TypeFactory tf = TypeFactory.createDefaultInstance().withClassLoader(classLoader);
        return sourceMapper.rebuild()
                .typeFactory(tf)
                .build();
    }

    ObjectMapper createDefaultMapper() {
        return init(JsonMapper.builder()).build();
    }

    protected JsonMapper.Builder init(JsonMapper.Builder builder) {
        return builder.changeDefaultPropertyInclusion(incl -> incl
                            .withValueInclusion(JsonInclude.Include.NON_NULL)
                            .withContentInclusion(JsonInclude.Include.NON_NULL))
                      .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

                      .changeDefaultVisibility(vc -> vc
                           .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                           .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                           .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                           .withCreatorVisibility(JsonAutoDetect.Visibility.NONE))
                      .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                      .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
    }

    @Override
    public Encoder getEncoder() {
        return encoder;
    }

    @Override
    public Decoder<Object> getDecoder() {
        return decoder;
    }
}