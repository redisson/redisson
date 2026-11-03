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

import org.redisson.api.search.query.ScoreExplanation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Search result object returned by
 * {@link org.redisson.api.RSearch#hybridSearch(String, HybridQueryArgs)} method
 *
 * @author Nikita Koksharov
 *
 */
public final class HybridSearchResult {

    private final long total;

    private final List<Map<String, String>> results;

    private final List<ScoreExplanation> scoreExplanations;

    public HybridSearchResult(long total, List<Map<String, String>> scores) {
        this(total, scores, Collections.emptyList());
    }

    public HybridSearchResult(long total, List<Map<String, String>> results, List<ScoreExplanation> scoreExplanations) {
        this.total = total;
        this.results = results;
        this.scoreExplanations = scoreExplanations;
    }

    /**
     * Returns total number of results.
     *
     * @return total number of results
     */
    public long getTotal() {
        return total;
    }

    /**
     * Returns result data
     *
     * @return scores
     */
    public List<Map<String, String>> getResults() {
        return results;
    }

    /**
     * Returns score explanations requested by {@link HybridQueryArgs#explainScore()}.
     * The explanation at each index belongs to the result at the same index
     * of {@link #getResults()}; it's <code>null</code> if Redis sent none for that result.
     *
     * @return score explanations, empty if they weren't requested
     */
    public List<ScoreExplanation> getScoreExplanations() {
        return scoreExplanations;
    }
}
