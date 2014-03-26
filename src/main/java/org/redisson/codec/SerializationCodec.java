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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

/**
 *
 * @author Nikita Koksharov
 *
 */
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

    @Override
    public byte[] encodeMapValue(Object value) {
        return encodeValue(value);
    }

    @Override
    public byte[] encodeMapKey(Object key) {
        return encodeKey(key);
    }

    @Override
    public Object decodeMapValue(ByteBuffer bytes) {
        return decodeValue(bytes);
    }

    @Override
    public Object decodeMapKey(ByteBuffer bytes) {
        return decodeKey(bytes);
    }

}
