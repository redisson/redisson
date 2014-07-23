package com.lambdaworks.redis.output;

import java.nio.ByteBuffer;

import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.protocol.CommandOutput;

public class MapScanOutput<K, V> extends CommandOutput<K, V, MapScanResult<K, V>> {

    int counter;
    
    public MapScanOutput(RedisCodec<K, V> codec) {
        super(codec, new MapScanResult<K, V>());
    }
    
    @Override
    public void set(ByteBuffer bytes) {
        if (output.getPos() == null) {
            output.setPos(((Number) codec.decodeValue(bytes)).longValue());
        } else {
            if (counter % 2 == 0) {
                output.addValue(codec.decodeMapValue(bytes));
            } else {
                output.addKey(codec.decodeMapKey(bytes));
            }
        }
        counter++;
    }

}
