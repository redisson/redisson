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
package org.redisson.api;

import org.redisson.api.search.SpellcheckOptions;
import org.redisson.api.search.aggregate.AggregationOptions;
import org.redisson.api.search.aggregate.AggregationResult;
import org.redisson.api.search.index.FieldIndex;
import org.redisson.api.search.index.IndexInfo;
import org.redisson.api.search.index.IndexOptions;
import org.redisson.api.search.query.QueryOptions;
import org.redisson.api.search.query.SearchResult;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Reactive API for RediSearch module
 *
 * @author Nikita Koksharov
 *
 */
public interface RSearchReactive {

    /**
     * Creates an index.
     * <p>
     * Code example:
     * <pre>
     *             search.create("idx", IndexOptions.defaults()
     *                                     .on(IndexType.HASH)
     *                                     .prefix(Arrays.asList("doc:")),
     *                                     FieldIndex.text("t1"),
     *                                     FieldIndex.tag("t2").withSuffixTrie());
     * </pre>
     *
     * @param indexName index name
     * @param options index options
     * @param fields fields
     */
    Mono<Void> createIndex(String indexName, IndexOptions options, FieldIndex... fields);

    /**
     * Executes search over defined index using defined query.
     * <p>
     * Code example:
     * <pre>
     * SearchResult r = s.search("idx", "*", QueryOptions.defaults()
     *                                                   .returnAttributes(new ReturnAttribute("t1"), new ReturnAttribute("t2")));
     * </pre>
     *
     * @param indexName index name
     * @param query query value
     * @param options query options
     * @return search result
     */
    Mono<SearchResult> search(String indexName, String query, QueryOptions options);

    /**
     * Executes aggregation over defined index using defined query.
     * <p>
     * Code example:
     * <pre>
     * AggregationResult r = s.aggregate("idx", "*", AggregationOptions.defaults()
     *                                                                 .load("t1", "t2"));
     * </pre>
     *
     * @param indexName index name
     * @param query query value
     * @param options aggregation options
     * @return aggregation result
     */
    Mono<AggregationResult> aggregate(String indexName, String query, AggregationOptions options);

    /**
     * Adds alias to defined index name
     *
     * @param alias alias value
     * @param indexName index name
     */
    Mono<Void> addAlias(String alias, String indexName);

    /**
     * Deletes index alias
     *
     * @param alias alias value
     */
    Mono<Void> delAlias(String alias);

    /**
     * Adds alias to defined index name.
     * Re-assigns the alias if it was used before with a different index.
     *
     * @param alias alias value
     * @param indexName index name
     */
    Mono<Void> updateAlias(String alias, String indexName);

    /**
     * Adds a new attribute to the index
     *
     * @param indexName index name
     * @param skipInitialScan doesn't scan the index if <code>true</code>
     * @param fields field indexes
     */
    Mono<Void> alter(String indexName, boolean skipInitialScan, FieldIndex... fields);

    /**
     * Returns configuration map by defined parameter name
     *
     * @param parameter parameter name
     * @return configuration map
     */
    Mono<Map<String, String>> getConfig(String parameter);

    /**
     * Sets configuration value by the parameter name
     *
     * @param parameter parameter name
     * @param value parameter value
     */
    Mono<Void> setConfig(String parameter, String value);

    /**
     * Deletes cursor by index name and id
     *
     * @param indexName index name
     * @param cursorId cursor id
     */
    Mono<Void> delCursor(String indexName, long cursorId);

    /**
     * Returns next results by index name and cursor id
     *
     * @param indexName index name
     * @param cursorId cursor id
     * @return aggregation result
     */
    Mono<AggregationResult> readCursor(String indexName, long cursorId);

    /**
     * Returns next results by index name, cursor id and results size
     *
     * @param indexName index name
     * @param cursorId cursor id
     * @param count results size
     * @return aggregation result
     */
    Mono<AggregationResult> readCursor(String indexName, long cursorId, int count);

    /**
     * Adds defined terms to the dictionary
     *
     * @param dictionary dictionary name
     * @param terms terms
     * @return number of new terms
     */
    Mono<Long> addDict(String dictionary, String... terms);

    /**
     * Deletes defined terms from the dictionary
     *
     * @param dictionary dictionary name
     * @param terms terms
     * @return number of deleted terms
     */
    Mono<Long> delDict(String dictionary, String... terms);

    /**
     * Returns terms stored in the dictionary
     *
     * @param dictionary dictionary name
     * @return terms
     */
    Mono<List<String>> dumpDict(String dictionary);

    /**
     * Deletes index by name
     *
     * @param indexName index name
     */
    Mono<Void> dropIndex(String indexName);

    /**
     * Deletes index by name and associated documents
     *
     * @param indexName index name
     */
    Mono<Void> dropIndexAndDocuments(String indexName);

    /**
     * Returns index info by name
     *
     * @param indexName index name
     * @return index info
     */
    Mono<IndexInfo> info(String indexName);

    /**
     * Executes spell checking by defined index name and query.
     * <pre>
     * Map<String, Map<String, Double>> res = s.spellcheck("idx", "Hocke sti", SpellcheckOptions.defaults()
     *                                                                                          .includedTerms("name"));
     * </pre>
     *
     * @param indexName index name
     * @param query query
     * @param options spell checking options
     * @return result
     */
    Mono<Map<String, Map<String, Double>>> spellcheck(String indexName, String query, SpellcheckOptions options);

    /**
     * Returns synonyms mapped by word by defined index name
     *
     * @param indexName index name
     * @return synonyms map
     */
    Mono<Map<String, List<String>>> dumpSynonyms(String indexName);

    /**
     * Updates synonyms
     *
     * @param indexName index name
     * @param synonymGroupId synonym group id
     * @param terms terms
     */
    Mono<Void> updateSynonyms(String indexName, String synonymGroupId, String... terms);

    /**
     * Returns list of all created indexes
     *
     * @return list of indexes
     */
    Mono<List<String>> getIndexes();
}
