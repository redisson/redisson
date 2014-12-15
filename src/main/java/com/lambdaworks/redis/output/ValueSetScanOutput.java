package com.lambdaworks.redis.output;

import java.nio.ByteBuffer;

import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.protocol.CommandOutput;

public class ValueSetScanOutput<K, V> extends CommandOutput<K, V, ListScanResult<V>> {

    public ValueSetScanOutput(RedisCodec<K, V> codec) {
        super(codec, new ListScanResult<V>());
    }

    @Override
    public void set(ByteBuffer bytes) {
        if (output.getPos() == null) {
            output.setPos(((Number) codec.decodeMapValue(bytes)).longValue());
        } else {
            output.addValue(codec.decodeMapValue(bytes));
        }
    }

}
