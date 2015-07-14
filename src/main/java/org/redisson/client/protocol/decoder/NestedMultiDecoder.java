package org.redisson.client.protocol.decoder;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import io.netty.buffer.ByteBuf;

public class NestedMultiDecoder<T> implements MultiDecoder<Object> {

    private final MultiDecoder<Object> firstDecoder;
    private final MultiDecoder<Object> secondDecoder;

    private ThreadLocal<Deque<MultiDecoder<?>>> decoders = new ThreadLocal<Deque<MultiDecoder<?>>>() {
        protected Deque<MultiDecoder<?>> initialValue() {
            return new ArrayDeque<MultiDecoder<?>>(Arrays.asList(firstDecoder, secondDecoder));
        };
    };

    private ThreadLocal<Deque<MultiDecoder<?>>> flipDecoders = new ThreadLocal<Deque<MultiDecoder<?>>>() {
        protected Deque<MultiDecoder<?>> initialValue() {
            return new ArrayDeque<MultiDecoder<?>>(Arrays.asList(firstDecoder, secondDecoder, firstDecoder));
        };
    };

    public NestedMultiDecoder(MultiDecoder<Object> firstDecoder, MultiDecoder<Object> secondDecoder) {
        this.firstDecoder = firstDecoder;
        this.secondDecoder = secondDecoder;
    }

    @Override
    public Object decode(ByteBuf buf) throws IOException {
        return flipDecoders.get().peek().decode(buf);
    }

    @Override
    public boolean isApplicable(int paramNum) {
        if (paramNum == 0) {
            flipDecoders.get().poll();
            // in case of incoming buffer tail
            // state should be reseted
            if (flipDecoders.get().isEmpty()) {
                flipDecoders.remove();
                decoders.remove();

                flipDecoders.get().poll();
            }
        }
        return flipDecoders.get().peek().isApplicable(paramNum);
    }

    @Override
    public Object decode(List<Object> parts) {
        Object result = decoders.get().poll().decode(parts);
        // clear state on last decoding
        if (decoders.get().isEmpty()) {
            flipDecoders.remove();
            decoders.remove();
        }
        return result;
    }

}
