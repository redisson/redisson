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

import com.lambdaworks.redis.codec.Utf8StringCodec;

public class StringCodec implements RedissonCodec {

    private final Utf8StringCodec codec = new Utf8StringCodec();

    @Override
    public Object decodeKey(ByteBuffer bytes) {
        return codec.decodeKey(bytes);
    }

    @Override
    public Object decodeValue(ByteBuffer bytes) {
        return codec.decodeValue(bytes);
    }

    @Override
    public byte[] encodeKey(Object key) {
        return codec.encodeKey((String)key);
    }

    @Override
    public byte[] encodeValue(Object value) {
        return codec.encodeValue((String)value);
    }

    @Override
    public byte[] encodeMapValue(Object value) {
        return codec.encodeMapValue((String)value);
    }

    @Override
    public byte[] encodeMapKey(Object key) {
        return codec.encodeMapKey((String)key);
    }

    @Override
    public Object decodeMapValue(ByteBuffer bytes) {
        return codec.decodeMapValue(bytes);
    }

    @Override
    public Object decodeMapKey(ByteBuffer bytes) {
        return codec.decodeMapKey(bytes);
    }

}
