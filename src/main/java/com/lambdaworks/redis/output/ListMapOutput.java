// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis.output;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.protocol.CommandOutput;

public class ListMapOutput<K, V> extends CommandOutput<K, V, List<Map<K, V>>> {
    private K key;
    private int index = 0;

    public ListMapOutput(RedisCodec<K, V> codec) {
        super(codec, new ArrayList<Map<K, V>>());
    }

    @Override
    public void set(ByteBuffer bytes) {
        if (key == null) {
            key = codec.decodeMapKey(bytes);
            return;
        }

        V value = (bytes == null) ? null : codec.decodeMapValue(bytes);
        if (output.isEmpty()) {
            output.add(new HashMap<K, V>());
        }
        Map<K, V> map = output.get(index);
        if (map == null) {
            map = new HashMap<K, V>();
            output.add(map);
        }
        if (map.get(key) != null) {
            index++;
            map = new HashMap<K, V>();
            output.add(map);
        }
        map.put(key, value);
        key = null;
    }
}
