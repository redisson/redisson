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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import tools.jackson.databind.type.TypeFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Jackson 3.x based JSON codec.
 *
 * @author Nikita Koksharov
 *
 */
public class JsonJackson3Codec extends BaseCodec {

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    public abstract static class UuidMixin {

        @JsonValue
        public abstract String toString();

        @JsonCreator
        public static UUID fromString(String value) {
            if (value != null) {
                return UUID.fromString(value);
            }
            return null;
        }
    }

    public static final JsonJackson3Codec INSTANCE = new JsonJackson3Codec();

    final ObjectMapper mapObjectMapper;

    private final Encoder encoder = new Encoder() {
        @Override
        public ByteBuf encode(Object in) throws IOException {
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
            try {
                ByteBufOutputStream os = new ByteBufOutputStream(out);
                mapObjectMapper.writeValue((OutputStream) os, in);
                return os.buffer();
            } catch (Exception e) {
                out.release();
                throw e;
            }
        }
    };

    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            return mapObjectMapper.readValue((InputStream) new ByteBufInputStream(buf), Object.class);
        }
    };

    /**
     * Creates a codec with default settings.
     */
    public JsonJackson3Codec() {
        this.mapObjectMapper = createDefaultMapper();
    }

    /**
     * Creates a codec with the specified class loader.
     *
     * @param classLoader the class loader to use for type resolution
     */
    public JsonJackson3Codec(ClassLoader classLoader) {
        this.mapObjectMapper = createDefaultMapper(classLoader);
    }

    /**
     * Creates a codec with the specified class loader and existing codec for cloning.
     *
     * @param classLoader the class loader to use
     * @param codec the existing codec to copy settings from
     */
    public JsonJackson3Codec(ClassLoader classLoader, JsonJackson3Codec codec) {
        this(createMapper(classLoader, codec.mapObjectMapper.rebuild().build()));
    }

    /**
     * Creates a codec with a pre-configured ObjectMapper.
     *
     * @param mapObjectMapper the ObjectMapper to use for serialization/deserialization
     */
    public JsonJackson3Codec(ObjectMapper mapObjectMapper) {
        this.mapObjectMapper = mapObjectMapper;
    }

    public JsonJackson3Codec(ObjectMapper mapObjectMapper, boolean copy) {
        if (copy) {
            this.mapObjectMapper = mapObjectMapper.rebuild().build();
        } else {
            this.mapObjectMapper = mapObjectMapper;
        }
    }


    protected void initTypeInclusion(JsonMapper.Builder builder) {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .allowIfSubType(Object.class)
                .build();

        builder.activateDefaultTypingAsProperty(typeValidator,
                DefaultTyping.NON_FINAL, "@class");
    }

    /**
     * Creates a default ObjectMapper with standard Redisson configuration.
     *
     * @return a configured ObjectMapper
     */
    protected ObjectMapper createDefaultMapper() {
        return createDefaultMapper(null);
    }

    /**
     * Creates a default ObjectMapper with the specified class loader.
     *
     * @param classLoader the class loader to use, or null for the default
     * @return a configured ObjectMapper
     */
    protected ObjectMapper createDefaultMapper(ClassLoader classLoader) {
        TypeFactory typeFactory = TypeFactory.createDefaultInstance();
        if (classLoader != null) {
            typeFactory = typeFactory.withClassLoader(classLoader);
        }

        JsonMapper.Builder b = JsonMapper.builder()
                .typeFactory(typeFactory)
                // Serialization settings
                .changeDefaultPropertyInclusion(incl -> incl
                        .withValueInclusion(JsonInclude.Include.NON_NULL)
                        .withContentInclusion(JsonInclude.Include.NON_NULL))
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)

                // Deserialization settings
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Mapper settings
                .enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
                // Generator settings - don't close the stream, let Redisson handle it
                .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .addMixIn(UUID.class, UuidMixin.class);

        initTypeInclusion(b);
        return b.build();
    }

    protected static ObjectMapper createMapper(ClassLoader classLoader, ObjectMapper existingMapper) {
        TypeFactory typeFactory = existingMapper.getTypeFactory();
        if (classLoader != null) {
            typeFactory = typeFactory.withClassLoader(classLoader);
        }

        return existingMapper.rebuild()
                .typeFactory(typeFactory)
                .build();
    }

    /**
     * Gets the ObjectMapper used by this codec.
     *
     * @return the ObjectMapper
     */
    public ObjectMapper getObjectMapper() {
        return mapObjectMapper;
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

    @Override
    public Encoder getValueEncoder() {
        return encoder;
    }

    @Override
    public ClassLoader getClassLoader() {
        TypeFactory tf = mapObjectMapper.getTypeFactory();
        if (tf.getClassLoader() != null) {
            return tf.getClassLoader();
        }
        return super.getClassLoader();
    }
}