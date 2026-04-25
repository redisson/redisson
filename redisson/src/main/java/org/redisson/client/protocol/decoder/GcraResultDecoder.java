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

import org.redisson.api.GcraResult;
import org.redisson.client.handler.State;

import java.util.List;

/**
 *
 * @author Su Ko
 *
 */
public class GcraResultDecoder implements MultiDecoder<GcraResult> {

    @Override
    public GcraResult decode(List<Object> parts, State state) {
        if (parts == null || parts.size() != 5) {
            throw new IllegalStateException("Unexpected GCRA response: " + parts);
        }

        return new GcraResult(toLong(parts.get(0)) == 1,
                toLong(parts.get(1)),
                toLong(parts.get(2)),
                toLong(parts.get(3)),
                toLong(parts.get(4)));
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

}
