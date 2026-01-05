/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import java.util.ArrayList;
import java.util.List;

/**
 * BloomFilter option for initialization
 *
 * @author Su Ko
 *
 */
public class BloomFilterInitOptions {
    private final double errorRate;
    private final long capacity;
    private final Long expansionRate;
    private final Boolean nonScaling;

    // expansionRate and nonScaling are mutually exclusive
    public BloomFilterInitOptions(double errorRate, long capacity, Boolean nonScaling) {
        this(errorRate, capacity, null, nonScaling);
    }

    public BloomFilterInitOptions(double errorRate, long capacity, Long expansionRate) {
        this(errorRate, capacity, expansionRate, null);
    }

    public BloomFilterInitOptions(double errorRate, long capacity) {
        this(errorRate, capacity, null, null);
    }

    private BloomFilterInitOptions(double errorRate, long capacity, Long expansionRate, Boolean nonScaling) {
        this.errorRate = errorRate;
        this.capacity = capacity;

        this.expansionRate = expansionRate;
        this.nonScaling = nonScaling;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public long getCapacity() {
        return capacity;
    }

    public Long getExpansionRate() {
        return expansionRate;
    }

    public Boolean isNonScaling() {
        return nonScaling;
    }

    public List<Object> toParams(String key) {
        if (errorRate <= 0 || errorRate >= 1) {
            throw new IllegalArgumentException("BloomFilter Native errorRate must be greater than 0 and less than 1");
        }

        if (capacity <= 0) {
            throw new IllegalArgumentException("BloomFilter Native capacity must be greater than 0");
        }

        if (expansionRate != null && nonScaling != null) {
            throw new IllegalArgumentException("BloomFilter Native expansionRate and nonScaling are mutually exclusive");
        }

        List<Object> params = new ArrayList<>();
        params.add(key);
        params.add(errorRate);
        params.add(capacity);

        if (expansionRate != null) {
            if (expansionRate <= 1) {
                throw new IllegalArgumentException("BloomFilter Native expansionRate must be greater than 1");
            }

            params.add("EXPANSION");
            params.add(expansionRate);
        }

        if (nonScaling != null && nonScaling) {
            params.add("NONSCALING");
        }

        return params;
    }
}
