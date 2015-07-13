package org.redisson.client.protocol.decoder;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class KeyValueObjectDecoder implements MultiDecoder<Object> {

    public MultiDecoder<Object> get() {
        return (MultiDecoder<Object>) this;
    }

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
