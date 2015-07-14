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

    private Deque<MultiDecoder<?>> iterator;
    private Deque<MultiDecoder<?>> flipIterator;

    public NestedMultiDecoder(MultiDecoder<Object> firstDecoder, MultiDecoder<Object> secondDecoder) {
        this.firstDecoder = firstDecoder;
        this.secondDecoder = secondDecoder;

        init(firstDecoder, secondDecoder);
    }

    private void init(MultiDecoder<Object> firstDecoder, MultiDecoder<Object> secondDecoder) {
        iterator = new ArrayDeque<MultiDecoder<?>>(Arrays.asList(firstDecoder, secondDecoder));
        flipIterator = new ArrayDeque<MultiDecoder<?>>(Arrays.asList(firstDecoder, secondDecoder, firstDecoder));
    }

    @Override
    public Object decode(ByteBuf buf) throws IOException {
        return flipIterator.peek().decode(buf);
    }

    @Override
    public MultiDecoder<?> get() {
        return this;
    }

    @Override
    public boolean isApplicable(int paramNum) {
        if (paramNum == 0) {
            flipIterator.poll();
            if (flipIterator.isEmpty()) {
                init(firstDecoder, secondDecoder);
                flipIterator.poll();
            }
        }
        return flipIterator.peek().isApplicable(paramNum);
    }

    @Override
    public Object decode(List<Object> parts) {
        return iterator.poll().decode(parts);
    }

}
