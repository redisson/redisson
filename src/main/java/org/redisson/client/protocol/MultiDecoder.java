package org.redisson.client.protocol;

import java.util.List;

public interface MultiDecoder<T> extends Decoder<Object> {

    T decode(List<Object> parts);

}
