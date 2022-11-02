package org.redisson.client.protocol.decoder;

import org.redisson.client.handler.State;

import java.util.List;

public class StringListListReplayDecoder extends StringListReplayDecoder {

    @Override
    public List<String> decode(List<Object> parts, State state) {
        for (Object part : parts) {
            if (part instanceof List) {
                return (List<String>) (Object) parts;
            }
        }
        return super.decode(parts, state);
    }

}
