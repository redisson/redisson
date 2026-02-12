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
package org.redisson.api.bloomfilter;

/**
 * BloomFilter info for BF.INFO command
 *
 * @author Su Ko
 *
 */
public class BloomFilterInfo {
    private final long capacity;
    private final long size;
    private final long subFilterCount;
    private final long itemCount;
    private final long expansionRate;

    public BloomFilterInfo(Long capacity, Long size, Long subFilterCount, Long itemCount, Long expansionRate) {
        this.capacity = capacity;
        this.size = size;
        this.subFilterCount = subFilterCount;
        this.itemCount = itemCount;
        this.expansionRate = expansionRate;
    }

    public long getExpansionRate() {
        return expansionRate;
    }

    public long getItemCount() {
        return itemCount;
    }

    public long getSubFilterCount() {
        return subFilterCount;
    }

    public long getSize() {
        return size;
    }

    public long getCapacity() {
        return capacity;
    }
}
