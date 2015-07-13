package org.redisson.client.protocol.decoder;

import java.util.Arrays;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class StringListReplayDecoder implements MultiDecoder<List<String>> {

    @Override
    public MultiDecoder<?> get() {
        return this;
    }

    @Override
    public Object decode(ByteBuf buf) {
        return buf.toString(CharsetUtil.UTF_8);
    }

    @Override
    public List<String> decode(List<Object> parts) {
        return Arrays.asList(Arrays.copyOf(parts.toArray(), parts.size(), String[].class));
    }

    @Override
    public boolean isApplicable(int paramNum) {
        return true;
    }

}
