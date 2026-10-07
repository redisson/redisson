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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Decoder for commands replying with counters positionally
 * matching the elements sent in the request.
 * <p>
 * Zips the request elements with the returned counters
 * preserving the request order.
 *
 * @param <T> element type
 *
 * @author Nikita Koksharov
 *
 */
public class CountMapDecoder<T> implements MultiDecoder<Map<T, Long>> {

    private final List<T> args;

    public CountMapDecoder(Collection<T> args) {
        if (args instanceof List) {
            this.args = (List<T>) args;
        } else {
            this.args = new ArrayList<>(args);
        }
    }

    @Override
    public Map<T, Long> decode(List<Object> parts, State state) {
        if (parts.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<T, Long> result = MultiDecoder.newLinkedHashMap(parts.size());
        for (int index = 0; index < parts.size(); index++) {
            Object value = parts.get(index);
            if (value instanceof Number) {
                result.put(args.get(index), ((Number) value).longValue());
            }
        }

        return result;
    }

}
