package org.redisson.codec;

import java.nio.ByteBuffer;

public interface RedissonCodec {

    /**
     * Decode the key output by redis.
     *
     * @param bytes Raw bytes of the key.
     *
     * @return The decoded key.
     */
    public abstract Object decodeKey(ByteBuffer bytes);

    /**
     * Decode the value output by redis.
     *
     * @param bytes Raw bytes of the value.
     *
     * @return The decoded value.
     */
    public abstract Object decodeValue(ByteBuffer bytes);

    /**
     * Encode the key for output to redis.
     *
     * @param key Key.
     *
     * @return The encoded key.
     */
    public abstract byte[] encodeKey(Object key);

    /**
     * Encode the value for output to redis.
     *
     * @param value Value.
     *
     * @return The encoded value.
     */
    public abstract byte[] encodeValue(Object value);

}
