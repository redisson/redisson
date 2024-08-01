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
public final class TextIndexParams implements TextIndex {

    private final String fieldName;
    private String as;
    private SortMode sortMode;
    private boolean noIndex;
    private boolean noStem;
    private PhoneticMatcher matcher;
    private boolean withSuffixTrie;
    private Double weight;
    private boolean indexEmpty;
    private boolean indexMissing;

    TextIndexParams(String name) {
        this.fieldName = name;
    }

    @Override
    public TextIndexParams as(String as) {
        this.as = as;
        return this;
    }

    @Override
    public TextIndexParams sortMode(SortMode sortMode) {
        this.sortMode = sortMode;
        return this;
    }

    @Override
    public TextIndexParams noStem() {
        noStem = true;
        return this;
    }

    @Override
    public TextIndexParams noIndex() {
        noIndex = true;
        return this;
    }

    @Override
    public TextIndexParams withSuffixTrie() {
        withSuffixTrie = true;
        return this;
    }

    @Override
    public TextIndexParams phonetic(PhoneticMatcher matcher) {
        this.matcher = matcher;
        return this;
    }

    @Override
    public TextIndexParams weight(Double weight) {
        this.weight = weight;
        return this;
    }

    @Override
    public TextIndexParams indexEmpty() {
        this.indexEmpty = true;
        return this;
    }

    @Override
    public TextIndexParams indexMissing() {
        this.indexMissing = true;
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

    public boolean isNoIndex() {
        return noIndex;
    }

    public boolean isNoStem() {
        return noStem;
    }

    public PhoneticMatcher getMatcher() {
        return matcher;
    }

    public boolean isWithSuffixTrie() {
        return withSuffixTrie;
    }

    public Double getWeight() {
        return weight;
    }

    public boolean isIndexEmpty() {
        return indexEmpty;
    }

    public boolean isIndexMissing() {
        return indexMissing;
    }
}
