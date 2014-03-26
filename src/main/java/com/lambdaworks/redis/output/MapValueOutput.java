// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis.output;

import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.protocol.CommandOutput;

import java.nio.ByteBuffer;

/**
 * Value output.
 *
 * @param <V> Value type.
 *
 * @author Will Glozer
 */
public class MapValueOutput<K, V> extends CommandOutput<K, V, V> {
    public MapValueOutput(RedisCodec<K, V> codec) {
        super(codec, null);
    }

    @Override
    public void set(ByteBuffer bytes) {
        output = (bytes == null) ? null : codec.decodeMapValue(bytes);
    }
}
