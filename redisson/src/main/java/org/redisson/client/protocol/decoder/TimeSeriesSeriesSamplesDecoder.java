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
import org.redisson.api.tsnative.TSSeriesSamples;
import org.redisson.client.handler.State;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class TimeSeriesSeriesSamplesDecoder implements MultiDecoder<Map<String, TSSeriesSamples>> {

    @Override
    public Map<String, TSSeriesSamples> decode(List<Object> parts, State state) {
        if (parts.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, TSSeriesSamples> result = new LinkedHashMap<>();
        if (parts.get(0) instanceof List) {
            for (Object part : parts) {
                List<Object> row = (List<Object>) part;
                result.put((String) row.get(0),
                        toSeriesSamples(TimeSeriesReplyValues.toLabels((List<Object>) row.get(1)),
                                (List<Object>) row.get(row.size() - 1)));
            }
            return result;
        }

        for (int i = 0; i < parts.size(); i += 2) {
            List<Object> row = (List<Object>) parts.get(i + 1);
            Map<String, String> labels =
                    new LinkedHashMap<>(TimeSeriesReplyValues.toLabels((List<Object>) row.get(0)));

            for (int j = 1; j < row.size() - 1; j++) {
                List<Object> entry = (List<Object>) row.get(j);
                if (entry.size() < 2) {
                    continue;
                }
                if ("reducers".equals(entry.get(0))) {
                    labels.put("__reducer__", join(entry.get(1)));
                } else if ("sources".equals(entry.get(0))) {
                    labels.put("__source__", join(entry.get(1)));
                }
            }
            result.put((String) parts.get(i),
                    toSeriesSamples(labels, (List<Object>) row.get(row.size() - 1)));
        }
        return result;
    }

    private static String join(Object value) {
        List<Object> items = (List<Object>) value;
        StringBuilder joined = new StringBuilder();
        for (Object item : items) {
            if (joined.length() > 0) {
                joined.append(',');
            }
            joined.append(item);
        }
        return joined.toString();
    }

    private static TSSeriesSamples toSeriesSamples(Map<String, String> labels, List<Object> rawSamples) {
        List<TSSample> samples = new ArrayList<>(rawSamples.size());
        for (Object rawSample : rawSamples) {
            samples.add(TimeSeriesReplyValues.toSample((List<Object>) rawSample));
        }
        return new TSSeriesSamples(labels, samples);
    }

}
