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
package org.redisson.api.search.aggregate;

import java.util.Map;

/**
 *
 * @author seakider
 *
 */
public class AggregationEntry {
    private final long total;
    private final Map<String, Object> attributes;

    public AggregationEntry(long total, Map<String, Object> attributes) {
        this.total = total;
        this.attributes = attributes;
    }

    public long getTotal() {
        return total;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
