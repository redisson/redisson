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

/**
 * BloomFilter Params for BF.INSERT command
 *
 * @author Su Ko
 *
 */
public class BloomFilterInsertParams implements BloomFilterInsertArgs, OptionalBloomFilterInsertArgs{
    private final String[] items;

    private Double errorRate;
    private Long capacity;
    private Long expansionRate;
    private Boolean nonScaling;
    private Boolean noCreate;

    public BloomFilterInsertParams(String[] items) {
        this.items = items;
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

    public String[] getItems() {
        return items;
    }

    @Override
    public OptionalBloomFilterInsertArgs capacity(long capacity) {
        this.capacity = capacity;
        return this;
    }

    @Override
    public OptionalBloomFilterInsertArgs errorRate(double errorRate) {
        this.errorRate = errorRate;
        return this;
    }

    @Override
    public OptionalBloomFilterInsertArgs expansionRate(long expansionRate) {
        this.expansionRate = expansionRate;
        return this;
    }

    @Override
    public OptionalBloomFilterInsertArgs nonScaling(boolean nonScaling) {
        this.nonScaling = nonScaling;
        return this;
    }

    @Override
    public OptionalBloomFilterInsertArgs noCreate(boolean noCreate) {
        this.noCreate = noCreate;
        return this;
    }
}
