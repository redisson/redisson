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

import org.redisson.client.handler.State;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class TimeSeriesMultiAddDecoder implements MultiDecoder<Map<String, List<Long>>> {

    private final List<Object> keys;
    private final List<Integer> counts;

    public TimeSeriesMultiAddDecoder(List<Object> keys, List<Integer> counts) {
        this.keys = keys;
        this.counts = counts;
    }

    @Override
    public Map<String, List<Long>> decode(List<Object> parts, State state) {
        Map<String, List<Long>> result = new LinkedHashMap<>(keys.size());
        int index = 0;
        for (int i = 0; i < keys.size(); i++) {
            int count = counts.get(i);
            List<Long> timestamps = new ArrayList<>(count);
            for (int n = 0; n < count; n++) {
                timestamps.add(TimeSeriesReplyValues.toLong(parts.get(index)));
                index++;
            }
            result.put((String) keys.get(i), timestamps);
        }
        return result;
    }

}
