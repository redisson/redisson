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
package org.redisson.api;

/**
 *
 * @author seakider
 *
 */
class SetReadArgsParam implements SetReadArgs {
    private String[] names;
    private RScoredSortedSet.Aggregate aggregate;
    private Double[] weights;

    SetReadArgsParam(String[] names) {
        this.names = names;
        this.aggregate = RScoredSortedSet.Aggregate.SUM;
    }

    @Override
    public SetReadArgs weights(Double... weights) {
        this.weights = weights;
        return this;
    }

    @Override
    public SetReadArgs aggregate(RScoredSortedSet.Aggregate aggregate) {
        this.aggregate = aggregate;
        return this;
    }

    public String[] getNames() {
        return names;
    }

    public RScoredSortedSet.Aggregate getAggregate() {
        return aggregate;
    }

    public Double[] getWeights() {
        return weights;
    }
}
