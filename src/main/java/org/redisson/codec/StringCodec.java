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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class StringCodec implements RedissonCodec {

    private Charset charset = Charset.forName("UTF-8");

    @Override
    public Object decodeKey(ByteBuffer bytes) {
        return decodeValue(bytes);
    }

    @Override
    public Object decodeValue(ByteBuffer bytes) {
        return new String(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.limit(), charset);
    }

    @Override
    public byte[] encodeKey(Object key) {
        return encodeValue((String)key);
    }

    @Override
    public byte[] encodeValue(Object value) {
        return ((String)value).getBytes(charset);
    }

    @Override
    public byte[] encodeMapValue(Object value) {
        return encodeValue((String)value);
    }

    @Override
    public byte[] encodeMapKey(Object key) {
        return encodeValue((String)key);
    }

    @Override
    public Object decodeMapValue(ByteBuffer bytes) {
        return decodeValue(bytes);
    }

    @Override
    public Object decodeMapKey(ByteBuffer bytes) {
        return decodeValue(bytes);
    }

}
