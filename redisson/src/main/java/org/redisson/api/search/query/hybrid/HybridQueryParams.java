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

import org.redisson.api.SortOrder;
import org.redisson.api.search.aggregate.Expression;
import org.redisson.api.search.aggregate.GroupBy;

import java.time.Duration;
import java.util.*;

/**
 * Implementation of hybrid query arguments with fluent builder pattern.
 *
 * @author Nikita Koksharov
 */
public final class HybridQueryParams implements QueryStep, ParamsStep, SortStep, HybridQueryArgs {

    // Query component
    private final String query;
    private String scorer;
    private String queryScoreAlias;

    // Vector similarity component
    private VectorSimilarityParams vectorSimilarityParams;

    // Parameters
    private Map<String, Object> params;

    // Combine component
    private Combine combine;

    // Sort component
    private String sortFieldName;
    private SortOrder sortOrder;
    private boolean noSort;

    // Final options
    private List<String> loadFields;
    private Integer limitOffset;
    private Integer limitCount;
    private List<GroupBy> groupBy;
    private List<Expression> expressions;
    private Duration timeout;
    private String postFilter;

    HybridQueryParams(String query) {
        this.query = query;
    }

    @Override
    public QueryStep scorer(String scorer) {
        this.scorer = scorer;
        return this;
    }

    @Override
    public QueryStep scoreAlias(String alias) {
        this.queryScoreAlias = alias;
        return this;
    }

    @Override
    public ParamsStep vectorSimilarity(VectorSimilarity value) {
        this.vectorSimilarityParams = (VectorSimilarityParams) value;
        return this;
    }

    @Override
    public SortStep params(Map<String, Object> params) {
        this.params = params;
        return this;
    }

    @Override
    public HybridQueryArgs sortBy(String fieldName, SortOrder order) {
        this.sortFieldName = fieldName;
        this.sortOrder = order;
        return this;
    }

    @Override
    public HybridQueryArgs noSort() {
        this.noSort = true;
        return this;
    }

    @Override
    public HybridQueryArgs combine(Combine value) {
        this.combine = value;
        return this;
    }

    @Override
    public HybridQueryArgs load(String... fields) {
        this.loadFields = Arrays.asList(fields);
        return this;
    }

    @Override
    public HybridQueryArgs limit(int offset, int count) {
        this.limitOffset = offset;
        this.limitCount = count;
        return this;
    }

    @Override
    public HybridQueryArgs groupBy(GroupBy... groups) {
        this.groupBy = Arrays.asList(groups);
        return this;
    }

    @Override
    public HybridQueryArgs apply(Expression... expressions) {
        this.expressions = Arrays.asList(expressions);
        return this;
    }

    @Override
    public HybridQueryArgs timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public HybridQueryArgs filter(String value) {
        this.postFilter = value;
        return this;
    }

    public String getQuery() {
        return query;
    }

    public String getScorer() {
        return scorer;
    }

    public String getQueryScoreAlias() {
        return queryScoreAlias;
    }

    public VectorSimilarityParams getVectorSimilarityParams() {
        return vectorSimilarityParams;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Combine getCombine() {
        return combine;
    }

    public String getSortFieldName() {
        return sortFieldName;
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public boolean isNoSort() {
        return noSort;
    }

    public List<String> getLoadFields() {
        return loadFields;
    }

    public Integer getLimitOffset() {
        return limitOffset;
    }

    public Integer getLimitCount() {
        return limitCount;
    }

    public List<GroupBy> getGroupBy() {
        return groupBy;
    }

    public List<Expression> getExpressions() {
        return expressions;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public String getPostFilter() {
        return postFilter;
    }
}