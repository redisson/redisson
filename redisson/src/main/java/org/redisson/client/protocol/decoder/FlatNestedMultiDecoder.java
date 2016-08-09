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

import org.redisson.client.handler.State;

import io.netty.buffer.ByteBuf;

public class FlatNestedMultiDecoder<T> extends NestedMultiDecoder {

    public FlatNestedMultiDecoder(MultiDecoder<Object> firstDecoder, MultiDecoder<Object> secondDecoder) {
        super(firstDecoder, secondDecoder);
    }
    
    public FlatNestedMultiDecoder(MultiDecoder firstDecoder, MultiDecoder secondDecoder, boolean handleEmpty) {
        super(firstDecoder, secondDecoder, handleEmpty);
    }

    @Override
    public Object decode(ByteBuf buf, State state) throws IOException {
        return firstDecoder.decode(buf, state);
    }

    @Override
    public boolean isApplicable(int paramNum, State state) {
        NestedDecoderState ds = getDecoder(state);
        if (paramNum == 0) {
            ds.resetDecoderIndex();
        }
        return firstDecoder.isApplicable(paramNum, state);
    }

}
