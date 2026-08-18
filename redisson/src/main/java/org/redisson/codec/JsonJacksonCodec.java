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

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTypeResolverBuilder;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Json codec based on Jackson implementation.
 * https://github.com/FasterXML/jackson
 * <p>
 * Fully thread-safe.
 *
 * @see org.redisson.codec.CborJacksonCodec
 * @see org.redisson.codec.MsgPackJacksonCodec
 *
 * @author Nikita Koksharov
 *
 */
public class JsonJacksonCodec extends BaseCodec {

    public static final JsonJacksonCodec INSTANCE = new JsonJacksonCodec();

    @JsonIdentityInfo(generator=ObjectIdGenerators.IntSequenceGenerator.class, property="@id")
    @JsonAutoDetect(fieldVisibility = Visibility.NON_PRIVATE,
                    getterVisibility = Visibility.PUBLIC_ONLY, 
                    setterVisibility = Visibility.NONE, 
                    isGetterVisibility = Visibility.NONE)
    public static class ThrowableMixIn {
        
    }

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

        private static final long serialVersionUID = -6197582101777274236L;
        private static final String PRIMITIVE_TYPE_CODES = "ZBCDFIJS";
        private static final int MAX_TYPE_DEPTH = 100;

        private final Set<String> allowedClasses;

        AllowedClassesValidator(Set<String> allowedClasses) {
            this.allowedClasses = allowedClasses;
        }

        @Override
        public Validity validateSubClassName(MapperConfig<?> config, JavaType baseType, String subClassName) {
            if (!isAllowed(subClassName)) {
                return Validity.DENIED;
            }
            return Validity.INDETERMINATE;
        }

        @Override
        public Validity validateSubType(MapperConfig<?> config, JavaType baseType, JavaType subType) {
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

    protected final ObjectMapper mapObjectMapper;

    final Set<String> allowedClasses;

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
            } catch (Exception e) {
                out.release();
                throw new IOException(e);
            }
        }
    };

    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            return mapObjectMapper.readValue((InputStream) new ByteBufInputStream(buf), Object.class);
        }
    };
    
    public JsonJacksonCodec() {
        this(new ObjectMapper());
    }
    
    /**
     * Creates a codec restricted to the defined class names.
     * <p>
     * An empty set allows any class.
     *
     * @param allowedClasses class names allowed during decoding
     */
    public JsonJacksonCodec(Set<String> allowedClasses) {
        this(new ObjectMapper(), true, allowedClasses);
    }

    public JsonJacksonCodec(ClassLoader classLoader) {
        this(createObjectMapper(classLoader, new ObjectMapper()));
    }

    /**
     * Creates a codec with the specified class loader restricted to the defined class names.
     * <p>
     * An empty set allows any class.
     *
     * @param classLoader the class loader used to resolve stored class names
     * @param allowedClasses class names allowed during decoding
     */
    public JsonJacksonCodec(ClassLoader classLoader, Set<String> allowedClasses) {
        this(createObjectMapper(classLoader, new ObjectMapper()), true, allowedClasses);
    }

    public JsonJacksonCodec(ClassLoader classLoader, JsonJacksonCodec codec) {
        this(createObjectMapper(classLoader, codec.mapObjectMapper.copy()), true, codec.allowedClasses);
    }

    private static boolean warmedup = false;

    private void warmup() {
        if (getValueEncoder() == null || getValueDecoder() == null || warmedup) {
            return;
        }
        warmedup = true;

        ByteBuf d = null;
        try {
            d = getValueEncoder().encode("testValue");
            getValueDecoder().decode(d, null);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (d != null) {
                d.release();
            }
        }
    }

    protected static ObjectMapper createObjectMapper(ClassLoader classLoader, ObjectMapper om) {
        TypeFactory tf = om.getTypeFactory().withClassLoader(classLoader);
        om.setTypeFactory(tf);
        return om;
    }

    public JsonJacksonCodec(ObjectMapper mapObjectMapper) {
        this(mapObjectMapper, true);
        warmup();
    }

    public JsonJacksonCodec(ObjectMapper mapObjectMapper, boolean copy) {
        this(mapObjectMapper, copy, Collections.emptySet());
    }

    /**
     * Creates a codec with a pre-configured ObjectMapper restricted to the defined class names.
     * <p>
     * An empty set allows any class.
     *
     * @param mapObjectMapper the ObjectMapper to use for serialization/deserialization
     * @param copy defines whether the ObjectMapper should be copied before it's configured
     * @param allowedClasses class names allowed during decoding
     */
    public JsonJacksonCodec(ObjectMapper mapObjectMapper, boolean copy, Set<String> allowedClasses) {
        this.allowedClasses = copyOf(allowedClasses);
        if (copy) {
            this.mapObjectMapper = mapObjectMapper.copy();
        } else {
            this.mapObjectMapper = mapObjectMapper;
        }
        init(this.mapObjectMapper);
        initTypeInclusion(this.mapObjectMapper);
        if (!this.allowedClasses.isEmpty()) {
            // covers the type information defined through a @JsonTypeInfo annotation,
            // which is resolved through the validator of the mapper instead of the one of the default typing
            this.mapObjectMapper.setPolymorphicTypeValidator(createPolymorphicTypeValidator());
        }
        warmup();
    }

    private static Set<String> copyOf(Set<String> allowedClasses) {
        if (allowedClasses == null || allowedClasses.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(allowedClasses));
    }

    protected PolymorphicTypeValidator createPolymorphicTypeValidator() {
        if (allowedClasses.isEmpty()) {
            return LaissezFaireSubTypeValidator.instance;
        }
        return new AllowedClassesValidator(allowedClasses);
    }

    protected void initTypeInclusion(ObjectMapper mapObjectMapper) {
        mapObjectMapper.addMixIn(UUID.class, UuidMixin.class);
        TypeResolverBuilder<?> mapTyper = new DefaultTypeResolverBuilder(DefaultTyping.NON_FINAL,
                                                                            createPolymorphicTypeValidator()) {
            public boolean useForType(JavaType t) {
                switch (_appliesFor) {
                case NON_CONCRETE_AND_ARRAYS:
                    while (t.isArrayType()) {
                        t = t.getContentType();
                    }
                    // fall through
                case OBJECT_AND_NON_CONCRETE:
                    return t.getRawClass() == Object.class || !t.isConcrete();
                case NON_FINAL:
                    while (t.isArrayType()) {
                        t = t.getContentType();
                    }
                    // to fix problem with wrong long to int conversion
                    if (t.getRawClass() == Long.class) {
                        return true;
                    }
                    if (t.getRawClass() == XMLGregorianCalendar.class) {
                        return false;
                    }
                    return !t.isFinal(); // includes Object.class
                default:
                    // case JAVA_LANG_OBJECT:
                    return t.getRawClass() == Object.class;
                }
            }
        };
        mapTyper.init(JsonTypeInfo.Id.CLASS, null);
        mapTyper.inclusion(JsonTypeInfo.As.PROPERTY);
        mapObjectMapper.setDefaultTyping(mapTyper);
    }

    protected void init(ObjectMapper objectMapper) {
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.setVisibility(objectMapper.getSerializationConfig()
                                                    .getDefaultVisibilityChecker()
                                                        .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                                                        .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                                                        .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                                                        .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.enable(Feature.WRITE_BIGDECIMAL_AS_PLAIN);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        objectMapper.addMixIn(Throwable.class, ThrowableMixIn.class);
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
        if (mapObjectMapper.getTypeFactory().getClassLoader() != null) {
            return mapObjectMapper.getTypeFactory().getClassLoader();
        }

        return super.getClassLoader();
    }

    public ObjectMapper getObjectMapper() {
        return mapObjectMapper;
    }
}
