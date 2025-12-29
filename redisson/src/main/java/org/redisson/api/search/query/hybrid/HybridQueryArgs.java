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

import org.redisson.api.search.aggregate.Expression;
import org.redisson.api.search.aggregate.GroupBy;

import java.time.Duration;

/**
 * Arguments for FT.HYBRID command which performs hybrid search combining
 * text search and vector similarity with configurable fusion methods.
 * <p>
 * Requires Redis 8.4.0 or higher.
 *
 * @author Nikita Koksharov
 */
public interface HybridQueryArgs {

    /**
     * Creates a new hybrid query with the specified text search query.
     *
     * @param query the text search query expression
     * @return query step for further configuration
     */
    static QueryStep query(String query) {
        return new HybridQueryParams(query);
    }

    /**
     * Configures the combine method for fusing text search and vector similarity results.
     *
     * @param value combine configuration (use {@link Combine#reciprocalRankFusion()} or {@link Combine#linear()})
     * @return this instance
     */
    HybridQueryArgs combine(Combine value);

    /**
     * Specifies which fields to return in the results.
     *
     * @param fields field names to load
     * @return this instance
     */
    HybridQueryArgs load(String... fields);

    /**
     * Limits the final results.
     *
     * @param offset zero-indexed offset
     * @param count number of results to return
     * @return this instance
     */
    HybridQueryArgs limit(int offset, int count);

    /**
     * Groups results by specified fields with reduction functions.
     *
     * @param groups group by definitions
     * @return this instance
     */
    HybridQueryArgs groupBy(GroupBy... groups);

    /**
     * Applies transformations to create new fields.
     *
     * @param expressions apply expressions
     * @return this instance
     */
    HybridQueryArgs apply(Expression... expressions);

    /**
     * Sets a runtime timeout for the query execution.
     *
     * @param timeout query timeout
     * @return this instance
     */
    HybridQueryArgs timeout(Duration timeout);

    /**
     * Applies post-filtering after the COMBINE step.
     *
     * @param value filter expression
     * @return this instance
     */
    HybridQueryArgs filter(String value);

}