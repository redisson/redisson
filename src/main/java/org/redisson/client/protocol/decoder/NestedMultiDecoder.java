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
