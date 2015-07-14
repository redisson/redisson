package org.redisson.client.protocol.decoder;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class ListScanResultReplayDecoder implements MultiDecoder<ListScanResult<Object>> {

    @Override
    public Object decode(ByteBuf buf) {
        return Long.valueOf(buf.toString(CharsetUtil.UTF_8));
    }

    @Override
    public ListScanResult<Object> decode(List<Object> parts) {
        return new ListScanResult<Object>((Long)parts.get(0), (List<Object>)parts.get(1));
    }

    @Override
    public boolean isApplicable(int paramNum) {
        return paramNum == 0;
    }

}
