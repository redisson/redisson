package org.redisson.client.protocol;

import java.util.List;

import org.redisson.client.protocol.pubsub.MultiDecoder;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class KeyValueObjectDecoder implements MultiDecoder<Object> {

    @Override
    public Object decode(ByteBuf buf) {
        String status = buf.toString(CharsetUtil.UTF_8);
        buf.skipBytes(2);
        return status;
    }

    @Override
    public Object decode(List<Object> parts) {
        return new KeyValueMessage(parts.get(0), parts.get(1));
    }

    @Override
    public boolean isApplicable(int paramNum) {
        return paramNum == 0;
    }

}
