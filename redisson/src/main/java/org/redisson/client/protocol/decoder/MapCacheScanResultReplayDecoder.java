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

import java.util.List;
import java.util.Map;

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class MapCacheScanResultReplayDecoder implements MultiDecoder<MapCacheScanResult<Object, Object>> {

    @Override
    public MapCacheScanResult<Object, Object> decode(List<Object> parts, State state) {
        Long pos = (Long) parts.get(0);
        Map<Object, Object> values = (Map<Object, Object>) parts.get(1);
        List<Object> idleKeys = (List<Object>) parts.get(2);
        return new MapCacheScanResult<Object, Object>(pos, values, idleKeys);
    }

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state) {
        return LongCodec.INSTANCE.getValueDecoder();
    }

}
