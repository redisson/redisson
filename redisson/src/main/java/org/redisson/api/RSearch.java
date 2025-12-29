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
import org.redisson.api.search.aggregate.AggregationEntry;
import org.redisson.api.search.aggregate.AggregationOptions;
import org.redisson.api.search.aggregate.AggregationResult;
import org.redisson.api.search.aggregate.IterableAggregationOptions;
import org.redisson.api.search.index.IndexInfo;
import org.redisson.api.search.index.IndexOptions;
import org.redisson.api.search.index.FieldIndex;
import org.redisson.api.search.query.hybrid.HybridSearchResult;
import org.redisson.api.search.query.QueryOptions;
import org.redisson.api.search.query.SearchResult;
import org.redisson.api.search.query.hybrid.HybridQueryArgs;

import java.util.List;
import java.util.Map;

/**
 * API for RediSearch module
 *
 * @author Nikita Koksharov
 *
 */
public interface RSearch extends RSearchAsync {

    /**
     * Creates an index.
     * <p>
     * Code example:
     * <pre>
     *             search.createIndex("idx", IndexOptions.defaults()
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
    void createIndex(String indexName, IndexOptions options, FieldIndex... fields);

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
    SearchResult search(String indexName, String query, QueryOptions options);

    /**
     * Performs hybrid search combining text search and vector similarity
     * using the FT.HYBRID command.
     * <p>
     * Requires Redis Stack 8.4.0 or higher.
     * <p>
     * Usage example:
     * <pre>
     * SearchResult result = search.hybridSearch("myIndex",
     *     HybridQueryArgs.query("laptop")
     *         .vectorSimilarity("@embedding", "$vec")
     *         .nearestNeighbors(10)
     *         .params(Map.of("vec", vectorBytes))
     *         .limit(0, 10));
     * </pre>
     *
     * @param indexName the name of the index
     * @param args hybrid query arguments
     * @return search result
     */
    HybridSearchResult hybridSearch(String indexName, HybridQueryArgs args);

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
    AggregationResult aggregate(String indexName, String query, AggregationOptions options);

    /**
     * Executes aggregation over defined index using defined query.
     * <p>
     * Code example:
     * <pre>
     * Iterable<AggregationEntry> r = s.aggregate("idx", "*", IterableAggregationOptions.defaults()
     *                                                                 .load("t1", "t2"));
     * </pre>
     *
     * @param indexName index name
     * @param query query value
     * @param options iterable aggregationOptions options
     * @return iterable aggregation result
     */
    Iterable<AggregationEntry> aggregate(String indexName, String query, IterableAggregationOptions options);

    /**
     * Adds alias to defined index name
     *
     * @param alias alias value
     * @param indexName index name
     */
    void addAlias(String alias, String indexName);

    /**
     * Deletes index alias
     *
     * @param alias alias value
     */
    void delAlias(String alias);

    /**
     * Adds alias to defined index name.
     * Re-assigns the alias if it was used before with a different index.
     *
     * @param alias alias value
     * @param indexName index name
     */
    void updateAlias(String alias, String indexName);

    /**
     * Adds a new attribute to the index
     *
     * @param indexName index name
     * @param skipInitialScan doesn't scan the index if <code>true</code>
     * @param fields field indexes
     */
    void alter(String indexName, boolean skipInitialScan, FieldIndex... fields);

    /**
     * Returns configuration map by defined parameter name
     *
     * @param parameter parameter name
     * @return configuration map
     */
    Map<String, String> getConfig(String parameter);

    /**
     * Sets configuration value by the parameter name
     *
     * @param parameter parameter name
     * @param value parameter value
     */
    void setConfig(String parameter, String value);

    /**
     * Deletes cursor by index name and id
     *
     * @param indexName index name
     * @param cursorId cursor id
     */
    void delCursor(String indexName, long cursorId);

    /**
     * Returns next results by index name and cursor id
     *
     * @param indexName index name
     * @param cursorId cursor id
     * @return aggregation result
     */
    AggregationResult readCursor(String indexName, long cursorId);

    /**
     * Returns next results by index name, cursor id and results size
     *
     * @param indexName index name
     * @param cursorId cursor id
     * @param count results size
     * @return aggregation result
     */
    AggregationResult readCursor(String indexName, long cursorId, int count);

    /**
     * Adds defined terms to the dictionary
     *
     * @param dictionary dictionary name
     * @param terms terms
     * @return number of new terms
     */
    long addDict(String dictionary, String... terms);

    /**
     * Deletes defined terms from the dictionary
     *
     * @param dictionary dictionary name
     * @param terms terms
     * @return number of deleted terms
     */
    long delDict(String dictionary, String... terms);

    /**
     * Returns terms stored in the dictionary
     *
     * @param dictionary dictionary name
     * @return terms
     */
    List<String> dumpDict(String dictionary);

    /**
     * Deletes index by name
     *
     * @param indexName index name
     */
    void dropIndex(String indexName);

    /**
     * Deletes index by name and associated documents.
     * Associated documents are deleted asynchronously.
     * Method {@link #info(String)} can be used to check for process completion.
     *
     * @param indexName index name
     */
    void dropIndexAndDocuments(String indexName);

    /**
     * Returns index info by name
     *
     * @param indexName index name
     * @return index info
     */
    IndexInfo info(String indexName);

    /**
     * Executes spell checking by defined index name and query.
     * Returns a map of misspelled terms and their score.
     *
     * <pre>
     * Map<String, Map<String, Double>> res = s.spellcheck("idx", "Hocke sti", SpellcheckOptions.defaults()
     *                                                                                          .includedTerms("name"));
     * </pre>
     *
     * @param indexName index name
     * @param query query
     * @param options spell checking options
     * @return map of misspelled terms and their score
     */
    Map<String, Map<String, Double>> spellcheck(String indexName, String query, SpellcheckOptions options);

    /**
     * Returns synonyms mapped by word by defined index name
     *
     * @param indexName index name
     * @return synonyms map
     */
    Map<String, List<String>> dumpSynonyms(String indexName);

    /**
     * Updates synonyms
     *
     * @param indexName index name
     * @param synonymGroupId synonym group id
     * @param terms terms
     */
    void updateSynonyms(String indexName, String synonymGroupId, String... terms);

    /**
     * Returns list of all created indexes
     *
     * @return list of indexes
     */
    List<String> getIndexes();

}
