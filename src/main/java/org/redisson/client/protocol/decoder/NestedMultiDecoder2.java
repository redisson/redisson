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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.redisson.client.handler.State;

import io.netty.buffer.ByteBuf;

public class NestedMultiDecoder2<T> implements MultiDecoder<Object> {

    private final MultiDecoder<Object> firstDecoder;
    private final MultiDecoder<Object> secondDecoder;

    public NestedMultiDecoder2(MultiDecoder<Object> firstDecoder, MultiDecoder<Object> secondDecoder) {
        this.firstDecoder = firstDecoder;
        this.secondDecoder = secondDecoder;
    }

    @Override
    public Object decode(ByteBuf buf, State state) throws IOException {
        return firstDecoder.decode(buf, state);
    }

    @Override
    public boolean isApplicable(int paramNum, State state) {
        if (paramNum == 0) {
            setCounter(state, 0);
        }
        return firstDecoder.isApplicable(paramNum, state);
    }

    private Integer getCounter(State state) {
        Integer value = state.getDecoderState();
        if (value == null) {
            return 0;
        }
        return value;
    }

    private void setCounter(State state, Integer value) {
        state.setDecoderState(value);
    }


    @Override
    public Object decode(List<Object> parts, State state) {
        // handle empty result
        if (parts.isEmpty() && state.getDecoderState() == null) {
            return secondDecoder.decode(parts, state);
        }

        int counter = getCounter(state);
        if (counter == 2) {
            counter = 0;
        }
        counter++;
        setCounter(state, counter);
        MultiDecoder<?> decoder = null;
        if (counter == 1) {
            decoder = firstDecoder;
        }
        if (counter == 2) {
            decoder = secondDecoder;
        }
        return decoder.decode(parts, state);
    }

}
