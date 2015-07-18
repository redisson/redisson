/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.client.protocol.decoder;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import org.redisson.client.handler.State;

import io.netty.buffer.ByteBuf;

public class NestedMultiDecoder<T> implements MultiDecoder<Object> {

    public static class DecoderState {

        Deque<MultiDecoder<?>> decoders;

        Deque<MultiDecoder<?>> flipDecoders;

        public DecoderState(MultiDecoder<Object> firstDecoder, MultiDecoder<Object> secondDecoder) {
            super();
            this.decoders = new ArrayDeque<MultiDecoder<?>>(Arrays.asList(firstDecoder, secondDecoder));
            this.flipDecoders = new ArrayDeque<MultiDecoder<?>>(Arrays.asList(firstDecoder, secondDecoder, firstDecoder));
        }

        public Deque<MultiDecoder<?>> getDecoders() {
            return decoders;
        }

        public Deque<MultiDecoder<?>> getFlipDecoders() {
            return flipDecoders;
        }

    }

    private final MultiDecoder<Object> firstDecoder;
    private final MultiDecoder<Object> secondDecoder;

    public NestedMultiDecoder(MultiDecoder<Object> firstDecoder, MultiDecoder<Object> secondDecoder) {
        this.firstDecoder = firstDecoder;
        this.secondDecoder = secondDecoder;
    }

    @Override
    public Object decode(ByteBuf buf, State state) throws IOException {
        DecoderState ds = getDecoder(state);
        return ds.getFlipDecoders().peek().decode(buf, state);
    }

    @Override
    public boolean isApplicable(int paramNum, State state) {
        DecoderState ds = getDecoder(state);
        if (paramNum == 0) {
            ds.getFlipDecoders().poll();
        }
        return ds.getFlipDecoders().peek().isApplicable(paramNum, state);
    }

    private DecoderState getDecoder(State state) {
        DecoderState ds = state.getDecoderState();
        if (ds == null) {
            ds = new DecoderState(firstDecoder, secondDecoder);
            state.setDecoderState(ds);
        }
        return ds;
    }

    @Override
    public Object decode(List<Object> parts, State state) {
        DecoderState ds = getDecoder(state);
        return ds.getDecoders().poll().decode(parts, state);
    }

}
