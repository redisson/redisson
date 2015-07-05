package org.redisson.client.protocol;

public interface Encoder {

    byte[] encode(int paramIndex, Object in);

}
