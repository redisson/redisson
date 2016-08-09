/**
 * Copyright 2016 Nikita Koksharov
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
import java.util.List;

import org.redisson.client.handler.State;

import io.netty.buffer.ByteBuf;

public class ListMultiDecoder<T> implements MultiDecoder<Object> {

    private final MultiDecoder<?>[] decoders;
    
    public static class NestedDecoderState implements DecoderState {

        int index = -1;
        int partsIndex = -1;
        
        public NestedDecoderState() {
        }
        
        public NestedDecoderState(int index) {
            super();
            this.index = index;
        }

        public void resetPartsIndex() {
            partsIndex = -1;
        }
        
        public int incPartsIndex() {
            return ++partsIndex;
        }
        
        public int getPartsIndex() {
            return partsIndex;
        }
        
        public int incIndex() {
            return ++index;
        }
        
        public int getIndex() {
            return index;
        }

        @Override
        public DecoderState copy() {
            return new NestedDecoderState(index);
        }

        @Override
        public String toString() {
            return "NestedDecoderState [index=" + index + "]";
        }
        
    }
    
    protected final NestedDecoderState getDecoder(State state) {
        NestedDecoderState ds = state.getDecoderState();
        if (ds == null) {
            ds = new NestedDecoderState();
            state.setDecoderState(ds);
        }
        return ds;
    }
    
    public ListMultiDecoder(MultiDecoder<?> ... decoders) {
        this.decoders = decoders;
    }

    public Object decode(ByteBuf buf, State state) throws IOException {
        int index = getDecoder(state).getIndex();
        return decoders[index].decode(buf, state);
    }

    @Override
    public boolean isApplicable(int paramNum, State state) {
        if (paramNum == 0) {
            NestedDecoderState s = getDecoder(state);
            s.incIndex();
            s.resetPartsIndex();
        }
        return true;
    }

    @Override
    public Object decode(List<Object> parts, State state) {
        NestedDecoderState s = getDecoder(state);
        int index = s.getIndex();
        index += s.incPartsIndex();
        Object res = decoders[index].decode(parts, state);
        if (res == null) {
            index = s.incIndex() + s.getPartsIndex();
            return decoders[index].decode(parts, state);
        }
        return res;
    }
    
}
