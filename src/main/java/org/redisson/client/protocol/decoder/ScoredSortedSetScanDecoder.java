package org.redisson.client.protocol.decoder;

import java.math.BigDecimal;

import org.redisson.client.handler.State;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class ScoredSortedSetScanDecoder<T> extends ObjectListReplayDecoder<T> {

    @Override
    public Object decode(ByteBuf buf, State state) {
        return new BigDecimal(buf.toString(CharsetUtil.UTF_8));
    }

    @Override
    public boolean isApplicable(int paramNum, State state) {
        return paramNum % 2 != 0;
    }

}
