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

import org.redisson.api.search.aggregate.AggregationEntry;
import org.redisson.client.handler.State;

import java.util.*;

/**
 *
 * @author seakider
 *
 */
public class AggregationCursorResultScanDecoderV2 implements MultiDecoder<ListScanResult<AggregationEntry>> {

    @Override
    public ListScanResult<AggregationEntry> decode(List<Object> parts, State state) {
        List<Object> attrs = (List<Object>) parts.get(0);
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < attrs.size(); i++) {
            if (i % 2 != 0) {
                m.put(attrs.get(i-1).toString(), attrs.get(i));
            }
        }

        Long total = (Long) m.get("total_results");
        List<AggregationEntry> docs = new LinkedList<>();
        List<Map<String, Object>> results = (List<Map<String, Object>>) m.get("results");
        for (Map<String, Object> result : results) {
            Map<String, Object> map = (Map<String, Object>) result.get("extra_attributes");
            docs.add(new AggregationEntry(total, map));
        }

        return new ListScanResult<>((String) parts.get(1), docs);
    }
}
