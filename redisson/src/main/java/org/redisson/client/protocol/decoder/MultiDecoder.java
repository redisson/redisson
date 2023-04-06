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

import java.util.LinkedHashMap;
import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> type
 */
public interface MultiDecoder<T> {

    default Decoder<Object> getDecoder(Codec codec, int paramNum, State state) {
        if (codec == null) {
            codec = StringCodec.INSTANCE;
        }
        return codec.getValueDecoder();
    }
    
    T decode(List<Object> parts, State state);

    static <K, V> LinkedHashMap<K, V> newLinkedHashMap(int expectedSize) {
        if (expectedSize < 3) {
            return new LinkedHashMap<>(expectedSize + 1);
        }
        return new LinkedHashMap<>((int) Math.ceil(expectedSize / 0.75));
    }

}
