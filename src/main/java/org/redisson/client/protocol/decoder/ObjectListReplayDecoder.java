package org.redisson.client.protocol.decoder;

import java.util.List;

import org.redisson.client.protocol.pubsub.MultiDecoder;

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

}
