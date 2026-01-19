/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
import org.redisson.api.bloomfilter.BloomFilterInfo;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

/**
 * BloomFilter info decoder
 *
 * @author Su Ko
 *
 */
public class BloomFilterInfoDecoder implements MultiDecoder<BloomFilterInfo> {

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state, long size) {
        return LongCodec.INSTANCE.getValueDecoder();
    }

    @Override
    public BloomFilterInfo decode(List<Object> parts, State state) {
        Long capacity = null;
        Long size = null;
        Long subFilterCount = null;
        Long itemCount = null;
        Long expansionRate = null;

        for (int i = 0; i < parts.size(); i += 2) {
            String key = (String) parts.get(i);
            Long value = (Long) parts.get(i + 1);

            if (key.toLowerCase().contains("capacity")){
                capacity = value;
            } else if (key.toLowerCase().contains("size")){
                size = value;
            } else if (key.toLowerCase().contains("number of filters")){
                subFilterCount = value;
            } else if (key.toLowerCase().contains("number of items inserted")){
                itemCount = value;
            } else if (key.toLowerCase().contains("expansion rate")){
                expansionRate = value;
            }
        }

        return new BloomFilterInfo(capacity, size, subFilterCount, itemCount, expansionRate);
    }

}
