package org.redisson.client.protocol.decoder;

import java.util.List;
import java.util.Map;

import com.lambdaworks.redis.output.MapScanResult;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class MapScanResultReplayDecoder implements MultiDecoder<MapScanResult<Object, Object>> {

    ThreadLocal<MultiDecoder<?>> currentMultiDecoder = new ThreadLocal<MultiDecoder<?>>();
    ThreadLocal<Boolean> posParsed = new ThreadLocal<Boolean>();
    ObjectMapReplayDecoder nextDecoder = new ObjectMapReplayDecoder();

    public MultiDecoder<?> get() {
        if (currentMultiDecoder.get() == null) {
            currentMultiDecoder.set(nextDecoder);
            return nextDecoder;
        }
        return (MultiDecoder<?>) this;
    }

    @Override
    public Object decode(ByteBuf buf) {
        posParsed.set(true);
        return Long.valueOf(buf.toString(CharsetUtil.UTF_8));
    }

    @Override
    public MapScanResult<Object, Object> decode(List<Object> parts) {
        return new MapScanResult<Object, Object>((Long)parts.get(0), (Map<Object, Object>)parts.get(1));
    }

    @Override
    public boolean isApplicable(int paramNum) {
        return paramNum == 0 && posParsed.get() == null;
    }

}
