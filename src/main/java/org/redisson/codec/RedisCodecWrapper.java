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

import com.lambdaworks.redis.codec.RedisCodec;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedisCodecWrapper extends RedisCodec<Object, Object> {

    private final RedissonCodec redissonCodec;

    public RedisCodecWrapper(RedissonCodec redissonCodec) {
        this.redissonCodec = redissonCodec;
    }

    @Override
    public Object decodeKey(ByteBuffer bytes) {
        return redissonCodec.decodeKey(bytes);
    }

    @Override
    public Object decodeValue(ByteBuffer bytes) {
        return redissonCodec.decodeValue(bytes);
    }

    @Override
    public byte[] encodeKey(Object key) {
        return redissonCodec.encodeKey(key);
    }

    @Override
    public byte[] encodeValue(Object value) {
        return redissonCodec.encodeValue(value);
    }



}
