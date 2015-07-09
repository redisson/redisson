package org.redisson.client.protocol.pubsub;

import java.util.List;

import org.redisson.client.protocol.Decoder;

public interface MultiDecoder<T> extends Decoder<Object> {

    T decode(List<Object> parts);

}
