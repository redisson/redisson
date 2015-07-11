package org.redisson.client.protocol.decoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.redisson.client.protocol.pubsub.MultiDecoder;

import io.netty.buffer.ByteBuf;

public class ObjectMapReplayDecoder implements MultiDecoder<Map<Object, Object>> {

    @Override
    public Object decode(ByteBuf buf) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<Object, Object> decode(List<Object> parts) {
        Map<Object, Object> result = new HashMap<Object, Object>(parts.size()/2);
        for (int i = 0; i < parts.size(); i++) {
            if (i % 2 != 0) {
                result.put(parts.get(i-1).toString(), parts.get(i).toString());
           }
        }
        return result;
    }

    @Override
    public boolean isApplicable(int paramNum) {
        return false;
    }

}
