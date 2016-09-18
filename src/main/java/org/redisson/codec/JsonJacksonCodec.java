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
package org.redisson.codec;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTypeResolverBuilder;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.io.InputStream;
import java.io.IOException;

/**
 *
 * @see org.redisson.codec.CborJacksonCodec
 * @see org.redisson.codec.MsgPackJacksonCodec
 *
 * @author Nikita Koksharov
 *
 */
public class JsonJacksonCodec implements Codec {

    public static final JsonJacksonCodec INSTANCE = new JsonJacksonCodec();

    @JsonIdentityInfo(generator=ObjectIdGenerators.IntSequenceGenerator.class, property="@id")
    @JsonAutoDetect(fieldVisibility = Visibility.NONE, getterVisibility = Visibility.PUBLIC_ONLY, setterVisibility = Visibility.PUBLIC_ONLY, isGetterVisibility = Visibility.PUBLIC_ONLY)
    public static class ThrowableMixIn {
        
    }
    
    private final ObjectMapper mapObjectMapper = initObjectMapper();

    protected ObjectMapper initObjectMapper() {
        return new ObjectMapper();
    }

    private final Encoder encoder = new Encoder() {
        @Override
        public byte[] encode(Object in) throws IOException {
            return mapObjectMapper.writeValueAsBytes(in);
        }
    };

    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            return mapObjectMapper.readValue((InputStream)new ByteBufInputStream(buf), Object.class);
        }
    };

    public JsonJacksonCodec() {
        init(mapObjectMapper);
        // type info inclusion
        TypeResolverBuilder<?> mapTyper = new DefaultTypeResolverBuilder(DefaultTyping.NON_FINAL) {
            public boolean useForType(JavaType t) {
                switch (_appliesFor) {
                case NON_CONCRETE_AND_ARRAYS:
                    while (t.isArrayType()) {
                        t = t.getContentType();
                    }
                    // fall through
                case OBJECT_AND_NON_CONCRETE:
                    return (t.getRawClass() == Object.class) || !t.isConcrete();
                case NON_FINAL:
                    while (t.isArrayType()) {
                        t = t.getContentType();
                    }
                    // to fix problem with wrong long to int conversion
                    if (t.getRawClass() == Long.class) {
                        return true;
                    }
                    return !t.isFinal(); // includes Object.class
                default:
                    // case JAVA_LANG_OBJECT:
                    return (t.getRawClass() == Object.class);
                }
            }
        };
        mapTyper.init(JsonTypeInfo.Id.CLASS, null);
        mapTyper.inclusion(JsonTypeInfo.As.PROPERTY);
        mapObjectMapper.setDefaultTyping(mapTyper);
    }

    protected void init(ObjectMapper objectMapper) {
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.setVisibilityChecker(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY).withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        objectMapper.addMixIn(Throwable.class, ThrowableMixIn.class);
    }

    @Override
    public Decoder<Object> getMapValueDecoder() {
        return decoder;
    }

    @Override
    public Encoder getMapValueEncoder() {
        return encoder;
    }

    @Override
    public Decoder<Object> getMapKeyDecoder() {
        return decoder;
    }

    @Override
    public Encoder getMapKeyEncoder() {
        return encoder;
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

    @Override
    public Encoder getValueEncoder() {
        return encoder;
    }

}
