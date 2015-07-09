package org.redisson.client.protocol;

import java.io.UnsupportedEncodingException;

public class StringParamsEncoder implements Encoder {

    @Override
    public byte[] encode(int paramIndex, Object in) {
        try {
            return in.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

}
