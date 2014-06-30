package com.lambdaworks.redis.output;

import java.nio.ByteBuffer;

import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.protocol.CommandOutput;

public class ScanOutput<K, V> extends CommandOutput<K, V, ScanResult<V>> {

    public ScanOutput(RedisCodec<K, V> codec) {
        super(codec, new ScanResult<V>());
    }
    
    @Override
    public void set(ByteBuffer bytes) {
        if (output.getPos() == null) {
            output.setPos(((Number) codec.decodeValue(bytes)).longValue());
        } else {
            output.addValue(codec.decodeValue(bytes));
        }
    }

}
