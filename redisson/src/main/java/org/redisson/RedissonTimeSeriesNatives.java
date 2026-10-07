/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
import org.redisson.api.RTimeSeriesNatives;
import org.redisson.api.tsnative.TSGroupedRangeArgs;
import org.redisson.api.tsnative.TSGroupedRangeParams;
import org.redisson.api.tsnative.TSMultiGetArgs;
import org.redisson.api.tsnative.TSMultiGetParams;
import org.redisson.api.tsnative.TSMultiRangeArgs;
import org.redisson.api.tsnative.TSMultiRangeParams;
import org.redisson.api.tsnative.TSSample;
import org.redisson.api.tsnative.TSSeriesSample;
import org.redisson.api.tsnative.TSSeriesSamples;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.TimeSeriesMultiAddDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Operations of the RedisTimeSeries module that span several series.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTimeSeriesNatives implements RTimeSeriesNatives {

    private final CommandAsyncExecutor commandExecutor;

    public RedissonTimeSeriesNatives(CommandAsyncExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @Override
    public Map<String, List<Long>> addAll(Map<String, Map<Long, Double>> samples) {
        return commandExecutor.get(addAllAsync(samples));
    }

    @Override
    public RFuture<Map<String, List<Long>>> addAllAsync(Map<String, Map<Long, Double>> samples) {
        if (samples.isEmpty()) {
            return new CompletableFutureWrapper<>(Collections.<String, List<Long>>emptyMap());
        }

        Map<Object, Map<Long, Double>> mapped = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Long, Double>> entry : samples.entrySet()) {
            mapped.put(mapName(entry.getKey()), entry.getValue());
        }
        List<Object> keys = new ArrayList<>(mapped.keySet());

        return commandExecutor.writeBatchedAsync(StringCodec.INSTANCE, madd(keys, mapped),
                new SlotCallback<Map<String, List<Long>>, Map<String, List<Long>>>() {

            @Override
            public RedisCommand<Map<String, List<Long>>> createCommand(List<Object> slotKeys) {
                return madd(slotKeys, mapped);
            }

            @Override
            public Object[] createParams(List<Object> slotKeys) {
                List<Object> params = new ArrayList<>();
                for (Object key : slotKeys) {
                    for (Map.Entry<Long, Double> sample : mapped.get(key).entrySet()) {
                        params.add(key);
                        params.add(sample.getKey());
                        params.add(sample.getValue());
                    }
                }
                return params.toArray();
            }

            @Override
            public Map<String, List<Long>> onResult(Collection<Map<String, List<Long>>> result) {
                Map<String, List<Long>> merged = new HashMap<>();
                for (Map<String, List<Long>> perSlot : result) {
                    merged.putAll(perSlot);
                }

                Map<String, List<Long>> ordered = new LinkedHashMap<>(samples.size());
                for (String key : samples.keySet()) {
                    ordered.put(key, merged.get(mapName(key)));
                }
                return ordered;
            }
        }, keys.toArray());
    }

    private static RedisCommand<Map<String, List<Long>>> madd(List<Object> keys,
                                                              Map<Object, Map<Long, Double>> samples) {
        List<Integer> counts = new ArrayList<>(keys.size());
        for (Object key : keys) {
            counts.add(samples.get(key).size());
        }
        return new RedisCommand<>(RedisCommands.TS_MADD.getName(),
                new TimeSeriesMultiAddDecoder(keys, counts));
    }

    @Override
    public Map<String, TSSeriesSample> getAll(TSMultiGetArgs args) {
        return commandExecutor.get(getAllAsync(args));
    }

    @Override
    public RFuture<Map<String, TSSeriesSample>> getAllAsync(TSMultiGetArgs args) {
        TSMultiGetParams params = (TSMultiGetParams) args;
        requireFilters(params.getFilters());

        List<Object> cmdArgs = new ArrayList<>();
        if (params.isLatest()) {
            cmdArgs.add("LATEST");
        }
        cmdArgs.addAll(labelSelection(params.isWithLabels(), params.getSelectedLabels()));
        cmdArgs.add("FILTER");
        cmdArgs.addAll(Arrays.asList(params.getFilters()));

        return byFilter(RedisCommands.TS_MGET, cmdArgs);
    }

    @Override
    public Map<String, TSSeriesSamples> range(TSMultiRangeArgs args) {
        return commandExecutor.get(rangeAsync(args));
    }

    @Override
    public RFuture<Map<String, TSSeriesSamples>> rangeAsync(TSMultiRangeArgs args) {
        return multiRangeAsync(RedisCommands.TS_MRANGE, args);
    }

    @Override
    public Map<String, TSSeriesSamples> rangeReversed(TSMultiRangeArgs args) {
        return commandExecutor.get(rangeReversedAsync(args));
    }

    @Override
    public RFuture<Map<String, TSSeriesSamples>> rangeReversedAsync(TSMultiRangeArgs args) {
        return multiRangeAsync(RedisCommands.TS_MREVRANGE, args);
    }

    private RFuture<Map<String, TSSeriesSamples>> multiRangeAsync(
            RedisCommand<Map<String, TSSeriesSamples>> command,
                                                           TSMultiRangeArgs args) {
        TSMultiRangeParams params = (TSMultiRangeParams) args;
        requireFilters(params.getFilters());
        if (params.getGroupByLabel() != null) {

            if (params.isExcludeEmpty()) {
                throw new IllegalArgumentException(
                        "groupBy() and excludeEmpty() are mutually exclusive");
            }
            if (params.getAggregations() != null && params.getAggregations().get(0).size() > 1) {
                throw new IllegalArgumentException(
                        "groupBy() is mutually exclusive with an aggregation() of more than one aggregator");
            }
        }

        List<Object> cmdArgs = new ArrayList<>();
        RedissonTimeSeriesNative.appendRangeOptions(cmdArgs, params,
                labelSelection(params.isWithLabels(), params.getSelectedLabels()));

        if (params.isExcludeEmpty()) {
            cmdArgs.add("EXCLUDEEMPTY");
        }
        cmdArgs.add("FILTER");
        cmdArgs.addAll(Arrays.asList(params.getFilters()));
        if (params.getGroupByLabel() != null) {
            cmdArgs.add("GROUPBY");
            cmdArgs.add(params.getGroupByLabel());
            cmdArgs.add("REDUCE");
            cmdArgs.add(params.getReducer().getValue());
        }

        return byFilter(command, cmdArgs);
    }

    @Override
    public List<TSSample> groupedRange(TSGroupedRangeArgs args) {
        return commandExecutor.get(groupedRangeAsync(args));
    }

    @Override
    public RFuture<List<TSSample>> groupedRangeAsync(TSGroupedRangeArgs args) {
        return groupedRangeAsync(RedisCommands.TS_NRANGE, args);
    }

    @Override
    public List<TSSample> groupedRangeReversed(TSGroupedRangeArgs args) {
        return commandExecutor.get(groupedRangeReversedAsync(args));
    }

    @Override
    public RFuture<List<TSSample>> groupedRangeReversedAsync(TSGroupedRangeArgs args) {
        return groupedRangeAsync(RedisCommands.TS_NREVRANGE, args);
    }

    private RFuture<List<TSSample>> groupedRangeAsync(RedisCommand<List<TSSample>> command,
                                                             TSGroupedRangeArgs args) {
        TSGroupedRangeParams params = (TSGroupedRangeParams) args;
        if (params.getKeys().length == 0) {
            throw new IllegalArgumentException("At least one key is required");
        }
        if (params.getAggregations() != null
                && params.getAggregations().size() != params.getKeys().length) {
            throw new IllegalArgumentException("Aggregations must hold one entry per key: "
                    + params.getKeys().length + " expected, " + params.getAggregations().size() + " given");
        }

        List<Object> cmdArgs = new ArrayList<>();
        cmdArgs.add(params.getKeys().length);
        for (String key : params.getKeys()) {
            cmdArgs.add(mapName(key));
        }
        RedissonTimeSeriesNative.appendRangeOptions(cmdArgs, params, Collections.emptyList());

        return commandExecutor.readAsync(mapName(params.getKeys()[0]), StringCodec.INSTANCE,
                command, cmdArgs.toArray());
    }

    @Override
    public Set<String> queryIndex(String... filters) {
        return commandExecutor.get(queryIndexAsync(filters));
    }

    @Override
    public RFuture<Set<String>> queryIndexAsync(String... filters) {
        requireFilters(filters);
        return byFilter(RedisCommands.TS_QUERYINDEX, new ArrayList<Object>(Arrays.asList(filters)));
    }

    @Override
    public Set<String> labelNames(String... filters) {
        return commandExecutor.get(labelNamesAsync(filters));
    }

    @Override
    public RFuture<Set<String>> labelNamesAsync(String... filters) {
        List<Object> cmdArgs = new ArrayList<>();
        cmdArgs.add("LABELS");
        if (filters != null && filters.length > 0) {
            cmdArgs.add("FILTER");
            cmdArgs.addAll(Arrays.asList(filters));
        }
        return byFilter(RedisCommands.TS_QUERYLABELS, cmdArgs);
    }

    @Override
    public Set<String> labelValues(String label, String... filters) {
        return commandExecutor.get(labelValuesAsync(label, filters));
    }

    @Override
    public RFuture<Set<String>> labelValuesAsync(String label, String... filters) {
        List<Object> cmdArgs = new ArrayList<>();
        cmdArgs.add("VALUES");
        cmdArgs.add(label);
        if (filters != null && filters.length > 0) {
            cmdArgs.add("FILTER");
            cmdArgs.addAll(Arrays.asList(filters));
        }
        return byFilter(RedisCommands.TS_QUERYLABELS, cmdArgs);
    }

    private <T> RFuture<T> byFilter(RedisCommand<T> command, List<Object> cmdArgs) {
        return commandExecutor.readAsync((String) null, StringCodec.INSTANCE, command, cmdArgs.toArray());
    }

    private static List<Object> labelSelection(boolean withLabels, String[] selectedLabels) {
        if (selectedLabels != null && withLabels) {
            throw new IllegalArgumentException(
                    "withLabels() and selectedLabels() are mutually exclusive");
        }
        if (selectedLabels != null) {
            if (selectedLabels.length == 0) {
                throw new IllegalArgumentException("selectedLabels() requires at least one label");
            }
            List<Object> selection = new ArrayList<>(selectedLabels.length + 1);
            selection.add("SELECTED_LABELS");
            selection.addAll(Arrays.asList(selectedLabels));
            return selection;
        }
        if (withLabels) {
            return Collections.singletonList("WITHLABELS");
        }
        return Collections.emptyList();
    }

    private static void requireFilters(String[] filters) {
        if (filters == null || filters.length == 0) {
            throw new IllegalArgumentException("At least one filter expression is required");
        }
    }

    private String mapName(String name) {
        return commandExecutor.getServiceManager().getNameMapper().map(name);
    }

}
