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
 * Vector similarity configuration for hybrid search.
 * <p>
 * Supports range-based and K-nearest neighbors (KNN) vector search modes.
 *
 * @author Nikita Koksharov
 */
public interface VectorSimilarityBasic extends VectorSimilarity {

    /**
     * Creates a range-based vector similarity search configuration.
     *
     * @param radius maximum distance for vector matches
     * @return range configuration step
     */
    VectorSimilarityRange range(double radius);

    /**
     * Creates a K-nearest neighbors vector similarity search configuration.
     *
     * @param k number of nearest neighbors to find
     * @return KNN configuration step
     */
    VectorSimilarityNearestNeighbors nearestNeighbors(int k);

}