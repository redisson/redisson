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

import java.util.Objects;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class CountMinMergeArgsImpl implements CountMinMergeArgs {

    final String[] names;
    long[] weights;

    CountMinMergeArgsImpl(String[] names) {
        Objects.requireNonNull(names, "Source sketch names can't be null");
        if (names.length == 0) {
            throw new IllegalArgumentException("At least one source sketch name is required");
        }
        this.names = names;
    }

    @Override
    public CountMinMergeArgs weights(long... weights) {
        if (weights.length != names.length) {
            throw new IllegalArgumentException(
                    "Amount of weights (" + weights.length + ") should match "
                            + "amount of source sketches (" + names.length + ")");
        }
        this.weights = weights;
        return this;
    }

    public String[] getNames() {
        return names;
    }

    public long[] getWeights() {
        return weights;
    }
}
