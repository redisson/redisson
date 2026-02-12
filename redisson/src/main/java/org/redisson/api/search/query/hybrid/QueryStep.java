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
 * Query step for configuring the text search component of hybrid search.
 *
 * @author Nikita Koksharov
 */
public interface QueryStep {

    /**
     * Specifies the scoring algorithm for the text search component.
     *
     * @param scorer scoring algorithm name (e.g., "BM25")
     * @return this step for further configuration
     */
    QueryStep scorer(String scorer);

    /**
     * Assigns an alias to the search score for use in post-processing.
     *
     * @param alias score alias name
     * @return this step for further configuration
     */
    QueryStep scoreAlias(String alias);

    /**
     * Defines the vector similarity component of the hybrid query.
     *
     * @param value vector similarity configuration
     * @return params step for further configuration
     */
    ParamsStep vectorSimilarity(VectorSimilarity value);

}