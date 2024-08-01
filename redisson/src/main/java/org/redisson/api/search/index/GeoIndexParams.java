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
public final class GeoIndexParams implements GeoIndex {

    private SortMode sortMode;
    private boolean noIndex;
    private String fieldName;
    private String as;
    private boolean indexMissing;

    GeoIndexParams(String name) {
        this.fieldName = name;
    }

    @Override
    public GeoIndexParams as(String as) {
        this.as = as;
        return this;
    }

    @Override
    public GeoIndexParams sortMode(SortMode sortMode) {
        this.sortMode = sortMode;
        return this;
    }

    @Override
    public GeoIndexParams noIndex() {
        this.noIndex = true;
        return this;
    }

    @Override
    public GeoIndexParams indexMissing() {
        this.indexMissing = true;
        return this;
    }

    public SortMode getSortMode() {
        return sortMode;
    }

    public boolean isNoIndex() {
        return noIndex;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getAs() {
        return as;
    }

    public boolean isIndexMissing() {
        return indexMissing;
    }
}
