// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis.output;

import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.protocol.CommandOutput;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * 64-bit integer output, may be null.
 *
 * @author Will Glozer
 */
public class IntegerOutput<K, V> extends CommandOutput<K, V, Long> {
    public IntegerOutput(RedisCodec<K, V> codec) {
        super(codec, null);
    }

    @Override
    public void set(long integer) {
        output = integer;
    }

    @Override
    public void set(ByteBuffer bytes) {
        output = bytes == null ? null : new Long(decodeAscii(bytes));
    }
}
