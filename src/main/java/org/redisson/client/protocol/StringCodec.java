package org.redisson.client.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class StringCodec implements Codec {

    @Override
    public Object encode(Object in) {
        return in.toString();
    }

    @Override
    public Object decode(ByteBuf buf) {
        return buf.toString(CharsetUtil.UTF_8);
    }

}
