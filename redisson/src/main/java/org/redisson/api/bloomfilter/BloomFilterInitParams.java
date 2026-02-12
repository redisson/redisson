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
 * BloomFilterInitParams for BF.RESERVE command
 *
 * @author Su Ko
 *
 */
public class BloomFilterInitParams implements BloomFilterInitArgs, ErrorRateBloomFilterInitArgs, CapacityBloomFilterInitArgs, OptionalBloomFilterInitArgs {

    private double errorRate;
    private long capacity;
    private Long expansionRate;
    private Boolean nonScaling;

    @Override
    public CapacityBloomFilterInitArgs errorRate(double errorRate) {
        this.errorRate = errorRate;
        return this;
    }

    @Override
    public OptionalBloomFilterInitArgs capacity(long capacity) {
        this.capacity = capacity;
        return this;
    }

    @Override
    public OptionalBloomFilterInitArgs expansionRate(long expansionRate) {
        this.expansionRate = expansionRate;
        return this;
    }

    @Override
    public OptionalBloomFilterInitArgs nonScaling(boolean nonScaling) {
        this.nonScaling = nonScaling;
        return this;
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
}
