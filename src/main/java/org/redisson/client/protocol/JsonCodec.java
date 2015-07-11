package org.redisson.client.protocol;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

public class JsonCodec implements Codec {

    public static final JsonCodec INSTANCE = new JsonCodec();

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public byte[] encode(int paramIndex, Object in) {
        try {
            return mapper.writeValueAsBytes(in);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Object decode(ByteBuf buf) {
        try {
            return mapper.readValue(new ByteBufInputStream(buf), Object.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
