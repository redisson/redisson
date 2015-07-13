package org.redisson.client.protocol.decoder;

import org.redisson.client.protocol.Decoder;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class LongReplayDecoder implements Decoder<Long> {

    @Override
    public Long decode(ByteBuf buf) {
        if (buf == null) {
            return 0L;
        }
        return Long.valueOf(buf.toString(CharsetUtil.UTF_8));
    }

}
