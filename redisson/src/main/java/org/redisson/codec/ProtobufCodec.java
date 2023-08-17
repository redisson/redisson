package org.redisson.codec;

import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ProtobufCodec extends BaseCodec {
    private final Class<?> mapKeyClass;
    private final Class<?> mapValueClass;
    private final Class<?> valueClass;

    //class in blacklist will not be serialized using protobuf ,but instead will use jackson
    private final Set<Class<?>> blacklist = new HashSet<>();

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
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                //todo 这里是否不该用MessageLite，而是使用存在parseFrom方法的类？
                if (MessageLite.class.isAssignableFrom(clazz)) {
                    try {
                        final Method parseFrom = clazz.getDeclaredMethod("parseFrom", byte[].class);
                        return parseFrom.invoke(clazz, bytes);
                    } catch (Exception e) {
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
            public ByteBuf encode(Object in) {
                ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
                byte[] bytes;
                //todo 缓存下来
                //todo String类型的key要使用proto编码吗
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