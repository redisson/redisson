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
package org.redisson.api.search.query;

import org.redisson.api.SortOrder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Search query options for {@link org.redisson.api.RSearch#search(String, String, QueryOptions)} method
 *
 * @author Nikita Koksharov
 *
 */
public final class QueryOptions {

    private boolean noContent;
    private boolean verbatim;
    private boolean noStopwords;
    private boolean withScores;
    private boolean withSortKeys;
    private Integer slop;
    private Long timeout;
    private boolean inOrder;
    private String language;
    private String expander;
    private String scorer;
    private boolean explainScore;
    private String sortBy;
    private SortOrder sortOrder;
    private boolean withCount;
    private Integer offset;
    private Integer count;
    private Map<String, Object> params = Collections.emptyMap();
    private Integer dialect;
    private List<QueryFilter> filters = Collections.emptyList();
    private SummarizeOptions summarize;
    private HighlightOptions highlight;
    private List<String> inKeys = Collections.emptyList();
    private List<String> inFields = Collections.emptyList();
    private List<ReturnAttribute> returnAttributes = Collections.emptyList();

    private QueryOptions() {
    }

    public static QueryOptions defaults() {
        return new QueryOptions();
    }

    public QueryOptions filters(QueryFilter... filters) {
        this.filters = Arrays.asList(filters);
        return this;
    }

    public QueryOptions noContent(boolean noContent) {
        this.noContent = noContent;
        return this;
    }

    public QueryOptions verbatim(boolean verbatim) {
        this.verbatim = verbatim;
        return this;
    }

    public QueryOptions noStopwords(boolean noStopwords) {
        this.noStopwords = noStopwords;
        return this;
    }

    public QueryOptions withScores(boolean withScores) {
        this.withScores = withScores;
        return this;
    }

    public QueryOptions withSortKeys(boolean withSortKeys) {
        this.withSortKeys = withSortKeys;
        return this;
    }

    public QueryOptions slop(Integer slop) {
        this.slop = slop;
        return this;
    }

    public QueryOptions timeout(Long timeout) {
        this.timeout = timeout;
        return this;
    }

    public QueryOptions inOrder(boolean inOrder) {
        this.inOrder = inOrder;
        return this;
    }

    public QueryOptions language(String language) {
        this.language = language;
        return this;
    }

    public QueryOptions expander(String expander) {
        this.expander = expander;
        return this;
    }

    public QueryOptions scorer(String scorer) {
        this.scorer = scorer;
        return this;
    }

    public QueryOptions explainScore(boolean explainScore) {
        this.explainScore = explainScore;
        return this;
    }

    public QueryOptions sortBy(String sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    public QueryOptions sortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    public QueryOptions withCount(boolean withCount) {
        this.withCount = withCount;
        return this;
    }

    public QueryOptions limit(int offset, int count) {
        this.offset = offset;
        this.count = count;
        return this;
    }

    public QueryOptions params(Map<String, Object> params) {
        this.params = params;
        return this;
    }

    public QueryOptions dialect(Integer dialect) {
        this.dialect = dialect;
        return this;
    }

    public QueryOptions summarize(SummarizeOptions summarize) {
        this.summarize = summarize;
        return this;
    }

    public QueryOptions highlight(HighlightOptions highlight) {
        this.highlight = highlight;
        return this;
    }

    public QueryOptions inKeys(List<String> inKeys) {
        this.inKeys = inKeys;
        return this;
    }

    public QueryOptions inFields(List<String> inFields) {
        this.inFields = inFields;
        return this;
    }

    public QueryOptions returnAttributes(ReturnAttribute... returnAttributes) {
        return returnAttributes(Arrays.asList(returnAttributes));
    }

    public QueryOptions returnAttributes(List<ReturnAttribute> returnAttributes) {
        this.returnAttributes = returnAttributes;
        return this;
    }

    public boolean isNoContent() {
        return noContent;
    }

    public boolean isVerbatim() {
        return verbatim;
    }

    public boolean isNoStopwords() {
        return noStopwords;
    }

    public boolean isWithScores() {
        return withScores;
    }

    public boolean isWithSortKeys() {
        return withSortKeys;
    }

    public Integer getSlop() {
        return slop;
    }

    public Long getTimeout() {
        return timeout;
    }

    public boolean isInOrder() {
        return inOrder;
    }

    public String getLanguage() {
        return language;
    }

    public String getExpander() {
        return expander;
    }

    public String getScorer() {
        return scorer;
    }

    public boolean isExplainScore() {
        return explainScore;
    }

    public String getSortBy() {
        return sortBy;
    }

    public boolean isWithCount() {
        return withCount;
    }

    public Integer getOffset() {
        return offset;
    }

    public Integer getCount() {
        return count;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Integer getDialect() {
        return dialect;
    }

    public List<QueryFilter> getFilters() {
        return filters;
    }

    public SummarizeOptions getSummarize() {
        return summarize;
    }

    public HighlightOptions getHighlight() {
        return highlight;
    }

    public List<String> getInKeys() {
        return inKeys;
    }

    public List<String> getInFields() {
        return inFields;
    }

    public List<ReturnAttribute> getReturnAttributes() {
        return returnAttributes;
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }
}
