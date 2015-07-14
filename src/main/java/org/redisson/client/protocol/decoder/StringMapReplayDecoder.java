package org.redisson.client.protocol.decoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class StringMapReplayDecoder implements MultiDecoder<List<Map<String, String>>> {

    @Override
    public Object decode(ByteBuf buf) {
        return buf.toString(CharsetUtil.UTF_8);
    }

    @Override
    public List<Map<String, String>> decode(List<Object> parts) {
        // TODO refactor
        if (!parts.isEmpty()) {
            if (parts.get(0) instanceof List) {
                List<Map<String, String>> result = new ArrayList<Map<String, String>>(parts.size());
                for (Object object : parts) {
                    List<Map<String, String>> list = (List<Map<String, String>>) object;
                    result.addAll(list);
                }
                return result;
            }
        }

        Map<String, String> result = new HashMap<String, String>(parts.size()/2);
        for (int i = 0; i < parts.size(); i++) {
            if (i % 2 != 0) {
                result.put(parts.get(i-1).toString(), parts.get(i).toString());
           }
        }
        return Collections.singletonList(result);
    }

    @Override
    public boolean isApplicable(int paramNum) {
        return true;
    }

}
