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

import org.redisson.api.vector.QuantizationType;
import org.redisson.api.vector.VectorInfo;
import org.redisson.client.handler.State;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class VectorInfoDecoder implements MultiDecoder<VectorInfo> {

    @Override
    public VectorInfo decode(List<Object> parts, State state) {
        Map<String, Object> map = IntStream.range(0, parts.size())
                                    .filter(i -> i % 2 == 0)
                                    .mapToObj(i -> parts.subList(i, i+2))
                                    .filter(p -> p.get(1) != null)
                                    .collect(Collectors.toMap(e -> (String) e.get(0), e -> e.get(1)));

        VectorInfo info = new VectorInfo();
        info.setDimensions((Long) map.get("vector-dim"));
        info.setAttributesCount((Long) map.get("attributes-count"));
        info.setMaxConnections(((Long) map.get("hnsw-m")).intValue());
        String qt = (String) map.get("quant-type");
        if ("int8".equals(qt)) {
            info.setQuantizationType(QuantizationType.Q8);
        } else if ("bin".equals(qt)) {
            info.setQuantizationType(QuantizationType.BIN);
        } else if ("f32".equals(qt)) {
            info.setQuantizationType(QuantizationType.NOQUANT);
        }
        info.setSize((Long) map.get("size"));
        return info;
    }

}
