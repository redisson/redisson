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
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Jackson Json codec.
 * <p>
 * Fully thread-safe.
 *
 * @author Nikita Koksharov
 *
 */
public class JacksonCodec<T> implements JsonCodec {

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

    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            if (valueClass != null) {
                return mapObjectMapper.readValue((InputStream) new ByteBufInputStream(buf), valueClass);
            }
            return mapObjectMapper.readValue((InputStream) new ByteBufInputStream(buf), valueTypeReference);
        }
    };

    private Class<T> valueClass;
    private TypeReference<T> valueTypeReference;

    private final ObjectMapper mapObjectMapper;

    public JacksonCodec(Class<T> valueClass) {
        if (valueClass == null) {
            throw new NullPointerException("valueClass isn't defined");
        }
        this.valueClass = valueClass;
        this.mapObjectMapper = new ObjectMapper();
        init(mapObjectMapper);
    }

    public JacksonCodec(TypeReference<T> valueTypeReference) {
        if (valueTypeReference == null) {
            throw new NullPointerException("valueTypeReference isn't defined");
        }
        this.valueTypeReference = valueTypeReference;
        this.mapObjectMapper = new ObjectMapper();
        init(mapObjectMapper);
    }

    public JacksonCodec(ObjectMapper mapObjectMapper, TypeReference<T> valueTypeReference) {
        if (mapObjectMapper == null) {
            throw new NullPointerException("mapObjectMapper isn't defined");
        }
        if (valueTypeReference == null) {
            throw new NullPointerException("valueTypeReference isn't defined");
        }
        this.mapObjectMapper = mapObjectMapper;
        this.valueTypeReference = valueTypeReference;
    }

    public JacksonCodec(ObjectMapper mapObjectMapper, Class<T> valueClass) {
        if (mapObjectMapper == null) {
            throw new NullPointerException("mapObjectMapper isn't defined");
        }
        if (valueClass == null) {
            throw new NullPointerException("valueClass isn't defined");
        }
        this.mapObjectMapper = mapObjectMapper;
        this.valueClass = valueClass;
    }

    public JacksonCodec(ClassLoader classLoader, JacksonCodec<T> codec) {
        this.valueClass = codec.valueClass;
        this.valueTypeReference = codec.valueTypeReference;
        this.mapObjectMapper = createObjectMapper(classLoader, codec.mapObjectMapper.copy());
    }

    protected static ObjectMapper createObjectMapper(ClassLoader classLoader, ObjectMapper om) {
        TypeFactory tf = TypeFactory.defaultInstance().withClassLoader(classLoader);
        om.setTypeFactory(tf);
        return om;
    }

    protected void init(ObjectMapper objectMapper) {
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setVisibility(objectMapper.getSerializationConfig()
                .getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
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
