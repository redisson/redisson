/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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

    static class AllowedClassesValidator extends PolymorphicTypeValidator.Base {

        private static final long serialVersionUID = 5836054926724271886L;
        private static final String PRIMITIVE_TYPE_CODES = "ZBCDFIJS";
        private static final int MAX_TYPE_DEPTH = 100;

        private final Set<String> allowedClasses;

        AllowedClassesValidator(Set<String> allowedClasses) {
            this.allowedClasses = allowedClasses;
        }

        @Override
        public Validity validateSubClassName(DatabindContext ctxt, JavaType baseType, String subClassName) {
            if (!isAllowed(subClassName)) {
                return Validity.DENIED;
            }
            return Validity.INDETERMINATE;
        }

        @Override
        public Validity validateSubType(DatabindContext ctxt, JavaType baseType, JavaType subType) {
            if (isAllowed(subType, 0)) {
                return Validity.ALLOWED;
            }
            return Validity.DENIED;
        }

        private boolean isAllowed(JavaType type, int depth) {
            if (depth > MAX_TYPE_DEPTH) {
                return false;
            }
            if (!isAllowed(type.getRawClass().getName())) {
                return false;
            }
            for (int i = 0; i < type.containedTypeCount(); i++) {
                JavaType containedType = type.containedType(i);
                if (containedType != null && !isAllowed(containedType, depth + 1)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isAllowed(String className) {
            if (allowedClasses.contains(className)) {
                return true;
            }
            String elementName = className;
            int idx = 0;
            while (idx < elementName.length() && elementName.charAt(idx) == '[') {
                idx++;
            }
            if (idx > 0) {
                if (idx >= elementName.length()) {
                    return false;
                }
                if (elementName.charAt(idx) != 'L') {
                    // an array of a primitive type, like [I, holds no class name
                    return idx == elementName.length() - 1
                            && PRIMITIVE_TYPE_CODES.indexOf(elementName.charAt(idx)) >= 0;
                }
                if (!elementName.endsWith(";")) {
                    return false;
                }
                elementName = elementName.substring(idx + 1, elementName.length() - 1);
            }
            return elementName.startsWith("java.") || allowedClasses.contains(elementName);
        }
    }

    public static final JsonJackson3Codec INSTANCE = new JsonJackson3Codec();

    final ObjectMapper mapObjectMapper;

    final Set<String> allowedClasses;

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
        this.allowedClasses = Collections.emptySet();
        this.mapObjectMapper = createDefaultMapper();
    }

    /**
     * Creates a codec restricted to the defined class names.
     * <p>
     * An empty set allows any class.
     *
     * @param allowedClasses class names allowed during decoding
     */
    public JsonJackson3Codec(Set<String> allowedClasses) {
        this.allowedClasses = copyOf(allowedClasses);
        this.mapObjectMapper = createDefaultMapper();
    }

    /**
     * Creates a codec with the specified class loader.
     *
     * @param classLoader the class loader to use for type resolution
     */
    public JsonJackson3Codec(ClassLoader classLoader) {
        this.allowedClasses = Collections.emptySet();
        this.mapObjectMapper = createDefaultMapper(classLoader);
    }

    /**
     * Creates a codec with the specified class loader restricted to the defined class names.
     * <p>
     * An empty set allows any class.
     *
     * @param classLoader the class loader to use for type resolution
     * @param allowedClasses class names allowed during decoding
     */
    public JsonJackson3Codec(ClassLoader classLoader, Set<String> allowedClasses) {
        this.allowedClasses = copyOf(allowedClasses);
        this.mapObjectMapper = createDefaultMapper(classLoader);
    }

    /**
     * Creates a codec with the specified class loader and existing codec for cloning.
     *
     * @param classLoader the class loader to use
     * @param codec the existing codec to copy settings from
     */
    public JsonJackson3Codec(ClassLoader classLoader, JsonJackson3Codec codec) {
        this(createMapper(classLoader, codec.mapObjectMapper.rebuild().build()), codec.allowedClasses);
    }

    /**
     * Creates a codec with a pre-configured ObjectMapper.
     *
     * @param mapObjectMapper the ObjectMapper to use for serialization/deserialization
     */
    public JsonJackson3Codec(ObjectMapper mapObjectMapper) {
        this(mapObjectMapper, Collections.emptySet());
    }

    public JsonJackson3Codec(ObjectMapper mapObjectMapper, boolean copy) {
        this.allowedClasses = Collections.emptySet();
        if (copy) {
            this.mapObjectMapper = mapObjectMapper.rebuild().build();
        } else {
            this.mapObjectMapper = mapObjectMapper;
        }
    }

    private JsonJackson3Codec(ObjectMapper mapObjectMapper, Set<String> allowedClasses) {
        this.allowedClasses = allowedClasses;
        this.mapObjectMapper = mapObjectMapper;
    }

    private static Set<String> copyOf(Set<String> allowedClasses) {
        if (allowedClasses == null || allowedClasses.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(allowedClasses));
    }

    protected PolymorphicTypeValidator createPolymorphicTypeValidator() {
        if (allowedClasses.isEmpty()) {
            return BasicPolymorphicTypeValidator.builder()
                    .allowIfBaseType(Object.class)
                    .allowIfSubType(Object.class)
                    .build();
        }
        return new AllowedClassesValidator(allowedClasses);
    }

    protected void initTypeInclusion(JsonMapper.Builder builder) {
        PolymorphicTypeValidator typeValidator = createPolymorphicTypeValidator();

        builder.addMixIn(UUID.class, UuidMixin.class);
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
                .disable(StreamWriteFeature.AUTO_CLOSE_TARGET);

        initTypeInclusion(b);
        if (!allowedClasses.isEmpty()) {
            // covers the type information defined through a @JsonTypeInfo annotation,
            // which is resolved through the validator of the mapper instead of the one of the default typing
            b.polymorphicTypeValidator(createPolymorphicTypeValidator());
        }
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