/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
package org.redisson.spring.data.connection;

import java.util.List;

import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.decoder.MultiDecoder;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> type
 */
public class ObjectListReplayDecoder2<T> implements MultiDecoder<List<T>> {

    private final Decoder<Object> decoder;
    
    public ObjectListReplayDecoder2() {
        this(null);
    }
    
    public ObjectListReplayDecoder2(Decoder<Object> decoder) {
        super();
        this.decoder = decoder;
    }

    @Override
    public List<T> decode(List<Object> parts, State state) {
        for (int i = 0; i < parts.size(); i++) {
            Object object = parts.get(i);
            if (object instanceof List) {
                if (((List) object).isEmpty()) {
                    parts.set(i, null);
                }
            }
        }
        return (List<T>) parts;
    }

    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        return decoder;
    }
}
