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

import org.redisson.api.tsnative.TSSample;
import org.redisson.api.tsnative.TSSeriesSample;
import org.redisson.client.handler.State;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class TimeSeriesSeriesSampleDecoder implements MultiDecoder<Map<String, TSSeriesSample>> {

    @Override
    public Map<String, TSSeriesSample> decode(List<Object> parts, State state) {
        if (parts.isEmpty()) {
            return Collections.emptyMap();
        }

        // Insertion-ordered so the map hands rows back in the order the server listed them,
        // which a plain HashMap would scramble for no gain.
        Map<String, TSSeriesSample> result = new LinkedHashMap<>();
        if (parts.get(0) instanceof List) {
            for (Object part : parts) {
                List<Object> row = (List<Object>) part;
                result.put((String) row.get(0),
                        toSeriesSample((List<Object>) row.get(1), (List<Object>) row.get(2)));
            }
            return result;
        }

        for (int i = 0; i < parts.size(); i += 2) {
            List<Object> row = (List<Object>) parts.get(i + 1);
            result.put((String) parts.get(i),
                    toSeriesSample((List<Object>) row.get(0), (List<Object>) row.get(1)));
        }
        return result;
    }

    private static TSSeriesSample toSeriesSample(List<Object> labels, List<Object> sample) {
        TSSample lastSample = null;
        if (!sample.isEmpty()) {
            lastSample = TimeSeriesReplyValues.toSample(sample);
        }
        return new TSSeriesSample(TimeSeriesReplyValues.toLabels(labels), lastSample);
    }

}
