package org.redisson.client.protocol;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class PubSubMessageDecoder implements MultiDecoder<Object> {

    @Override
    public Object decode(ByteBuf buf) {
        String status = buf.toString(CharsetUtil.UTF_8);
        buf.skipBytes(2);
        return status;
    }

    @Override
    public PubSubMessage decode(List<Object> parts) {
        return new PubSubMessage(parts.get(1).toString(), parts.get(2).toString());
    }

}
