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
package org.redisson.api.search.query.hybrid;

/**
 * Combine configuration for hybrid search fusion methods.
 * <p>
 * Supports Reciprocal Rank Fusion (RRF) and Linear combination methods.
 *
 * @author Nikita Koksharov
 */
public interface Combine {

    /**
     * Creates a Reciprocal Rank Fusion (RRF) combine configuration.
     * <p>
     * RRF combines rankings from text search and vector similarity using
     * the formula: score = 1 / (constant + rank)
     *
     * @return RRF configuration step
     */
    static CombineReciprocalRankFusionStep reciprocalRankFusion() {
        return new CombineRrfParams();
    }

    /**
     * Creates a Linear combination configuration.
     * <p>
     * Linear combination uses weighted scores: score = alpha * text_score + beta * vector_score
     *
     * @return Linear configuration step
     */
    static CombineLinearStep linear() {
        return new CombineLinearParams();
    }

}