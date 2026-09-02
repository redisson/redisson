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
package org.redisson.api.countmin;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class CountMinInitArgsImpl implements CountMinInitArgs {

    final Long width;
    final Long depth;
    final Double errorRate;
    final Double probability;

    CountMinInitArgsImpl(long width, long depth) {
        this.width = width;
        this.depth = depth;
        this.errorRate = null;
        this.probability = null;
    }

    CountMinInitArgsImpl(double errorRate, double probability) {
        this.width = null;
        this.depth = null;
        this.errorRate = errorRate;
        this.probability = probability;
    }

    public Long getWidth() {
        return width;
    }

    public Long getDepth() {
        return depth;
    }

    public Double getErrorRate() {
        return errorRate;
    }

    public Double getProbability() {
        return probability;
    }

    public boolean isByDimensions() {
        return width != null;
    }
}
