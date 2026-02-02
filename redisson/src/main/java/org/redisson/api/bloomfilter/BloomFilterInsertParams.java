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

import java.util.Collection;

/**
 * BloomFilter Params for BF.INSERT command
 *
 * @author Su Ko
 *
 */
public class BloomFilterInsertParams<V> implements BloomFilterInsertArgs<V>, OptionalBloomFilterInsertArgs<V> {
    private final Collection<V> elements;

    private Double errorRate;
    private Long capacity;
    private Long expansionRate;
    private Boolean nonScaling;
    private Boolean noCreate;

    public BloomFilterInsertParams(Collection<V> elements) {
        this.elements = elements;
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

    public Collection<V> getElements() {
        return elements;
    }

    @Override
    public OptionalBloomFilterInsertArgs<V> capacity(long capacity) {
        this.capacity = capacity;
        return this;
    }

    @Override
    public OptionalBloomFilterInsertArgs<V> errorRate(double errorRate) {
        this.errorRate = errorRate;
        return this;
    }

    @Override
    public OptionalBloomFilterInsertArgs<V> expansionRate(long expansionRate) {
        this.expansionRate = expansionRate;
        return this;
    }

    @Override
    public OptionalBloomFilterInsertArgs<V> nonScaling(boolean nonScaling) {
        this.nonScaling = nonScaling;
        return this;
    }

    @Override
    public OptionalBloomFilterInsertArgs<V> noCreate(boolean noCreate) {
        this.noCreate = noCreate;
        return this;
    }
}
