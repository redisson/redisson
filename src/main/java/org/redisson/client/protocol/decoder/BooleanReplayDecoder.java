package org.redisson.client.protocol.decoder;

import org.redisson.client.protocol.Decoder;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class BooleanReplayDecoder implements Decoder<Boolean> {

    @Override
    public Boolean decode(ByteBuf buf) {
        String status = buf.readBytes(buf.bytesBefore((byte) '\r')).toString(CharsetUtil.UTF_8);
        buf.skipBytes(2);
        return "OK".equals(status);
    }

}
