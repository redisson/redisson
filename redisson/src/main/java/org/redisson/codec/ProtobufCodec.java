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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ProtobufCodec extends BaseCodec {
    private static final Logger log = LoggerFactory.getLogger(ProtobufCodec.class);
    private final Class<?> mapKeyClass;
    private final Class<?> mapValueClass;
    private final Class<?> valueClass;

    private final ObjectMapper objectMapper = new ObjectMapper();

    //class in blacklist will not be serialized using protobuf ,but instead will use jackson
    private final Set<String> protobufBlacklist;

    private final static Set<String> CLASSES_OWNS_JACKSON_SERIALIZER = new HashSet<>();

    static {
        try {
            Field concreteField = BasicSerializerFactory.class.getDeclaredField("_concrete");
            concreteField.setAccessible(true);
            CLASSES_OWNS_JACKSON_SERIALIZER.addAll(((Map) concreteField.get(BasicSerializerFactory.class)).keySet());
            Field _concreteLazyField = BasicSerializerFactory.class.getDeclaredField("_concreteLazy");
            _concreteLazyField.setAccessible(true);
            CLASSES_OWNS_JACKSON_SERIALIZER.addAll(((Map) concreteField.get(BasicSerializerFactory.class)).keySet());
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            log.warn("Failed to retrieve Jackson serializers. Maybe some objects (like String which using StringSerializer is better) will be serialized with protobuf unless the protobuf blacklist is explicitly set.");
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
        protobufBlacklist.addAll(CLASSES_OWNS_JACKSON_SERIALIZER);

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
        return new Decoder<Object>() {
            @Override
            public Object decode(ByteBuf buf, State state) throws IOException {
                if (protobufBlacklist.contains(clazz.getName())) {
                    return objectMapper.readValue((InputStream) new ByteBufInputStream(buf), clazz);
                }
                //todo 使用ByteBuf
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                if (MessageLite.class.isAssignableFrom(clazz)) {
                    try {
                        final Method parseFrom = clazz.getDeclaredMethod("parseFrom", byte[].class);
                        return parseFrom.invoke(clazz, bytes);
                    } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    return ProtostuffUtils.deserialize(bytes, clazz);
                }
            }
        };
    }

    private Encoder createEncoder(Class<?> clazz) {
        return new Encoder() {
            @Override
            public ByteBuf encode(Object in) throws IOException {
                if (protobufBlacklist.contains(clazz.getName())) {
                    ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
                    try {
                        ByteBufOutputStream os = new ByteBufOutputStream(out);
                        objectMapper.writeValue((OutputStream) os, in);
                        return os.buffer();
                    } catch (IOException e) {
                        out.release();
                        throw e;
                    }
                }
                ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
                byte[] bytes;
                if (MessageLite.class.isAssignableFrom(clazz)) {
                    bytes = ((MessageLite) in).toByteArray();
                } else {
                    bytes = ProtostuffUtils.serialize(in);
                }
                out.writeBytes(bytes);
                System.out.println(Arrays.toString(bytes));
                return out;
            }
        };
    }


    private static class ProtostuffUtils {

        /**
         * 避免每次序列化都重新申请Buffer空间
         */
        private static final LinkedBuffer buffer = LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE);

        /**
         * 序列化方法，把指定对象序列化成字节数组 * * @param obj * @param <T> * @return
         */
        @SuppressWarnings("unchecked")
        public static <T> byte[] serialize(T obj) {
            Schema<T> schema = RuntimeSchema.getSchema((Class<T>) obj.getClass());
            byte[] data;
            try {
                data = ProtostuffIOUtil.toByteArray(obj, schema, buffer);
            } finally {
                buffer.clear();
            }

            return data;
        }

        /**
         * 反序列化方法，将字节数组反序列化成指定Class类型 * * @param data * @param clazz * @param <T> * @return
         */
        public static <T> T deserialize(byte[] data, Class<T> clazz) {
            Schema<T> schema = RuntimeSchema.getSchema(clazz);
            T obj = schema.newMessage();
            ProtostuffIOUtil.mergeFrom(data, obj, schema);
            return obj;
        }

    }

}