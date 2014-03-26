// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis.codec;

import java.nio.ByteBuffer;

/**
 * A RedisCodec encodes keys and values sent to redis, and decodes keys
 * and values in the command output.
 *
 * The encode methods will be called by multiple threads and must be thread-safe,
 * however the decode methods will only be called by one thread.
 *
 * @param <K> Key type.
 * @param <V> Value type.
 *
 * @author Will Glozer
 */
public abstract class RedisCodec<K, V> {
    /**
     * Decode the key output by redis.
     *
     * @param bytes Raw bytes of the key.
     *
     * @return The decoded key.
     */
    public abstract K decodeKey(ByteBuffer bytes);

    /**
     * Decode the value output by redis.
     *
     * @param bytes Raw bytes of the value.
     *
     * @return The decoded value.
     */
    public abstract V decodeValue(ByteBuffer bytes);

    /**
     * Encode the key for output to redis.
     *
     * @param key Key.
     *
     * @return The encoded key.
     */
    public abstract byte[] encodeKey(K key);

    /**
     * Encode the value for output to redis.
     *
     * @param value Value.
     *
     * @return The encoded value.
     */
    public abstract byte[] encodeValue(V value);


    public abstract byte[] encodeMapValue(V value);

    public abstract byte[] encodeMapKey(K key);

    public abstract V decodeMapValue(ByteBuffer bytes);

    public abstract K decodeMapKey(ByteBuffer bytes);


}
