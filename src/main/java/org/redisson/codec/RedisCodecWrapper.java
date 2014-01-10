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
