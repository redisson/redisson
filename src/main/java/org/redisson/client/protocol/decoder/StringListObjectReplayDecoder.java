package org.redisson.client.protocol.decoder;

import java.util.List;

import org.redisson.client.protocol.pubsub.MultiDecoder;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class StringListObjectReplayDecoder implements MultiDecoder<Object> {

    @Override
    public Object decode(ByteBuf buf) {
        return buf.toString(CharsetUtil.UTF_8);
    }

    @Override
    public Object decode(List<Object> parts) {
        return parts;
    }

}
