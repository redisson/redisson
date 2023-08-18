package org.redisson.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.BasicSerializerFactory;
import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    //classes in blacklist will not be serialized using protobuf ,but instead will use jackson
    private final Set<String> protobufBlacklist;

    private final static Set<String> CLASSES_OWN_JACKSON_SERIALIZER = new HashSet<>();

    static {
        try {
            Field concreteField = BasicSerializerFactory.class.getDeclaredField("_concrete");
            concreteField.setAccessible(true);
            CLASSES_OWN_JACKSON_SERIALIZER.addAll(((Map) concreteField.get(BasicSerializerFactory.class)).keySet());
            Field _concreteLazyField = BasicSerializerFactory.class.getDeclaredField("_concreteLazy");
            _concreteLazyField.setAccessible(true);
            CLASSES_OWN_JACKSON_SERIALIZER.addAll(((Map) concreteField.get(BasicSerializerFactory.class)).keySet());
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            log.warn("ProtobufCodec failed to retrieve Jackson serializers. Maybe some objects (like String which using StringSerializer is better) will be serialized with protobuf unless the protobuf blacklist is explicitly set.");
        }
    }

    public ProtobufCodec(Class<?> mapKeyClass, Class<?> mapValueClass) {
        this(mapKeyClass, mapValueClass, null);
    }

    public ProtobufCodec(Class<?> valueClass) {
        this(null, null, valueClass);
    }

    private ProtobufCodec(Class<?> mapKeyClass, Class<?> mapValueClass, Class<?> valueClass) {
        this.mapKeyClass = mapKeyClass;
        this.mapValueClass = mapValueClass;
        this.valueClass = valueClass;

        protobufBlacklist = new HashSet<>();
        protobufBlacklist.addAll(CLASSES_OWN_JACKSON_SERIALIZER);
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
        return createDecoder(valueClass);
    }

    @Override
    public Encoder getValueEncoder() {
        return createEncoder(valueClass);
    }

    @Override
    public Decoder<Object> getMapValueDecoder() {
        return createDecoder(mapValueClass);
    }

    @Override
    public Encoder getMapValueEncoder() {
        return createEncoder(mapValueClass);
    }

    @Override
    public Decoder<Object> getMapKeyDecoder() {
        return createDecoder(mapKeyClass);
    }

    @Override
    public Encoder getMapKeyEncoder() {
        return createEncoder(mapKeyClass);
    }

    private Decoder<Object> createDecoder(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("class to create protobuf decoder can not be null");
        }

        return new Decoder<Object>() {
            @Override
            public Object decode(ByteBuf buf, State state) throws IOException {
                //use jackson deserializer
                if (protobufBlacklist.contains(clazz.getName())) {
                    return objectMapper.readValue((InputStream) new ByteBufInputStream(buf), clazz);
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

    private Encoder createEncoder(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("class to create protobuf encoder can not be null");
        }
        return new Encoder() {
            @Override
            public ByteBuf encode(Object in) throws IOException {
                //use jackson serializer
                ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
                if (protobufBlacklist.contains(clazz.getName())) {
                    try {
                        ByteBufOutputStream os = new ByteBufOutputStream(out);
                        objectMapper.writeValue((OutputStream) os, in);
                        return os.buffer();
                    } catch (IOException e) {
                        out.release();
                        throw e;
                    }
                }

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