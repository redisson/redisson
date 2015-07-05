package org.redisson.client.protocol;

import java.io.UnsupportedEncodingException;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class StringCodec implements Codec {

    @Override
    public byte[] encode(int paramIndex, Object in) {
        try {
            return in.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Object decode(ByteBuf buf) {
        return buf.toString(CharsetUtil.UTF_8);
    }

}
