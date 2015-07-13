package org.redisson.client.protocol.decoder;

import org.redisson.client.protocol.Decoder;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class BooleanReplayDecoder2 implements Decoder<Boolean> {

    @Override
    public Boolean decode(ByteBuf buf) {
        if (buf == null) {
            return false;
        }
        return "OK".equals(buf.toString(CharsetUtil.UTF_8));
    }

}
