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
package org.redisson.api.search.query;

import java.util.List;

/**
 * Search result object returned by
 * {@link org.redisson.api.RSearch#search(String, String, QueryOptions)} method
 *
 * @author Nikita Koksharov
 *
 */
public final class SearchResult {

    private long total;

    private List<Document> documents;

    public SearchResult(long total, List<Document> documents) {
        this.total = total;
        this.documents = documents;
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
     * Returns list of found Document objects
     *
     * @return list of found Document objects
     */
    public List<Document> getDocuments() {
        return documents;
    }
}
