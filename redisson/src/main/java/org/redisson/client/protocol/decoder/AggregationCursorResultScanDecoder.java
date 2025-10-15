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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author seakider
 *
 */
public class AggregationCursorResultScanDecoder implements MultiDecoder<ListScanResult<AggregationEntry>> {

    @Override
    public ListScanResult<AggregationEntry> decode(List<Object> parts, State state) {

        List<Object> list = (List<Object>) parts.get(0);
        long total = (long) list.get(0);
        List<AggregationEntry> result = new LinkedList<>();
        for (int i = 1; i < list.size(); i++) {
            result.add(new AggregationEntry(total, (Map<String, Object>) list.get(i)));
        }

        long cursorId = (long) parts.get(1);

        return new ListScanResult<>(String.valueOf(cursorId), result);
    }
}
