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
package org.redisson.api.cuckoofilter;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class CuckooFilterInitArgsImpl implements CuckooFilterInitArgs {

    final long capacity;
    Long bucketSize;
    Long maxIterations;
    Long expansion;

    CuckooFilterInitArgsImpl(long capacity) {
        this.capacity = capacity;
    }

    @Override
    public CuckooFilterInitArgs bucketSize(long bucketSize) {
        this.bucketSize = bucketSize;
        return this;
    }

    @Override
    public CuckooFilterInitArgs maxIterations(long maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    @Override
    public CuckooFilterInitArgs expansion(long expansion) {
        this.expansion = expansion;
        return this;
    }

    public long getCapacity() {
        return capacity;
    }

    public Long getBucketSize() {
        return bucketSize;
    }

    public Long getMaxIterations() {
        return maxIterations;
    }

    public Long getExpansion() {
        return expansion;
    }
}
