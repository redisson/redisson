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

import org.redisson.api.search.aggregate.AggregationResult;
import org.redisson.client.handler.State;

import java.util.*;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class AggregationCursorResultDecoderV2 implements MultiDecoder<Object> {

    @Override
    public Object decode(List<Object> parts, State state) {
        if (parts.isEmpty()) {
            return new AggregationResult(0, Collections.emptyList(), -1);
        }

        List<Object> attrs = (List<Object>) parts.get(0);
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < attrs.size(); i++) {
            if (i % 2 != 0) {
                m.put(attrs.get(i-1).toString(), attrs.get(i));
            }
        }

        List<Map<String, Object>> docs = new ArrayList<>();
        List<Map<String, Object>> results = (List<Map<String, Object>>) m.get("results");
        for (Map<String, Object> result : results) {
            Map<String, Object> map = (Map<String, Object>) result.get("extra_attributes");
            docs.add(map);
        }
        Long total = (Long) m.get("total_results");
        long cursorId = (long) parts.get(1);
        return new AggregationResult(total, docs, cursorId);
    }

}
