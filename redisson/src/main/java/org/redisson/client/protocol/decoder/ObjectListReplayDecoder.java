/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import java.util.Collections;
import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> type
 */
public class ObjectListReplayDecoder<T> implements MultiDecoder<List<T>> {

    private final Decoder<Object> decoder;
    private final boolean reverse;

    public ObjectListReplayDecoder() {
        this(false);
    }

    public ObjectListReplayDecoder(boolean reverse) {
        this(reverse, null);
    }

    public ObjectListReplayDecoder(boolean reverse, Decoder<Object> decoder) {
        super();
        this.reverse = reverse;
        this.decoder = decoder;
    }

    @Override
    public List<T> decode(List<Object> parts, State state) {
        if (reverse) {
            Collections.reverse(parts);
        }
        return (List<T>) parts;
    }

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state) {
        if (decoder != null) {
            return decoder;
        }
        return MultiDecoder.super.getDecoder(codec, paramNum, state);
    }
}
