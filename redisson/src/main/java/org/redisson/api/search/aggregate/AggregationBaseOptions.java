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
package org.redisson.api.search.aggregate;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * @author seakider
 *
 */
public class AggregationBaseOptions<T> {
    private boolean verbatim;
    private List<String> load = Collections.emptyList();
    private Long timeout;
    private boolean loadAll;
    private List<GroupParams> groupByParams = Collections.emptyList();
    private List<SortedField> sortedByFields = Collections.emptyList();
    private Integer sortedByMax;
    private boolean sortedByWithCount;
    private List<Expression> expressions = Collections.emptyList();
    private Integer offset;
    private Integer count;
    private String filter;
    protected boolean withCursor;
    protected Integer cursorCount;
    protected Duration cursorMaxIdle;
    private Map<String, Object> params = Collections.emptyMap();
    private Integer dialect;

    protected AggregationBaseOptions() {
    }

    public T verbatim(boolean verbatim) {
        this.verbatim = verbatim;
        return (T) this;
    }

    public T load(String... attributes) {
        this.load = Arrays.asList(attributes);
        return (T) this;
    }

    public T timeout(Long timeout) {
        this.timeout = timeout;
        return (T) this;
    }

    public T loadAll() {
        this.loadAll = true;
        return (T) this;
    }

    public T groupBy(GroupBy... groups) {
        groupByParams = Arrays.stream(groups).map(g -> (GroupParams) g).collect(Collectors.toList());
        return (T) this;
    }

    public T sortBy(SortedField... fields) {
        sortedByFields = Arrays.asList(fields);
        return (T) this;
    }

    public T sortBy(int max, SortedField... fields) {
        sortedByMax = max;
        sortedByFields = Arrays.asList(fields);
        return (T) this;
    }

    public T sortBy(boolean withCount, SortedField... fields) {
        sortedByWithCount = withCount;
        sortedByFields = Arrays.asList(fields);
        return (T) this;
    }

    public T sortBy(int max, boolean withCount, SortedField... fields) {
        sortedByMax = max;
        sortedByWithCount = withCount;
        sortedByFields = Arrays.asList(fields);
        return (T) this;
    }

    public T apply(Expression... expressions) {
        this.expressions = Arrays.asList(expressions);
        return (T) this;
    }

    public T limit(int offset, int count) {
        this.offset = offset;
        this.count = count;
        return (T) this;
    }

    public T filter(String filter) {
        this.filter = filter;
        return (T) this;
    }

    protected T cursorCount(int count) {
        cursorCount = count;
        return (T) this;
    }

    protected T withCursor() {
        withCursor = true;
        return (T) this;
    }

    protected T cursorMaxIdle(Duration duration) {
        cursorMaxIdle = duration;
        return (T) this;
    }

    public T params(Map<String, Object> params) {
        this.params = params;
        return (T) this;
    }

    public T dialect(Integer dialect) {
        this.dialect = dialect;
        return (T) this;
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

    public boolean isSortedByWithCount() {
        return sortedByWithCount;
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

    public Duration getCursorMaxIdle() {
        return cursorMaxIdle;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Integer getDialect() {
        return dialect;
    }
}