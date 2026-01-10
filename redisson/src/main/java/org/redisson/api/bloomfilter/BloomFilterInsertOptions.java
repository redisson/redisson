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
 *'BloomFilter option for BF.INSERT command
 *
 * @author Su Ko
 *
 */
public class BloomFilterInsertOptions {
    private final Double errorRate;
    private final Long capacity;
    private final Long expansionRate;
    private final Boolean nonScaling;
    private final Boolean noCreate;

    // noCreate and capacity/errorRate are mutually exclusive
    public BloomFilterInsertOptions(Long expansionRate, Boolean nonScaling, Boolean noCreate) {
        this(null, null, expansionRate, nonScaling, noCreate);
    }

    public BloomFilterInsertOptions(Double errorRate, Long capacity, Long expansionRate, Boolean nonScaling) {
        this(errorRate, capacity, expansionRate, nonScaling, null);
    }

    private BloomFilterInsertOptions(Double errorRate, Long capacity, Long expansionRate, Boolean nonScaling, Boolean noCreate) {
        this.errorRate = errorRate;
        this.capacity = capacity;
        this.expansionRate = expansionRate;
        this.nonScaling = nonScaling;
        this.noCreate = noCreate;
    }

    public Double getErrorRate() {
        return errorRate;
    }

    public Long getCapacity() {
        return capacity;
    }

    public Long getExpansionRate() {
        return expansionRate;
    }

    public Boolean isNoCreate() {
        return noCreate;
    }

    public Boolean isNonScaling() {
        return nonScaling;
    }

    public List<Object> toParams(String key) {
        List<Object> params = new ArrayList<Object>();
        params.add(key);

        if (noCreate != null && noCreate && (capacity != null || errorRate != null)) {
            throw new IllegalArgumentException("BloomFilter Native noCreate and capacity/errorRate are mutually exclusive");
        }

        if (capacity != null) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("BloomFilter Native capacity must be greater than 0");
            }

            params.add("CAPACITY");
            params.add(capacity);
        }

        if (errorRate != null) {
            if (errorRate <= 0 || errorRate >= 1) {
                throw new IllegalArgumentException("BloomFilter Native errorRate must be greater than 0 and less than 1");
            }

            params.add("ERROR");
            params.add(errorRate);
        }

        if (expansionRate != null) {
            if (expansionRate <= 0) {
                throw new IllegalArgumentException("BloomFilter Native expansionRate must be greater than 0");
            }

            params.add("EXPANSION");
            params.add(expansionRate);
        }

        if (noCreate != null && noCreate) {
            params.add("NOCREATE");
        }

        if (nonScaling != null && nonScaling) {
            params.add("NONSCALING");
        }

        params.add("ITEMS");

        return params;
    }
}
