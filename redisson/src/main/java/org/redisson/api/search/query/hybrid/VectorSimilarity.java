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
package org.redisson.api.search.query.hybrid;

/**
 * Vector similarity configuration for hybrid search.
 * <p>
 * Supports range-based and K-nearest neighbors (KNN) vector search modes.
 *
 * @author Nikita Koksharov
 */
public interface VectorSimilarity {

    static VectorSimilarityBasic of(String field, String param) {
        return new VectorSimilarityParams(field, param);
    }

    /**
     * Assigns an alias to the vector similarity score.
     *
     * @param value score alias name
     * @return this instance for further configuration
     */
    VectorSimilarity scoreAlias(String value);

    /**
     * Applies pre-filtering to vector search results.
     *
     * @param value filter expression
     * @return this instance for further configuration
     */
    VectorSimilarity filter(String value);

}