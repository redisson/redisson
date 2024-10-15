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
package org.redisson;

import org.redisson.api.RFuture;
import org.redisson.api.RSearch;
import org.redisson.api.search.SpellcheckOptions;
import org.redisson.api.search.aggregate.*;
import org.redisson.api.search.index.*;
import org.redisson.api.search.query.*;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.EmptyMapConvertor;
import org.redisson.client.protocol.decoder.*;
import org.redisson.codec.CompositeCodec;
import org.redisson.command.CommandAsyncExecutor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonSearch implements RSearch {

    private final Codec codec;
    private final CommandAsyncExecutor commandExecutor;

    public RedissonSearch(CommandAsyncExecutor commandExecutor) {
        this.codec = commandExecutor.getServiceManager().getCfg().getCodec();
        this.commandExecutor = commandExecutor;
    }

    public RedissonSearch(Codec codec, CommandAsyncExecutor commandExecutor) {
        this.codec = commandExecutor.getServiceManager().getCodec(codec);
        this.commandExecutor = commandExecutor;
    }

    @Override
    public void createIndex(String indexName, IndexOptions options, FieldIndex... fields) {
        commandExecutor.get(createIndexAsync(indexName, options, fields));
    }

    @Override
    public RFuture<Void> createIndexAsync(String indexName, IndexOptions options, FieldIndex... fields) {
        if (fields.length == 0) {
            throw new IllegalArgumentException("At least one field index should be defined");
        }
        List<Object> args = new ArrayList<>();
        args.add(indexName);
        if (options.getOn() != null) {
            args.add("ON");
            args.add(options.getOn());
        }
        if (!options.getPrefix().isEmpty()) {
            args.add("PREFIX");
            args.add(options.getPrefix().size());
            args.addAll(options.getPrefix());
        }
        if (options.getFilter() != null) {
            args.add("FILTER");
            args.add(options.getFilter());
        }
        if (options.getLanguage() != null) {
            args.add("LANGUAGE");
            args.add(options.getLanguage());
        }
        if (options.getLanguageField() != null) {
            args.add("LANGUAGE_FIELD");
            args.add(options.getLanguageField());
        }
        if (options.getScore() != null) {
            args.add("SCORE");
            args.add(options.getScore());
        }
        if (options.getScoreField() != null) {
            args.add("SCORE_FIELD");
            args.add(options.getScoreField());
        }
        if (options.getPayloadField() != null) {
            args.add("PAYLOAD_FIELD");
            args.add(options.getPayloadField());
        }
        if (options.isMaxTextFields()) {
            args.add("MAXTEXTFIELDS");
        }
        if (options.getTemporary() != null) {
            args.add("TEMPORARY");
            args.add(options.getTemporary());
        }
        if (options.isNoOffsets()) {
            args.add("NOOFFSETS");
        }
        if (options.isNoHL()) {
            args.add("NOHL");
        }
        if (options.isNoFields()) {
            args.add("NOFIELDS");
        }
        if (options.isNoFreqs()) {
            args.add("NOFREQS");
        }
        if (!options.getStopwords().isEmpty()) {
            args.add("STOPWORDS");
            args.add(options.getStopwords().size());
            args.addAll(options.getStopwords());
        }
        if (options.isSkipInitialScan()) {
            args.add("SKIPINITIALSCAN");
        }

        args.add("SCHEMA");
        for (FieldIndex field : fields) {
            addTextIndex(args, field);
            addTagIndex(args, field);
            addGeoIndex(args, field);
            addNumericIndex(args, field);
            addFlatVectorIndex(args, field);
            addHNSWVectorIndex(args, field);
        }

        return commandExecutor.writeAsync(indexName, StringCodec.INSTANCE, RedisCommands.FT_CREATE, args.toArray());
    }

    private static void addHNSWVectorIndex(List<Object> args, FieldIndex field) {
        if (field instanceof HNSWVectorIndex) {
            HNSWVectorIndexParams params = (HNSWVectorIndexParams) field;
            args.add(params.getFieldName());
            if (params.getAs() != null) {
                args.add("AS");
                args.add(params.getAs());
            }
            args.add("VECTOR");
            args.add("HNSW");
            args.add(params.getCount()*2);
            args.add("TYPE");
            args.add(params.getType());
            args.add("DIM");
            args.add(params.getDim());
            args.add("DISTANCE_METRIC");
            args.add(params.getDistanceMetric());

            if (params.getInitialCap() != null) {
                args.add("INITIAL_CAP");
                args.add(params.getInitialCap());
            }
            if (params.getM() != null) {
                args.add("M");
                args.add(params.getM());
            }
            if (params.getEfConstruction() != null) {
                args.add("EF_CONSTRUCTION");
                args.add(params.getEfConstruction());
            }
            if (params.getEfRuntime() != null) {
                args.add("EF_RUNTIME");
                args.add(params.getEfRuntime());
            }
            if (params.getEpsilon() != null) {
                args.add("EPSILON");
                args.add(params.getEpsilon());
            }
        }
    }

    private static void addFlatVectorIndex(List<Object> args, FieldIndex field) {
        if (field instanceof FlatVectorIndex) {
            FlatVectorIndexParams params = (FlatVectorIndexParams) field;
            args.add(params.getFieldName());
            if (params.getAs() != null) {
                args.add("AS");
                args.add(params.getAs());
            }
            args.add("VECTOR");
            args.add("FLAT");
            args.add(params.getCount()*2);
            args.add("TYPE");
            args.add(params.getType());
            args.add("DIM");
            args.add(params.getDim());
            args.add("DISTANCE_METRIC");
            args.add(params.getDistanceMetric());

            if (params.getInitialCapacity() != null) {
                args.add("INITIAL_CAP");
                args.add(params.getInitialCapacity());
            }
            if (params.getBlockSize() != null) {
                args.add("BLOCK_SIZE");
                args.add(params.getBlockSize());
            }
        }
    }

    private static void addNumericIndex(List<Object> args, FieldIndex field) {
        if (field instanceof NumericIndex) {
            NumericIndexParams params = (NumericIndexParams) field;
            args.add(params.getFieldName());
            if (params.getAs() != null) {
                args.add("AS");
                args.add(params.getAs());
            }
            args.add("NUMERIC");
            if (params.getSortMode() != null) {
                args.add("SORTABLE");
                if (params.getSortMode() == SortMode.UNNORMALIZED) {
                    args.add("UNF");
                }
            }
            if (params.isNoIndex()) {
                args.add("NOINDEX");
            }
            if (params.isIndexMissing()) {
                args.add("INDEXMISSING");
            }
        }
    }

    private static void addGeoIndex(List<Object> args, FieldIndex field) {
        if (field instanceof GeoIndex) {
            GeoIndexParams params = (GeoIndexParams) field;
            args.add(params.getFieldName());
            if (params.getAs() != null) {
                args.add("AS");
                args.add(params.getAs());
            }
            args.add("GEO");
            if (params.getSortMode() != null) {
                args.add("SORTABLE");
                if (params.getSortMode() == SortMode.UNNORMALIZED) {
                    args.add("UNF");
                }
            }
            if (params.isNoIndex()) {
                args.add("NOINDEX");
            }
            if (params.isIndexMissing()) {
                args.add("INDEXMISSING");
            }
        }
    }

    private static void addTagIndex(List<Object> args, FieldIndex field) {
        if (field instanceof TagIndexParams) {
            TagIndexParams params = (TagIndexParams) field;
            args.add(params.getFieldName());
            if (params.getAs() != null) {
                args.add("AS");
                args.add(params.getAs());
            }
            args.add("TAG");
            if (params.isCaseSensitive()) {
                args.add("CASESENSITIVE");
            }
            if (params.getSeparator() != null) {
                args.add("SEPARATOR");
                args.add(params.getSeparator());
            }
            if (params.isWithSuffixTrie()) {
                args.add("WITHSUFFIXTRIE");
            }
            if (params.getSortMode() != null) {
                args.add("SORTABLE");
                if (params.getSortMode() == SortMode.UNNORMALIZED) {
                    args.add("UNF");
                }
            }
            if (params.isNoIndex()) {
                args.add("NOINDEX");
            }
            if (params.isIndexEmpty()) {
                args.add("INDEXEMPTY");
            }
            if (params.isIndexMissing()) {
                args.add("INDEXMISSING");
            }
        }
    }

    private static void addTextIndex(List<Object> args, FieldIndex field) {
        if (field instanceof TextIndexParams) {
            TextIndexParams params = (TextIndexParams) field;
            args.add(params.getFieldName());
            if (params.getAs() != null) {
                args.add("AS");
                args.add(params.getAs());
            }
            args.add("TEXT");
            if (params.isNoStem()) {
                args.add("NOSTEM");
            }
            if (params.getMatcher() != null) {
                args.add("PHONETIC");
                args.add(params.getMatcher());
            }
            if (params.getWeight() != null) {
                args.add("WEIGHT");
                args.add(params.getWeight());
            }
            if (params.isWithSuffixTrie()) {
                args.add("WITHSUFFIXTRIE");
            }
            if (params.getSortMode() != null) {
                args.add("SORTABLE");
                if (params.getSortMode() == SortMode.UNNORMALIZED) {
                    args.add("UNF");
                }
            }
            if (params.isNoIndex()) {
                args.add("NOINDEX");
            }
            if (params.isIndexEmpty()) {
                args.add("INDEXEMPTY");
            }
            if (params.isIndexMissing()) {
                args.add("INDEXMISSING");
            }
        }
    }

    @Override
    public SearchResult search(String indexName, String query, QueryOptions options) {
        return commandExecutor.get(searchAsync(indexName, query, options));
    }

    @SuppressWarnings("MethodLength")
    @Override
    public RFuture<SearchResult> searchAsync(String indexName, String query, QueryOptions options) {
        List<Object> args = new ArrayList<>();
        args.add(indexName);
        args.add(query);

        if (options.isNoContent()) {
            args.add("NOCONTENT");
        }
        if (options.isVerbatim()) {
            args.add("VERBATIM");
        }
        if (options.isNoStopwords()) {
            args.add("NOSTOPWORDS");
        }
        if (options.isWithScores()) {
            args.add("WITHSCORES");
        }
        if (options.isWithSortKeys()) {
            args.add("WITHSORTKEYS");
        }
        for (QueryFilter filter : options.getFilters()) {
            if (filter instanceof NumericFilterParams) {
                NumericFilterParams params = (NumericFilterParams) filter;
                args.add("FILTER");
                args.add(params.getFieldName());
                args.add(value(params.getMin(), params.isMinExclusive()));
                args.add(value(params.getMax(), params.isMaxExclusive()));
            }
        }
        for (QueryFilter filter : options.getFilters()) {
            if (filter instanceof GeoFilterParams) {
                GeoFilterParams params = (GeoFilterParams) filter;
                args.add("GEOFILTER");
                args.add(params.getFieldName());
                args.add(params.getLongitude());
                args.add(params.getLatitude());
                args.add(params.getRadius());
                args.add(params.getUnit());
            }
        }
        if (!options.getInKeys().isEmpty()) {
            args.add("INKEYS");
            args.add(options.getInKeys().size());
            args.addAll(options.getInKeys());
        }
        if (!options.getInFields().isEmpty()) {
            args.add("INFIELDS");
            args.add(options.getInFields().size());
            args.addAll(options.getInFields());
        }
        if (!options.getReturnAttributes().isEmpty()) {
            args.add("RETURN");
            args.add(options.getReturnAttributes().size());
            int pos = args.size() - 1;
            int amount = 0;
            for (ReturnAttribute attr : options.getReturnAttributes()) {
                args.add(attr.getIdentifier());
                amount++;
                if (attr.getProperty() != null) {
                    args.add("AS");
                    args.add(attr.getProperty());
                    amount += 2;
                }
            }
            args.set(pos, amount);
        }
        if (options.getSummarize() != null) {
            args.add("SUMMARIZE");
            if (!options.getSummarize().getFields().isEmpty()) {
                args.add("FIELDS");
                args.add(options.getSummarize().getFields().size());
                args.addAll(options.getSummarize().getFields());
            }
            if (options.getSummarize().getFragsNum() != null) {
                args.add("FRAGS");
                args.add(options.getSummarize().getFragsNum());
            }
            if (options.getSummarize().getFragSize() != null) {
                args.add("LEN");
                args.add(options.getSummarize().getFragSize());
            }
            if (options.getSummarize().getSeparator() != null) {
                args.add("SEPARATOR");
                args.add(options.getSummarize().getSeparator());
            }
        }
        if (options.getHighlight() != null) {
            args.add("HIGHLIGHT");
            if (!options.getHighlight().getFields().isEmpty()) {
                args.add("FIELDS");
                args.add(options.getHighlight().getFields().size());
                args.addAll(options.getHighlight().getFields());
            }
            if (options.getHighlight().getOpenTag() != null
                    && options.getHighlight().getCloseTag() != null) {
                args.add("TAGS");
                args.add(options.getHighlight().getOpenTag());
                args.add(options.getHighlight().getCloseTag());
            }
        }
        if (options.getSlop() != null) {
            args.add("SLOP");
            args.add(options.getSlop());
        }
        if (options.getTimeout() != null) {
            args.add("TIMEOUT");
            args.add(options.getTimeout());
        }
        if (options.isInOrder()) {
            args.add("INORDER");
        }
        if (options.getLanguage() != null) {
            args.add("LANGUAGE");
            args.add(options.getLanguage());
        }
        if (options.getExpander() != null) {
            args.add("EXPANDER");
            args.add(options.getExpander());
        }
        if (options.getScorer() != null) {
            args.add("SCORER");
            args.add(options.getScorer());
        }
        if (options.isExplainScore()) {
            args.add("EXPLAINSCORE");
        }
        if (options.getSortBy() != null) {
            args.add("SORTBY");
            args.add(options.getSortBy());
            if (options.getSortOrder() != null) {
                args.add(options.getSortOrder());
            }
            if (options.isWithCount()) {
                args.add("WITHCOUNT");
            }
        }
        if (options.getOffset() != null
                && options.getCount() != null) {
            args.add("LIMIT");
            args.add(options.getOffset());
            args.add(options.getCount());
        }
        if (!options.getParams().isEmpty()) {
            args.add("PARAMS");
            args.add(options.getParams().size()*2);
            for (Map.Entry<String, Object> entry : options.getParams().entrySet()) {
                args.add(entry.getKey());
                args.add(entry.getValue());
            }
        }
        if (options.getDialect() != null) {
            args.add("DIALECT");
            args.add(options.getDialect());
        }

        RedisStrictCommand<SearchResult> command;
        if (commandExecutor.getServiceManager().isResp3()) {
            command = new RedisStrictCommand<>("FT.SEARCH",
                    new ListMultiDecoder2(new SearchResultDecoderV2(),
                            new ObjectListReplayDecoder(),
                            new ObjectMapReplayDecoder(),
                            new ObjectMapReplayDecoder(new CompositeCodec(StringCodec.INSTANCE, codec))));
        } else {
            command = new RedisStrictCommand<>("FT.SEARCH",
                    new ListMultiDecoder2(new SearchResultDecoder(),
                                            new ObjectMapReplayDecoder(new CompositeCodec(StringCodec.INSTANCE, codec)),
                                            new ObjectListReplayDecoder()));
        }

        return commandExecutor.writeAsync(indexName, StringCodec.INSTANCE, command, args.toArray());
    }

    private String value(double score, boolean exclusive) {
        StringBuilder element = new StringBuilder();
        if (Double.isInfinite(score)) {
            if (score > 0) {
                element.append("+inf");
            } else {
                element.append("-inf");
            }
        } else {
            if (exclusive) {
                element.append("(");
            }
            element.append(BigDecimal.valueOf(score).toPlainString());
        }
        return element.toString();
    }

    @Override
    public AggregationResult aggregate(String indexName, String query, AggregationOptions options) {
        return commandExecutor.get(aggregateAsync(indexName, query, options));
    }

    @Override
    public RFuture<AggregationResult> aggregateAsync(String indexName, String query, AggregationOptions options) {
        List<Object> args = new ArrayList<>();
        args.add(indexName);
        args.add(query);

        if (options.isVerbatim()) {
            args.add("VERBATIM");
        }
        if (!options.getLoad().isEmpty()) {
            args.add("LOAD");
            args.add(options.getLoad().size());
            args.addAll(options.getLoad());
        }
        if (options.getTimeout() != null) {
            args.add("TIMEOUT");
            args.add(options.getTimeout());
        }
        if (options.isLoadAll()) {
            args.add("LOAD");
            args.add("*");
        }
        for (GroupParams group : options.getGroupByParams()) {
            args.add("GROUPBY");
            args.add(group.getFieldNames().size());
            args.addAll(group.getFieldNames());
            for (Reducer reducer : group.getReducers()) {
                args.add("REDUCE");
                ReducerParams params = (ReducerParams) reducer;
                args.add(params.getFunctionName());
                args.add(params.getArgs().size());
                args.addAll(params.getArgs());
                if (params.getAs() != null) {
                    args.add("AS");
                    args.add(params.getAs());
                }
            }
        }
        if (!options.getSortedByFields().isEmpty()) {
            args.add("SORTBY");
            args.add(options.getSortedByFields().size()*2);
            for (SortedField sortedByField : options.getSortedByFields()) {
                args.add(sortedByField.getName());
                args.add(sortedByField.getOrder());
            }
            if (options.getSortedByMax() != null) {
                args.add("MAX");
                args.add(options.getSortedByMax());
            }
            if (options.isSortedByWithCount()) {
                args.add("WITHCOUNT");
            }
        }
        for (Expression expression : options.getExpressions()) {
            args.add("APPLY");
            args.add(expression.getValue());
            args.add("AS");
            args.add(expression.getAs());
        }
        if (options.getOffset() != null
                && options.getCount() != null) {
            args.add("LIMIT");
            args.add(options.getOffset());
            args.add(options.getCount());
        }
        if (options.getFilter() != null) {
            args.add("FILTER");
            args.add(options.getFilter());
        }
        if (options.isWithCursor()) {
            args.add("WITHCURSOR");
            if (options.getCursorCount() != null) {
                args.add("COUNT");
                args.add(options.getCursorCount());
            }
            if (options.getCursorMaxIdle() != null) {
                args.add("MAXIDLE");
                args.add(options.getCursorMaxIdle());
            }
        }
        if (!options.getParams().isEmpty()) {
            args.add("PARAMS");
            args.add(options.getParams().size()*2);
            for (Map.Entry<String, Object> entry : options.getParams().entrySet()) {
                args.add(entry.getKey());
                args.add(entry.getValue());
            }
        }
        if (options.getDialect() != null) {
            args.add("DIALECT");
            args.add(options.getDialect());
        }

        int reducers = options.getGroupByParams().stream()
                                                 .mapToInt(g -> g.getReducers().size())
                                                 .sum();
        RedisStrictCommand<AggregationResult> command;
        if (commandExecutor.getServiceManager().isResp3()) {
            if (options.isWithCursor()) {
                command = new RedisStrictCommand<>("FT.AGGREGATE",
                        new ListMultiDecoder2(new AggregationCursorResultDecoderV2(),
                                new ObjectListReplayDecoder(),
                                new ObjectListReplayDecoder(),
                                new ObjectMapReplayDecoder(),
                                new AggregationEntryDecoder(new CompositeCodec(StringCodec.INSTANCE, codec), reducers)));
            } else {
                command = new RedisStrictCommand<>("FT.AGGREGATE",
                        new ListMultiDecoder2(new AggregationResultDecoderV2(),
                                new ObjectListReplayDecoder(),
                                new ObjectMapReplayDecoder(),
                                new AggregationEntryDecoder(new CompositeCodec(StringCodec.INSTANCE, codec), reducers)));
            }
        } else {
            if (options.isWithCursor()) {
                command = new RedisStrictCommand<>("FT.AGGREGATE",
                        new ListMultiDecoder2(new AggregationCursorResultDecoder(),
                                new ObjectListReplayDecoder(),
                                new AggregationEntryDecoder(new CompositeCodec(StringCodec.INSTANCE, codec), reducers)));
            } else {
                command = new RedisStrictCommand<>("FT.AGGREGATE",
                        new ListMultiDecoder2(new AggregationResultDecoder(),
                                new AggregationEntryDecoder(new CompositeCodec(StringCodec.INSTANCE, codec), reducers),
                                new ObjectListReplayDecoder()));
            }
        }

        return commandExecutor.writeAsync(indexName, StringCodec.INSTANCE, command, args.toArray());
    }

    @Override
    public void addAlias(String alias, String indexName) {
        commandExecutor.get(addAliasAsync(alias, indexName));
    }

    @Override
    public RFuture<Void> addAliasAsync(String alias, String indexName) {
        return commandExecutor.writeAsync(alias, StringCodec.INSTANCE, RedisCommands.FT_ALIASADD, alias, indexName);
    }

    @Override
    public void delAlias(String alias) {
        commandExecutor.get(delAliasAsync(alias));
    }

    @Override
    public RFuture<Void> delAliasAsync(String alias) {
        return commandExecutor.writeAsync(alias, StringCodec.INSTANCE, RedisCommands.FT_ALIASDEL, alias);
    }

    @Override
    public void updateAlias(String alias, String indexName) {
        commandExecutor.get(updateAliasAsync(alias, indexName));
    }

    @Override
    public RFuture<Void> updateAliasAsync(String alias, String indexName) {
        return commandExecutor.writeAsync(alias, StringCodec.INSTANCE, RedisCommands.FT_ALIASUPDATE, alias, indexName);
    }

    @Override
    public void alter(String indexName, boolean skipInitialScan, FieldIndex... fields) {
        commandExecutor.get(alterAsync(indexName, skipInitialScan, fields));
    }

    @Override
    public RFuture<Void> alterAsync(String indexName, boolean skipInitialScan, FieldIndex... fields) {
        List<Object> args = new ArrayList<>();
        args.add(indexName);
        if (skipInitialScan) {
            args.add("SKIPINITIALSCAN");
        }

        args.add("SCHEMA");
        for (FieldIndex field : fields) {
            args.add("ADD");
            addTextIndex(args, field);
            addTagIndex(args, field);
            addGeoIndex(args, field);
            addNumericIndex(args, field);
            addFlatVectorIndex(args, field);
            addHNSWVectorIndex(args, field);
        }

        return commandExecutor.writeAsync(indexName, StringCodec.INSTANCE, RedisCommands.FT_ALTER, args.toArray());
    }

    @Override
    public Map<String, String> getConfig(String parameter) {
        return commandExecutor.get(getConfigAsync(parameter));
    }

    @Override
    public RFuture<Map<String, String>> getConfigAsync(String parameter) {
        return commandExecutor.readAsync((String) null, StringCodec.INSTANCE, RedisCommands.FT_CONFIG_GET, parameter);
    }

    @Override
    public void setConfig(String parameter, String value) {
        commandExecutor.get(setConfigAsync(parameter, value));
    }

    @Override
    public RFuture<Void> setConfigAsync(String parameter, String value) {
        return commandExecutor.writeAsync((String) null, StringCodec.INSTANCE, RedisCommands.FT_CONFIG_SET, parameter, value);
    }

    @Override
    public void delCursor(String indexName, long cursorId) {
        commandExecutor.get(delCursorAsync(indexName, cursorId));
    }

    @Override
    public RFuture<Void> delCursorAsync(String indexName, long cursorId) {
        return commandExecutor.writeAsync((String) null, StringCodec.INSTANCE, RedisCommands.FT_CURSOR_DEL, indexName, cursorId);
    }

    @Override
    public AggregationResult readCursor(String indexName, long cursorId) {
        return commandExecutor.get(readCursorAsync(indexName, cursorId));
    }

    @Override
    public RFuture<AggregationResult> readCursorAsync(String indexName, long cursorId) {
        RedisStrictCommand command;
        if (commandExecutor.getServiceManager().isResp3()) {
            command = new RedisStrictCommand<>("FT.CURSOR", "READ",
                    new ListMultiDecoder2(new AggregationCursorResultDecoderV2(),
                            new ObjectListReplayDecoder(),
                            new ObjectListReplayDecoder(),
                            new ObjectMapReplayDecoder(),
                            new ObjectMapReplayDecoder(new CompositeCodec(StringCodec.INSTANCE, codec))));
        } else {
            command = new RedisStrictCommand<>("FT.CURSOR", "READ",
                    new ListMultiDecoder2(new AggregationCursorResultDecoder(),
                            new ObjectListReplayDecoder(),
                            new ObjectMapReplayDecoder(new CompositeCodec(StringCodec.INSTANCE, codec))));
        }

        return commandExecutor.writeAsync(indexName, StringCodec.INSTANCE, command, indexName, cursorId);
    }

    @Override
    public AggregationResult readCursor(String indexName, long cursorId, int count) {
        return commandExecutor.get(readCursorAsync(indexName, cursorId, count));
    }

    @Override
    public RFuture<AggregationResult> readCursorAsync(String indexName, long cursorId, int count) {
        RedisStrictCommand command = new RedisStrictCommand<>("FT.CURSOR", "READ",
                new ListMultiDecoder2(new AggregationCursorResultDecoder(),
                        new ObjectListReplayDecoder(),
                        new ObjectMapReplayDecoder(new CompositeCodec(StringCodec.INSTANCE, codec))));

        return commandExecutor.writeAsync(indexName, StringCodec.INSTANCE, command, indexName, cursorId, "COUNT", count);
    }

    @Override
    public long addDict(String dictionary, String... terms) {
        return commandExecutor.get(addDictAsync(dictionary, terms));
    }

    @Override
    public RFuture<Long> addDictAsync(String dictionary, String... terms) {
        List<Object> args = new ArrayList<>();
        args.add(dictionary);
        args.addAll(Arrays.asList(terms));
        return commandExecutor.writeAsync(dictionary, LongCodec.INSTANCE, RedisCommands.FT_DICTADD, args.toArray());
    }

    @Override
    public long delDict(String dictionary, String... terms) {
        return commandExecutor.get(delDictAsync(dictionary, terms));
    }

    @Override
    public RFuture<Long> delDictAsync(String dictionary, String... terms) {
        List<Object> args = new ArrayList<>();
        args.add(dictionary);
        args.addAll(Arrays.asList(terms));
        return commandExecutor.writeAsync(dictionary, LongCodec.INSTANCE, RedisCommands.FT_DICTDEL, args.toArray());
    }

    @Override
    public List<String> dumpDict(String dictionary) {
        return commandExecutor.get(dumpDictAsync(dictionary));
    }

    @Override
    public RFuture<List<String>> dumpDictAsync(String dictionary) {
        return commandExecutor.readAsync(dictionary, StringCodec.INSTANCE, RedisCommands.FT_DICTDUMP, dictionary);
    }

    @Override
    public void dropIndex(String indexName) {
        commandExecutor.get(dropIndexAsync(indexName));
    }

    @Override
    public RFuture<Void> dropIndexAsync(String indexName) {
        return commandExecutor.writeAsync((String) null, StringCodec.INSTANCE, RedisCommands.FT_DROPINDEX, indexName);
    }

    @Override
    public void dropIndexAndDocuments(String indexName) {
        commandExecutor.get(dropIndexAndDocumentsAsync(indexName));
    }

    @Override
    public RFuture<Void> dropIndexAndDocumentsAsync(String indexName) {
        return commandExecutor.writeAsync((String) null, StringCodec.INSTANCE, RedisCommands.FT_DROPINDEX, indexName, "DD");
    }

    @Override
    public IndexInfo info(String indexName) {
        return commandExecutor.get(infoAsync(indexName));
    }

    @Override
    public RFuture<IndexInfo> infoAsync(String indexName) {
        return commandExecutor.readAsync((String) null, StringCodec.INSTANCE, RedisCommands.FT_INFO, indexName);
    }

    @Override
    public Map<String, Map<String, Double>> spellcheck(String indexName, String query, SpellcheckOptions options) {
        return commandExecutor.get(spellcheckAsync(indexName, query, options));
    }

    @Override
    public RFuture<Map<String, Map<String, Double>>> spellcheckAsync(String indexName, String query, SpellcheckOptions options) {
        List<Object> args = new ArrayList<>();
        args.add(indexName);
        args.add(query);
        if (options.getDistance() != null) {
            args.add("DISTANCE");
            args.add(options.getDistance());
        }
        if (options.getExcludedDictionary() != null) {
            args.add("TERMS");
            args.add("EXCLUDE");
            args.add(options.getExcludedDictionary());
            args.addAll(options.getExcludedTerms());
        }
        if (options.getIncludedDictionary() != null) {
            args.add("TERMS");
            args.add("INCLUDE");
            args.add(options.getIncludedDictionary());
            args.addAll(options.getIncludedTerms());
        }

        if (options.getDialect() != null) {
            args.add("DIALECT");
            args.add(options.getDialect());
        }

        RedisCommand<Map<String, Map<String, Object>>> command = RedisCommands.FT_SPELLCHECK;
        if (commandExecutor.getServiceManager().isResp3()) {
            command = new RedisCommand<>("FT.SPELLCHECK",
                    new ListMultiDecoder2(
                            new ListObjectDecoder(1),
                            new ObjectMapReplayDecoder(),
                            new ListFirstObjectDecoder(new EmptyMapConvertor()),
                            new ObjectMapReplayDecoder(new CompositeCodec(StringCodec.INSTANCE, DoubleCodec.INSTANCE))));
        }

        return commandExecutor.readAsync(indexName, StringCodec.INSTANCE, command, args.toArray());
    }

    @Override
    public Map<String, List<String>> dumpSynonyms(String indexName) {
        return commandExecutor.get(dumpSynonymsAsync(indexName));
    }

    @Override
    public RFuture<Map<String, List<String>>> dumpSynonymsAsync(String indexName) {
        return commandExecutor.readAsync(indexName, StringCodec.INSTANCE, RedisCommands.FT_SYNDUMP, indexName);
    }

    @Override
    public void updateSynonyms(String indexName, String synonymGroupId, String... terms) {
        commandExecutor.get(updateSynonymsAsync(indexName, synonymGroupId, terms));
    }

    @Override
    public RFuture<Void> updateSynonymsAsync(String indexName, String synonymGroupId, String... terms) {
        List<Object> args = new ArrayList<>();
        args.add(indexName);
        args.add(synonymGroupId);
        args.addAll(Arrays.asList(terms));
        return commandExecutor.writeAsync(indexName, StringCodec.INSTANCE, RedisCommands.FT_SYNUPDATE, args.toArray());
    }

    @Override
    public List<String> getIndexes() {
        return commandExecutor.get(getIndexesAsync());
    }

    @Override
    public RFuture<List<String>> getIndexesAsync() {
        return commandExecutor.readAsync((String) null, StringCodec.INSTANCE, RedisCommands.FT_LIST);
    }
}
