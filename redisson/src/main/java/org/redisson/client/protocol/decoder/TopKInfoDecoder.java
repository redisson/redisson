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

import org.redisson.api.TopKInfo;
import org.redisson.client.handler.State;

import java.util.List;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class TopKInfoDecoder implements MultiDecoder<TopKInfo> {

    @Override
    public TopKInfo decode(List<Object> parts, State state) {
        long topK = 0;
        long width = 0;
        long depth = 0;
        double decay = 0;

        for (int i = 0; i < parts.size() - 1; i += 2) {
            String field = String.valueOf(parts.get(i));
            Object value = parts.get(i + 1);
            switch (field) {
                case "k":
                    topK = toLong(value);
                    break;
                case "width":
                    width = toLong(value);
                    break;
                case "depth":
                    depth = toLong(value);
                    break;
                case "decay":
                    decay = toDouble(value);
                    break;
                default:
                    break;
            }
        }

        return new TopKInfo(topK, width, depth, decay);
    }

    private static long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value != null) {
            return Long.parseLong(value.toString());
        }
        return 0;
    }

    private static double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value != null) {
            return Double.parseDouble(value.toString());
        }
        return 0;
    }

}
