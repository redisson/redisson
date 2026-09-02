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

import org.redisson.api.CountMinInfo;
import org.redisson.client.handler.State;

import java.util.List;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class CountMinInfoDecoder implements MultiDecoder<CountMinInfo> {

    @Override
    public CountMinInfo decode(List<Object> parts, State state) {
        return new CountMinInfo(getLong(parts, "width"),
                                getLong(parts, "depth"),
                                getLong(parts, "count"));
    }

    private static long getLong(List<Object> list, String key) {
        for (int i = 0; i < list.size() - 1; i += 2) {
            if (key.equals(String.valueOf(list.get(i)))) {
                Object value = list.get(i + 1);
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
            }
        }
        return 0;
    }

}
