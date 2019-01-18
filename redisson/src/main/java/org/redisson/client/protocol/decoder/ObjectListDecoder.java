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
package org.redisson.client.protocol.decoder;

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
public class ObjectListDecoder<T> implements MultiDecoder<List<T>> {

    private Codec codec;
    
    public ObjectListDecoder(Codec codec) {
        super();
        this.codec = codec;
    }
    
    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        return codec.getMapKeyDecoder();
    }

    @Override
    public List<T> decode(List<Object> parts, State state) {
        return (List<T>) parts;
    }

}
