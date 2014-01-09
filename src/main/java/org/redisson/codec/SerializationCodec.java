package org.redisson.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

public class SerializationCodec implements RedissonCodec {

    @Override
    public Object decodeKey(ByteBuffer bytes) {
        return decode(bytes);
    }

    @Override
    public Object decodeValue(ByteBuffer bytes) {
        return decode(bytes);
    }

    private Object decode(ByteBuffer bytes) {
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.limit());
            ObjectInputStream inputStream = new ObjectInputStream(in);
            return inputStream.readObject();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public byte[] encodeKey(Object key) {
        return encodeValue(key);
    }

    @Override
    public byte[] encodeValue(Object value) {
        try {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            ObjectOutputStream outputStream = new ObjectOutputStream(result);
            outputStream.writeObject(value);
            outputStream.close();
            return result.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
