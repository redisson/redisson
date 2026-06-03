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

import org.redisson.api.array.ArrayEntry;
import org.redisson.client.handler.State;

import java.util.ArrayList;
import java.util.List;

/**
 * Array entry decoder.
 *
 * @author lamnt2008
 *
 */
public class ArrayEntryDecoder implements MultiDecoder<List<ArrayEntry<Object>>> {

    @Override
    public List<ArrayEntry<Object>> decode(List<Object> parts, State state) {
        List<ArrayEntry<Object>> result = new ArrayList<>(parts.size());
        if (!parts.isEmpty() && parts.get(0) instanceof List) {
            for (Object part : parts) {
                List<?> entry = (List<?>) part;
                result.add(new ArrayEntry<>(toLong(entry.get(0)), entry.get(1)));
            }
            return result;
        }

        for (int i = 0; i + 1 < parts.size(); i += 2) {
            result.add(new ArrayEntry<>(toLong(parts.get(i)), parts.get(i + 1)));
        }
        return result;
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

}
