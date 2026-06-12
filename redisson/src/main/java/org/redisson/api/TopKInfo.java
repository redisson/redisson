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
 *
 * @author Nikita Koksharov
 *
 */
public class TopKInfo {

    private final long topK;
    private final long width;
    private final long depth;
    private final double decay;

    public TopKInfo(long topK, long width, long depth, double decay) {
        this.topK = topK;
        this.width = width;
        this.depth = depth;
        this.decay = decay;
    }

    /**
     * Returns the number of top items the Top-K keeps track of.
     *
     * @return number of top items (k)
     */
    public long getTopK() {
        return topK;
    }

    /**
     * Returns the number of counters in each array (width).
     *
     * @return width
     */
    public long getWidth() {
        return width;
    }

    /**
     * Returns the number of counter arrays (depth).
     *
     * @return depth
     */
    public long getDepth() {
        return depth;
    }

    /**
     * Returns the probability of a counter being decreased on collision.
     *
     * @return counter decay probability
     */
    public double getDecay() {
        return decay;
    }
}
