package org.redisson.codec;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ProtobufCodec extends BaseCodec {
    private static final Logger log = LoggerFactory.getLogger(ProtobufCodec.class);
    private final Class<?> mapKeyClass;
    private final Class<?> mapValueClass;
    private final Class<?> valueClass;

    //classes in blacklist will not be serialized using protobuf ,but instead will use blacklistCodec
    private final Set<String> protobufBlacklist;
    //default value is JsonJacksonCodec
    private final Codec blacklistCodec;

    private final static Set<String> CLASSES_NOT_SUITABLE_FOR_PROTOBUF = new HashSet<>();

    static {
        try {
            Field concreteField = BasicSerializerFactory.class.getDeclaredField("_concrete");
            concreteField.setAccessible(true);
            CLASSES_NOT_SUITABLE_FOR_PROTOBUF.addAll(((Map) concreteField.get(BasicSerializerFactory.class)).keySet());
            Field _concreteLazyField = BasicSerializerFactory.class.getDeclaredField("_concreteLazy");
            _concreteLazyField.setAccessible(true);
            CLASSES_NOT_SUITABLE_FOR_PROTOBUF.addAll(((Map) concreteField.get(BasicSerializerFactory.class)).keySet());
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            log.warn("ProtobufCodec failed to retrieve classes not suitable for protobuf.Maybe some objects (like String which using StringSerializer is better) will be serialized with protobuf unless the protobuf blacklist is explicitly set.");
        }
    }

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
        protobufBlacklist.addAll(CLASSES_NOT_SUITABLE_FOR_PROTOBUF);
        protobufBlacklist.add("java.util.ArrayList");
        protobufBlacklist.add("java.util.HashSet");
        protobufBlacklist.add("java.util.HashMap");
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

    private static class ProtostuffUtils {

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


}