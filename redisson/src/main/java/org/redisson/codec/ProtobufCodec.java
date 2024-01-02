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

import com.fasterxml.jackson.databind.cfg.SerializerFactoryConfig;
import com.fasterxml.jackson.databind.ser.BasicSerializerFactory;
import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class ProtobufCodec extends BaseCodec {
    private final Class<?> mapKeyClass;
    private final Class<?> mapValueClass;
    private final Class<?> valueClass;

    //classes in blacklist will not be serialized using protobuf ,but instead will use blacklistCodec
    private final Set<String> protobufBlacklist;
    //default value is JsonJacksonCodec
    private final Codec blacklistCodec;

    public ProtobufCodec(Class<?> mapKeyClass, Class<?> mapValueClass) {
        this(mapKeyClass, mapValueClass, null, null);
    }

    /**
     * @param blacklistCodec classes in protobufBlacklist will use this codec
     */
    public ProtobufCodec(Class<?> mapKeyClass, Class<?> mapValueClass, Codec blacklistCodec) {
        this(mapKeyClass, mapValueClass, null, blacklistCodec);
    }

    public ProtobufCodec(Class<?> valueClass) {
        this(null, null, valueClass, null);
    }

    /**
     * @param blacklistCodec classes in protobufBlacklist will use this codec
     */
    public ProtobufCodec(Class<?> valueClass, Codec blacklistCodec) {
        this(null, null, valueClass, blacklistCodec);
    }

    private ProtobufCodec(Class<?> mapKeyClass, Class<?> mapValueClass, Class<?> valueClass, Codec blacklistCodec) {
        this.mapKeyClass = mapKeyClass;
        this.mapValueClass = mapValueClass;
        this.valueClass = valueClass;
        if (blacklistCodec == null) {
            this.blacklistCodec = new JsonJacksonCodec();
        } else {
            if (blacklistCodec instanceof ProtobufCodec) {
                //will loop infinitely when encode or decode
                throw new IllegalArgumentException("BlacklistCodec can not be ProtobufCodec");
            }
            this.blacklistCodec = blacklistCodec;
        }

        protobufBlacklist = new HashSet<>();
        protobufBlacklist.addAll(BasicSerializerFactoryConcreteGetter.getConcreteKeySet());
        protobufBlacklist.add(ArrayList.class.getName());
        protobufBlacklist.add(HashSet.class.getName());
        protobufBlacklist.add(HashMap.class.getName());
    }

    public void addBlacklist(Class<?> clazz) {
        protobufBlacklist.add(clazz.getName());
    }

    public void removeBlacklist(Class<?> clazz) {
        protobufBlacklist.remove(clazz.getName());
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return createDecoder(valueClass, blacklistCodec.getValueDecoder());
    }

    @Override
    public Encoder getValueEncoder() {
        return createEncoder(valueClass, blacklistCodec.getValueEncoder());
    }

    @Override
    public Decoder<Object> getMapValueDecoder() {
        return createDecoder(mapValueClass, blacklistCodec.getMapValueDecoder());
    }

    @Override
    public Encoder getMapValueEncoder() {
        return createEncoder(mapValueClass, blacklistCodec.getMapValueEncoder());
    }

    @Override
    public Decoder<Object> getMapKeyDecoder() {
        return createDecoder(mapKeyClass, blacklistCodec.getMapKeyDecoder());
    }

    @Override
    public Encoder getMapKeyEncoder() {
        return createEncoder(mapKeyClass, blacklistCodec.getMapKeyEncoder());
    }

    private Decoder<Object> createDecoder(Class<?> clazz, Decoder<Object> blacklistDecoder) {
        if (clazz == null) {
            throw new IllegalArgumentException("class to create protobuf decoder can not be null");
        }

        return new Decoder<Object>() {
            @Override
            public Object decode(ByteBuf buf, State state) throws IOException {
                //use blacklistDecoder
                if (protobufBlacklist.contains(clazz.getName())) {
                    return blacklistDecoder.decode(buf, state);
                }

                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                if (MessageLite.class.isAssignableFrom(clazz)) {
                    //native deserialize
                    try {
                        return clazz.getDeclaredMethod("parseFrom", byte[].class).invoke(clazz, bytes);
                    } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    //protostuff
                    return ProtostuffUtils.deserialize(bytes, clazz);
                }
            }
        };
    }

    private Encoder createEncoder(Class<?> clazz, Encoder blacklistEncoder) {
        if (clazz == null) {
            throw new IllegalArgumentException("class to create protobuf encoder can not be null");
        }
        return new Encoder() {
            @Override
            public ByteBuf encode(Object in) throws IOException {
                //use blacklistEncoder
                if (protobufBlacklist.contains(clazz.getName())) {
                    return blacklistEncoder.encode(in);
                }

                ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
                if (MessageLite.class.isAssignableFrom(clazz)) {
                    //native serialize
                    out.writeBytes(((MessageLite) in).toByteArray());
                } else {
                    //protostuff
                    out.writeBytes(ProtostuffUtils.serialize(in));
                }
                return out;
            }
        };
    }

    private static final class ProtostuffUtils {

        @SuppressWarnings("unchecked")
        public static <T> byte[] serialize(T obj) {
            LinkedBuffer buffer = LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE);
            try {
                return ProtostuffIOUtil.toByteArray(obj, RuntimeSchema.getSchema((Class<T>) obj.getClass()), buffer);
            } finally {
                buffer.clear();
            }
        }

        public static <T> T deserialize(byte[] data, Class<T> clazz) {
            Schema<T> schema = RuntimeSchema.getSchema(clazz);
            T obj = schema.newMessage();
            ProtostuffIOUtil.mergeFrom(data, obj, schema);
            return obj;
        }

    }

    private abstract static class BasicSerializerFactoryConcreteGetter extends BasicSerializerFactory {
        protected BasicSerializerFactoryConcreteGetter(SerializerFactoryConfig config) {
            super(config);
        }

        private static Set<String> getConcreteKeySet() {
            Set<String> concreteKeySet = new HashSet<>();
            if (_concrete != null && !_concrete.isEmpty()) {
                concreteKeySet.addAll(_concrete.keySet());
            }
            if (_concreteLazy != null && !_concreteLazy.isEmpty()) {
                concreteKeySet.addAll(_concreteLazy.keySet());
            }
            return concreteKeySet;
        }
    }
}