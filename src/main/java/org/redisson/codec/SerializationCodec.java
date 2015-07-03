/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class SerializationCodec implements RedissonCodec {

    @Override
    public Object decodeKey(ByteBuffer bytes) {
        return new String(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.limit(), Charset.forName("ASCII"));
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
        return key.toString().getBytes(Charset.forName("ASCII"));
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

    @Override
    public byte[] encodeMapValue(Object value) {
        return encodeValue(value);
    }

    @Override
    public byte[] encodeMapKey(Object key) {
        return encodeValue(key);
    }

    @Override
    public Object decodeMapValue(ByteBuffer bytes) {
        return decodeValue(bytes);
    }

    @Override
    public Object decodeMapKey(ByteBuffer bytes) {
        return decodeValue(bytes);
    }

    protected String decodeAscii(ByteBuffer bytes) {
        if (bytes == null) {
            return null;
        }
        char[] chars = new char[bytes.remaining()];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) bytes.get();
        }
        return new String(chars);
    }

}
