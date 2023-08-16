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
import java.util.Arrays;

public class ProtobufCodec extends BaseCodec {
    final Class<?> t;

    public ProtobufCodec(Class<?> t) {
        this.t = t;
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return new Decoder<Object>() {
            @Override
            public Object decode(ByteBuf buf, State state) throws IOException {
                final int i = buf.readableBytes();
                byte[] bytes = new byte[i];
                buf.readBytes(bytes);
                return ProtostuffUtils.deserialize(bytes, t);
            }
        };
    }

    @Override
    public Encoder getValueEncoder() {
        return new Encoder() {
            @Override
            public ByteBuf encode(Object in) {
                ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
                byte[] bytes;
                if (in instanceof MessageLite) {
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