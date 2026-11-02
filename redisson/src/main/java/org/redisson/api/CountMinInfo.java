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
    private final int cellSize;

    /**
     * Creates information about a sketch with 4-byte cells,
     * the only size before Redis 8.12.0.
     *
     * @param width number of counters in each array
     * @param depth number of counter arrays
     * @param count total count of all items added to the sketch
     */
    public CountMinInfo(long width, long depth, long count) {
        this(width, depth, count, 4);
    }

    /**
     * Creates information about a sketch.
     *
     * @param width number of counters in each array
     * @param depth number of counter arrays
     * @param count total count of all items added to the sketch
     * @param cellSize size of each counter in bytes
     */
    public CountMinInfo(long width, long depth, long count, int cellSize) {
        this.width = width;
        this.depth = depth;
        this.count = count;
        this.cellSize = cellSize;
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
     * This is the sum of all increments, negative ones included,
     * not the number of distinct items.
     * <p>
     * Before Redis 8.12.0 it keeps accumulating after individual counters
     * have saturated at {@code 2^32-1}, so on a long-lived sketch it can
     * exceed what any count query is able to report. Since Redis 8.12.0
     * an increment that would overflow a counter is rejected and not
     * counted here either.
     *
     * @return total count
     */
    public long getCount() {
        return count;
    }

    /**
     * Returns the size of each counter in bytes: 1, 2, 4 or 8.
     * <p>
     * Servers before Redis 8.12.0 do not report it. Every sketch
     * they hold has 4-byte cells, so 4 is returned for them.
     *
     * @return cell size in bytes
     * @see org.redisson.api.countmin.CountMinInitArgs#cellSize(int)
     */
    public int getCellSize() {
        return cellSize;
    }
}
