/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ObjectMapReplayDecoder<K, V> implements MultiDecoder<Map<K, V>> {

    private boolean swapKeyValue;
    private final Codec codec;

    public ObjectMapReplayDecoder(Codec codec) {
        this.codec = codec;
    }

    public ObjectMapReplayDecoder() {
        this(null);
    }

    public ObjectMapReplayDecoder(boolean swapKeyValue, Codec codec) {
        this.swapKeyValue = swapKeyValue;
        this.codec = codec;
    }

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state, long size) {
        Codec c = Optional.ofNullable(this.codec).orElse(codec);
        if (paramNum % 2 != 0) {
            return c.getMapValueDecoder();
        }
        return c.getMapKeyDecoder();
    }

    @Override
    public Map<K, V> decode(List<Object> parts, State state) {
        Map<K, V> result = MultiDecoder.newLinkedHashMap(parts.size()/2);
        for (int i = 0; i < parts.size(); i++) {
            if (i % 2 != 0) {
                if (swapKeyValue) {
                    result.put((K) parts.get(i), (V) parts.get(i-1));
                } else {
                    result.put((K) parts.get(i-1), (V) parts.get(i));
                }
            }
        }
        return result;
    }

}
