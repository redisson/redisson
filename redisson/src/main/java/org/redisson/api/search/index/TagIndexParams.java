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
package org.redisson.api.search.index;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class TagIndexParams implements TagIndex {

    private final String fieldName;
    private String as;
    private SortMode sortMode;
    private boolean noIndex;
    private boolean caseSensitive;
    private boolean withSuffixTrie;
    private String separator;
    private boolean indexEmpty;

    TagIndexParams(String name) {
        this.fieldName = name;
    }

    public TagIndexParams as(String as) {
        this.as = as;
        return this;
    }

    public TagIndexParams separator(String separator) {
        if (separator.length() != 1) {
            throw new IllegalArgumentException("Separator should be a single character");
        }
        this.separator = separator;
        return this;
    }

    public TagIndexParams sortMode(SortMode sortMode) {
        this.sortMode = sortMode;
        return this;
    }

    public TagIndexParams caseSensitive() {
        caseSensitive = true;
        return this;
    }

    public TagIndexParams noIndex() {
        noIndex = true;
        return this;
    }

    public TagIndexParams withSuffixTrie() {
        withSuffixTrie = true;
        return this;
    }

    public TagIndexParams indexEmpty() {
        this.indexEmpty = true;
        return this;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getAs() {
        return as;
    }

    public SortMode getSortMode() {
        return sortMode;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public boolean isNoIndex() {
        return noIndex;
    }

    public boolean isWithSuffixTrie() {
        return withSuffixTrie;
    }

    public String getSeparator() {
        return separator;
    }

    public boolean isIndexEmpty() {
        return indexEmpty;
    }

}

