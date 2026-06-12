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
package org.redisson.api.topk;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class TopKInitArgsImpl implements TopKInitArgs {

    final int topK;
    Integer width;
    Integer depth;
    Double decay;

    TopKInitArgsImpl(int topK) {
        this.topK = topK;
    }

    @Override
    public TopKInitArgs width(int width) {
        this.width = width;
        return this;
    }

    @Override
    public TopKInitArgs depth(int depth) {
        this.depth = depth;
        return this;
    }

    @Override
    public TopKInitArgs decay(double decay) {
        this.decay = decay;
        return this;
    }

    public int getTopK() {
        return topK;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getDepth() {
        return depth;
    }

    public Double getDecay() {
        return decay;
    }
}
