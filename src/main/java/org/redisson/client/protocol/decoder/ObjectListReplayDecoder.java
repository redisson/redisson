package org.redisson.client.protocol.decoder;

import java.util.List;

import io.netty.buffer.ByteBuf;

public class ObjectListReplayDecoder implements MultiDecoder<List<Object>> {

    @Override
    public Object decode(ByteBuf buf) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Object> decode(List<Object> parts) {
        return parts;
    }

    @Override
    public boolean isApplicable(int paramNum) {
        return false;
    }

    @Override
    public MultiDecoder<?> get() {
        return this;
    }

}
