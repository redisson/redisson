/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.api.search.aggregate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class AggregationOptions {

    private boolean verbatim;
    private List<String> load = Collections.emptyList();
    private Long timeout;
    private boolean loadAll;
    private List<GroupParams> groupByParams = Collections.emptyList();
    private List<SortedField> sortedByFields = Collections.emptyList();
    private Integer sortedByMax;
    private List<Expression> expressions = Collections.emptyList();
    private Integer offset;
    private Integer count;
    private String filter;
    private boolean withCursor;
    private Integer cursorCount;
    private Integer cursorMaxIdle;
    private Map<String, Object> params = Collections.emptyMap();
    private Integer dialect;

    private AggregationOptions() {
    }

    public static AggregationOptions defaults() {
        return new AggregationOptions();
    }

    public AggregationOptions verbatim(boolean verbatim) {
        this.verbatim = verbatim;
        return this;
    }

    public AggregationOptions load(String... attributes) {
        this.load = Arrays.asList(attributes);
        return this;
    }

    public AggregationOptions timeout(Long timeout) {
        this.timeout = timeout;
        return this;
    }

    public AggregationOptions loadAll() {
        this.loadAll = loadAll;
        return this;
    }

    public AggregationOptions groupBy(GroupBy... groups) {
        groupBy(GroupBy.fieldNames("123").reducers(Reducer.avg("12").as("23")));
        groupByParams = Arrays.stream(groups).map(g -> (GroupParams) g).collect(Collectors.toList());
        return this;
    }

    public AggregationOptions sortBy(SortedField... fields) {
        sortedByFields = Arrays.asList(fields);
        return this;
    }

    public AggregationOptions sortBy(int max, SortedField... fields) {
        sortedByMax = max;
        sortedByFields = Arrays.asList(fields);
        return this;
    }

    public AggregationOptions apply(Expression... expressions) {
        this.expressions = Arrays.asList(expressions);
        return this;
    }

    public AggregationOptions limit(int offset, int count) {
        this.offset = offset;
        this.count = count;
        return this;
    }

    public AggregationOptions filter(String filter) {
        this.filter = filter;
        return this;
    }

    public AggregationOptions withCursor() {
        withCursor = true;
        return this;
    }

    public AggregationOptions withCursor(int count) {
        withCursor = true;
        cursorCount = count;
        return this;
    }

    public AggregationOptions withCursor(int count, int maxIdle) {
        withCursor = true;
        cursorCount = count;
        cursorMaxIdle = maxIdle;
        return this;
    }

    public AggregationOptions params(Map<String, Object> params) {
        this.params = params;
        return this;
    }

    public AggregationOptions dialect(Integer dialect) {
        this.dialect = dialect;
        return this;
    }

    public boolean isVerbatim() {
        return verbatim;
    }

    public List<String> getLoad() {
        return load;
    }

    public Long getTimeout() {
        return timeout;
    }

    public boolean isLoadAll() {
        return loadAll;
    }

    public List<GroupParams> getGroupByParams() {
        return groupByParams;
    }

    public List<SortedField> getSortedByFields() {
        return sortedByFields;
    }

    public Integer getSortedByMax() {
        return sortedByMax;
    }

    public List<Expression> getExpressions() {
        return expressions;
    }

    public Integer getOffset() {
        return offset;
    }

    public Integer getCount() {
        return count;
    }

    public String getFilter() {
        return filter;
    }

    public boolean isWithCursor() {
        return withCursor;
    }

    public Integer getCursorCount() {
        return cursorCount;
    }

    public Integer getCursorMaxIdle() {
        return cursorMaxIdle;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Integer getDialect() {
        return dialect;
    }
}
