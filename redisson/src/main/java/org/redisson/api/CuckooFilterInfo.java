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

import java.util.List;

/**
 * Cuckoo filter information returned by the {@code CF.INFO} command.
 *
 * @author Nikita Koksharov
 *
 */
public class CuckooFilterInfo {

    private final long size;
    private final long numberOfBuckets;
    private final long numberOfFilters;
    private final long numberOfInsertedItems;
    private final long numberOfDeletedItems;
    private final long bucketSize;
    private final long expansionRate;
    private final long maxIterations;

    /**
     * Creates instance from the raw list returned by {@code CF.INFO}.
     * <p>
     * The response is a flat list of alternating field names and values:
     * {@code [field1, value1, field2, value2, ...]}.
     *
     * @param info raw response list
     */
    public CuckooFilterInfo(List<Object> info) {
        this.size = getLong(info, "Size");
        this.numberOfBuckets = getLong(info, "Number of buckets");
        this.numberOfFilters = getLong(info, "Number of filters");
        this.numberOfInsertedItems = getLong(info, "Number of items inserted");
        this.numberOfDeletedItems = getLong(info, "Number of items deleted");
        this.bucketSize = getLong(info, "Bucket size");
        this.expansionRate = getLong(info, "Expansion rate");
        this.maxIterations = getLong(info, "Max iterations");
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

    /**
     * Returns the memory size in bytes.
     *
     * @return size in bytes
     */
    public long getSize() {
        return size;
    }

    /**
     * Returns the number of buckets in the filter.
     *
     * @return number of buckets
     */
    public long getNumberOfBuckets() {
        return numberOfBuckets;
    }

    /**
     * Returns the number of sub-filters.
     *
     * @return number of filters
     */
    public long getNumberOfFilters() {
        return numberOfFilters;
    }

    /**
     * Returns the number of items inserted into the filter.
     *
     * @return number of inserted items
     */
    public long getNumberOfInsertedItems() {
        return numberOfInsertedItems;
    }

    /**
     * Returns the number of items deleted from the filter.
     *
     * @return number of deleted items
     */
    public long getNumberOfDeletedItems() {
        return numberOfDeletedItems;
    }

    /**
     * Returns the number of items each bucket can hold.
     *
     * @return bucket size
     */
    public long getBucketSize() {
        return bucketSize;
    }

    /**
     * Returns the expansion rate.
     *
     * @return expansion rate
     */
    public long getExpansionRate() {
        return expansionRate;
    }

    /**
     * Returns the maximum number of swap attempts
     * before declaring the filter full.
     *
     * @return max iterations
     */
    public long getMaxIterations() {
        return maxIterations;
    }
}
