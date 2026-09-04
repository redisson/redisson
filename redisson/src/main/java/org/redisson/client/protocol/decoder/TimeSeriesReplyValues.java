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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 *
 */
final class TimeSeriesReplyValues {

    private TimeSeriesReplyValues() {
    }

    static long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    static double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    static TSSample toSample(List<Object> parts) {
        double[] values = new double[parts.size() - 1];
        for (int i = 0; i < values.length; i++) {
            values[i] = toDouble(parts.get(i + 1));
        }
        return new TSSample(toLong(parts.get(0)), values);
    }

    static Map<String, String> toLabels(List<Object> parts) {
        if (parts == null || parts.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> labels = new LinkedHashMap<>();
        if (parts.get(0) instanceof List) {
            for (Object part : parts) {
                List<Object> pair = (List<Object>) part;
                labels.put((String) pair.get(0), (String) pair.get(1));
            }
            return labels;
        }

        for (int i = 0; i < parts.size(); i += 2) {
            labels.put((String) parts.get(i), (String) parts.get(i + 1));
        }
        return labels;
    }

}
