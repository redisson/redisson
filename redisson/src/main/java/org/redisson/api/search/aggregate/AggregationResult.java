/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import java.util.List;
import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class AggregationResult {

    private final long total;
    private final List<Map<String, Object>> attributes;

    private long cursorId = -1;

    public AggregationResult(long total, List<Map<String, Object>> attributes) {
        this.total = total;
        this.attributes = attributes;
    }

    public AggregationResult(long total, List<Map<String, Object>> attributes, long cursorId) {
        this.total = total;
        this.attributes = attributes;
        this.cursorId = cursorId;
    }

    /**
     * Returns cursor id value.
     *
     * @return cursor id value
     */
    public long getCursorId() {
        return cursorId;
    }

    /**
     * Returns total amount of attributes.
     *
     * @return total amount of attributes
     */
    public long getTotal() {
        return total;
    }

    /**
     * List of attributes mapped by attribute name.
     *
     * @return list of attributes
     */
    public List<Map<String, Object>> getAttributes() {
        return attributes;
    }
}
