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
package org.redisson.api;

/**
 * Count-min sketch information returned by the {@code CMS.INFO} command.
 *
 * @author Nikita Koksharov
 *
 */
public class CountMinInfo {

    private final long width;
    private final long depth;
    private final long count;

    public CountMinInfo(long width, long depth, long count) {
        this.width = width;
        this.depth = depth;
        this.count = count;
    }

    /**
     * Returns the number of counters in each array.
     *
     * @return width
     */
    public long getWidth() {
        return width;
    }

    /**
     * Returns the number of counter arrays.
     *
     * @return depth
     */
    public long getDepth() {
        return depth;
    }

    /**
     * Returns the total count of all items added to the sketch.
     * <p>
     * This is the sum of all increments, not the number of distinct items.
     *
     * @return total count
     */
    public long getCount() {
        return count;
    }
}
