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

import org.redisson.api.AsyncIterator;
import org.redisson.api.RFuture;
import org.redisson.api.RTimeSeriesNative;
import org.redisson.api.tsnative.TSAddArgs;
import org.redisson.api.tsnative.TSAddParams;
import org.redisson.api.tsnative.TSAggregation;
import org.redisson.api.tsnative.TSAlterArgs;
import org.redisson.api.tsnative.TSAlterParams;
import org.redisson.api.tsnative.TSBaseRangeParams;
import org.redisson.api.tsnative.TSCreateArgs;
import org.redisson.api.tsnative.TSCreateParams;
import org.redisson.api.tsnative.TSCreationParams;
import org.redisson.api.tsnative.TSIncrArgs;
import org.redisson.api.tsnative.TSIncrParams;
import org.redisson.api.tsnative.TSInfo;
import org.redisson.api.tsnative.TSRangeArgs;
import org.redisson.api.tsnative.TSRangeParams;
import org.redisson.api.tsnative.TSReadArgs;
import org.redisson.api.tsnative.TSReadParams;
import org.redisson.api.tsnative.TSRuleArgs;
import org.redisson.api.tsnative.TSRuleParams;
import org.redisson.api.tsnative.TSSample;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.iterator.BaseAsyncIterator;
import org.redisson.iterator.BaseIterator;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.concurrent.CompletionStage;

/**
 * Time series based on TS.* commands of the RedisTimeSeries module.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTimeSeriesNative extends RedissonExpirable implements RTimeSeriesNative {

    public RedissonTimeSeriesNative(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    @Override
    public void create() {
        get(createAsync());
    }

    @Override
    public RFuture<Void> createAsync() {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_CREATE, getRawName());
    }

    @Override
    public void create(TSCreateArgs args) {
        get(createAsync(args));
    }

    @Override
    public RFuture<Void> createAsync(TSCreateArgs args) {
        List<Object> params = new ArrayList<>();
        params.add(getRawName());
        appendCreationOptions(params, (TSCreateParams) args);
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_CREATE, params.toArray());
    }

    @Override
    public boolean createIfAbsent() {
        return get(createIfAbsentAsync());
    }

    @Override
    public RFuture<Boolean> createIfAbsentAsync() {
        return createIfAbsentAsync(TSCreateArgs.defaults());
    }

    @Override
    public boolean createIfAbsent(TSCreateArgs args) {
        return get(createIfAbsentAsync(args));
    }

    @Override
    public RFuture<Boolean> createIfAbsentAsync(TSCreateArgs args) {
        List<Object> params = new ArrayList<>();
        appendCreationOptions(params, (TSCreateParams) args);

        return commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.EVAL_BOOLEAN,
                  "if redis.call('exists', KEYS[1]) == 0 then "
                    + "redis.call('TS.CREATE', KEYS[1], unpack(ARGV)); "
                    + "return 1; "
                + "end; "
                + "return 0;",
                Collections.singletonList(getRawName()), params.toArray());
    }

    @Override
    public void alter(TSAlterArgs args) {
        get(alterAsync(args));
    }

    @Override
    public RFuture<Void> alterAsync(TSAlterArgs args) {
        List<Object> params = new ArrayList<>();
        params.add(getRawName());
        appendCreationOptions(params, (TSAlterParams) args);
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_ALTER, params.toArray());
    }

    @Override
    public long add(long timestamp, double value) {
        return get(addAsync(timestamp, value));
    }

    @Override
    public RFuture<Long> addAsync(long timestamp, double value) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_ADD, getRawName(), timestamp, value);
    }

    @Override
    public long addCurrent(double value) {
        return get(addCurrentAsync(value));
    }

    @Override
    public RFuture<Long> addCurrentAsync(double value) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_ADD, getRawName(), "*", value);
    }

    @Override
    public long add(TSAddArgs args) {
        return get(addAsync(args));
    }

    @Override
    public RFuture<Long> addAsync(TSAddArgs args) {
        TSAddParams params = (TSAddParams) args;

        List<Object> cmdArgs = new ArrayList<>();
        cmdArgs.add(getRawName());
        if (params.getTimestamp() == null) {
            cmdArgs.add("*");
        } else {
            cmdArgs.add(params.getTimestamp());
        }
        cmdArgs.add(params.getValue());
        if (params.getOnDuplicate() != null) {
            cmdArgs.add("ON_DUPLICATE");
            cmdArgs.add(params.getOnDuplicate().name());
        }
        appendCreationOptions(cmdArgs, params);

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_ADD, cmdArgs.toArray());
    }

    @Override
    public List<Long> addAll(Map<Long, Double> samples) {
        return get(addAllAsync(samples));
    }

    @Override
    public RFuture<List<Long>> addAllAsync(Map<Long, Double> samples) {
        if (samples.isEmpty()) {
            return new CompletableFutureWrapper<>(Collections.<Long>emptyList());
        }

        List<Object> cmdArgs = new ArrayList<>(samples.size() * 3);
        for (Map.Entry<Long, Double> sample : samples.entrySet()) {
            cmdArgs.add(getRawName());
            cmdArgs.add(sample.getKey());
            cmdArgs.add(sample.getValue());
        }

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_MADD, cmdArgs.toArray());
    }

    @Override
    public long incrementBy(double addend) {
        return get(incrementByAsync(addend));
    }

    @Override
    public RFuture<Long> incrementByAsync(double addend) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_INCRBY, getRawName(), addend);
    }

    @Override
    public long incrementBy(TSIncrArgs args) {
        return get(incrementByAsync(args));
    }

    @Override
    public RFuture<Long> incrementByAsync(TSIncrArgs args) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_INCRBY, incrementArgs(args).toArray());
    }

    @Override
    public long decrementBy(double subtrahend) {
        return get(decrementByAsync(subtrahend));
    }

    @Override
    public RFuture<Long> decrementByAsync(double subtrahend) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_DECRBY, getRawName(), subtrahend);
    }

    @Override
    public long decrementBy(TSIncrArgs args) {
        return get(decrementByAsync(args));
    }

    @Override
    public RFuture<Long> decrementByAsync(TSIncrArgs args) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_DECRBY, incrementArgs(args).toArray());
    }

    private List<Object> incrementArgs(TSIncrArgs args) {
        TSIncrParams params = (TSIncrParams) args;

        List<Object> cmdArgs = new ArrayList<>();
        cmdArgs.add(getRawName());
        cmdArgs.add(params.getValue());
        if (params.getTimestamp() != null) {
            cmdArgs.add("TIMESTAMP");
            cmdArgs.add(params.getTimestamp());
        }
        appendCreationOptions(cmdArgs, params);
        return cmdArgs;
    }

    @Override
    public long removeRange(long fromTimestamp, long toTimestamp) {
        return get(removeRangeAsync(fromTimestamp, toTimestamp));
    }

    @Override
    public RFuture<Long> removeRangeAsync(long fromTimestamp, long toTimestamp) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_DEL, getRawName(), fromTimestamp, toTimestamp);
    }

    @Override
    public TSSample first() {
        return get(firstAsync());
    }

    @Override
    public RFuture<TSSample> firstAsync() {
        TSRangeParams params = (TSRangeParams) TSRangeArgs.all().count(1);

        List<Object> cmdArgs = new ArrayList<>();
        cmdArgs.add(getRawName());
        appendRangeOptions(cmdArgs, params, Collections.emptyList());

        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_RANGE_FIRST, cmdArgs.toArray());
    }

    @Override
    public long size() {
        return get(sizeAsync());
    }

    @Override
    public RFuture<Long> sizeAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_INFO_TOTAL_SAMPLES, getRawName());
    }

    @Override
    public long firstTimestamp() {
        return get(firstTimestampAsync());
    }

    @Override
    public RFuture<Long> firstTimestampAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_INFO_FIRST_TIMESTAMP, getRawName());
    }

    @Override
    public long lastTimestamp() {
        return get(lastTimestampAsync());
    }

    @Override
    public RFuture<Long> lastTimestampAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_INFO_LAST_TIMESTAMP, getRawName());
    }

    @Override
    public TSSample get() {
        return get(getAsync());
    }

    @Override
    public RFuture<TSSample> getAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_GET, getRawName());
    }

    @Override
    public TSSample getLatest() {
        return get(getLatestAsync());
    }

    @Override
    public RFuture<TSSample> getLatestAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_GET, getRawName(), "LATEST");
    }

    @Override
    public List<TSSample> range(long fromTimestamp, long toTimestamp) {
        return get(rangeAsync(fromTimestamp, toTimestamp));
    }

    @Override
    public RFuture<List<TSSample>> rangeAsync(long fromTimestamp, long toTimestamp) {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_RANGE, getRawName(), fromTimestamp, toTimestamp);
    }

    @Override
    public List<TSSample> range(TSRangeArgs args) {
        return get(rangeAsync(args));
    }

    @Override
    public RFuture<List<TSSample>> rangeAsync(TSRangeArgs args) {
        return rangeAsync(RedisCommands.TS_RANGE, args);
    }

    @Override
    public List<TSSample> rangeReversed(long fromTimestamp, long toTimestamp) {
        return get(rangeReversedAsync(fromTimestamp, toTimestamp));
    }

    @Override
    public RFuture<List<TSSample>> rangeReversedAsync(long fromTimestamp, long toTimestamp) {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_REVRANGE, getRawName(), fromTimestamp, toTimestamp);
    }

    @Override
    public List<TSSample> rangeReversed(TSRangeArgs args) {
        return get(rangeReversedAsync(args));
    }

    @Override
    public RFuture<List<TSSample>> rangeReversedAsync(TSRangeArgs args) {
        return rangeAsync(RedisCommands.TS_REVRANGE, args);
    }

    private RFuture<List<TSSample>> rangeAsync(RedisCommand<List<TSSample>> command, TSRangeArgs args) {
        TSRangeParams params = (TSRangeParams) args;

        List<Object> cmdArgs = new ArrayList<>();
        cmdArgs.add(getRawName());
        appendRangeOptions(cmdArgs, params, Collections.emptyList());

        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, command, cmdArgs.toArray());
    }

    @Override
    public List<TSSample> read(long timestamp) {
        return get(readAsync(timestamp));
    }

    @Override
    public RFuture<List<TSSample>> readAsync(long timestamp) {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_READ, getRawName(), timestamp);
    }

    @Override
    public List<TSSample> read(TSReadArgs args) {
        return get(readAsync(args));
    }

    @Override
    public RFuture<List<TSSample>> readAsync(TSReadArgs args) {
        TSReadParams params = (TSReadParams) args;

        List<Object> cmdArgs = new ArrayList<>();
        cmdArgs.add(getRawName());
        cmdArgs.add(params.getTimestamp());

        RedisCommand<List<TSSample>> command = RedisCommands.TS_READ;
        if (params.getBlockTimeout() != null) {
            command = RedisCommands.TS_READ_BLOCKING;
            cmdArgs.add("BLOCK");
            cmdArgs.add(params.getBlockTimeout().toMillis());
            cmdArgs.add(params.getMinCount());
        }
        if (params.getMaxCount() != null) {
            cmdArgs.add("MAX_COUNT");
            cmdArgs.add(params.getMaxCount());
        }

        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, command, cmdArgs.toArray());
    }

    @Override
    public void createRule(TSRuleArgs args) {
        get(createRuleAsync(args));
    }

    @Override
    public RFuture<Void> createRuleAsync(TSRuleArgs args) {
        TSRuleParams params = (TSRuleParams) args;

        List<Object> cmdArgs = new ArrayList<>();
        cmdArgs.add(getRawName());
        cmdArgs.add(commandExecutor.getServiceManager().getNameMapper().map(params.getDestinationKey()));
        cmdArgs.add("AGGREGATION");
        cmdArgs.add(params.getAggregation().getValue());
        cmdArgs.add(params.getBucketDuration().toMillis());
        if (params.getAlignTimestamp() != null) {
            cmdArgs.add(params.getAlignTimestamp());
        }

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_CREATERULE, cmdArgs.toArray());
    }

    @Override
    public void deleteRule(String destinationKey) {
        get(deleteRuleAsync(destinationKey));
    }

    @Override
    public RFuture<Void> deleteRuleAsync(String destinationKey) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.TS_DELETERULE,
                getRawName(), commandExecutor.getServiceManager().getNameMapper().map(destinationKey));
    }

    @Override
    public Iterator<TSSample> iterator() {
        return iterator(10);
    }

    @Override
    public Iterator<TSSample> iterator(int count) {
        return new BaseIterator<TSSample, TSSample>() {
            @Override
            protected ScanResult<TSSample> iterator(RedisClient client, String nextIterPos) {
                return window(get(rangeAsync(TSRangeArgs.from(Long.parseLong(nextIterPos)).count(count))), count);
            }

            @Override
            protected TSSample getValue(TSSample entry) {
                return entry;
            }

            @Override
            protected void remove(TSSample value) {
                throw new UnsupportedOperationException(
                        "a sample is removed by timestamp, with removeRange(long, long)");
            }
        };
    }

    @Override
    public AsyncIterator<TSSample> iteratorAsync() {
        return iteratorAsync(10);
    }

    @Override
    public AsyncIterator<TSSample> iteratorAsync(int count) {
        return new BaseAsyncIterator<TSSample, TSSample>() {
            @Override
            protected RFuture<ScanResult<TSSample>> iterator(RedisClient client, String nextItPos) {
                CompletionStage<ScanResult<TSSample>> result =
                        rangeAsync(TSRangeArgs.from(Long.parseLong(nextItPos)).count(count))
                                .thenApply(samples -> window(samples, count));
                return new CompletableFutureWrapper<>(result);
            }
        };
    }

    private static ScanResult<TSSample> window(List<TSSample> samples, int count) {
        String nextPos = "0";
        if (samples.size() == count) {
            nextPos = Long.toString(samples.get(samples.size() - 1).getTimestamp() + 1);
        }
        return new ListScanResult<>(nextPos, samples);
    }

    @Override
    public Stream<TSSample> stream() {
        return toStream(iterator());
    }

    @Override
    public Stream<TSSample> stream(int count) {
        return toStream(iterator(count));
    }

    @Override
    public Map<String, String> getLabels() {
        return get(getLabelsAsync());
    }

    @Override
    public RFuture<Map<String, String>> getLabelsAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_INFO_LABELS, getRawName());
    }

    @Override
    public TSInfo getInfo() {
        return get(getInfoAsync());
    }

    @Override
    public RFuture<TSInfo> getInfoAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_INFO, getRawName());
    }

    @Override
    public TSInfo getDebugInfo() {
        return get(getDebugInfoAsync());
    }

    @Override
    public RFuture<TSInfo> getDebugInfoAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TS_INFO, getRawName(), "DEBUG");
    }

    static void appendCreationOptions(List<Object> cmdArgs, TSCreationParams params) {
        if (params.getRetention() != null) {
            long retention = params.getRetention().toMillis();
            if (retention == 0 && !params.getRetention().isZero()) {
                throw new IllegalArgumentException("retention below 1ms is not representable: "
                        + params.getRetention() + " would be sent as RETENTION 0, which never expires");
            }
            cmdArgs.add("RETENTION");
            cmdArgs.add(retention);
        }
        if (params.getEncoding() != null) {
            cmdArgs.add("ENCODING");
            cmdArgs.add(params.getEncoding().name());
        }
        if (params.getChunkSize() != null) {
            cmdArgs.add("CHUNK_SIZE");
            cmdArgs.add(params.getChunkSize());
        }
        if (params.getDuplicatePolicy() != null) {
            cmdArgs.add("DUPLICATE_POLICY");
            cmdArgs.add(params.getDuplicatePolicy().name());
        }
        if (params.getIgnoreMaxTimeDiff() != null) {
            cmdArgs.add("IGNORE");
            cmdArgs.add(params.getIgnoreMaxTimeDiff().toMillis());
            cmdArgs.add(params.getIgnoreMaxValueDiff());
        }
        if (params.getLabels() != null) {
            cmdArgs.add("LABELS");
            for (Map.Entry<String, String> label : params.getLabels().entrySet()) {
                cmdArgs.add(label.getKey());
                cmdArgs.add(label.getValue());
            }
        }
    }

    @SuppressWarnings("MethodLength")
    static void appendRangeOptions(List<Object> cmdArgs, TSBaseRangeParams params, List<Object> selection) {
        cmdArgs.add(params.getFromTimestamp());
        cmdArgs.add(params.getToTimestamp());

        if (params.isLatest()) {
            cmdArgs.add("LATEST");
        }
        if (params.getFilterByTimestamps() != null) {
            cmdArgs.add("FILTER_BY_TS");
            for (long timestamp : params.getFilterByTimestamps()) {
                cmdArgs.add(timestamp);
            }
        }
        if (params.getFilterByMinValue() != null) {
            cmdArgs.add("FILTER_BY_VALUE");
            cmdArgs.add(params.getFilterByMinValue());
            cmdArgs.add(params.getFilterByMaxValue());
        }

        cmdArgs.addAll(selection);

        if (params.getCount() != null) {
            cmdArgs.add("COUNT");
            cmdArgs.add(params.getCount());
        }
        if (params.getAlign() != null) {
            if (params.getAggregations() == null) {
                throw new IllegalArgumentException("align() requires aggregation(): the module rejects "
                        + "ALIGN without AGGREGATION, and dropping it would silently run an unaligned query");
            }

            if (TSBaseRangeParams.EARLIEST.equals(params.getAlign())
                    && TSBaseRangeParams.EARLIEST.equals(params.getFromTimestamp())) {
                throw new IllegalArgumentException(
                        "alignStart() requires a range with an explicit start");
            }
            if (TSBaseRangeParams.LATEST_TIMESTAMP.equals(params.getAlign())
                    && TSBaseRangeParams.LATEST_TIMESTAMP.equals(params.getToTimestamp())) {
                throw new IllegalArgumentException(
                        "alignEnd() requires a range with an explicit end");
            }
        }
        if (params.getAggregations() != null) {
            if (params.getAlign() != null) {
                cmdArgs.add("ALIGN");
                cmdArgs.add(params.getAlign());
            }
            cmdArgs.add("AGGREGATION");

            for (List<TSAggregation> perKey : params.getAggregations()) {
                StringBuilder joined = new StringBuilder();
                for (TSAggregation aggregation : perKey) {
                    if (joined.length() > 0) {
                        joined.append(',');
                    }
                    joined.append(aggregation.getValue());
                }
                cmdArgs.add(joined.toString());
            }
            cmdArgs.add(params.getBucketDuration().toMillis());
            if (params.getBucketTimestamp() != null) {
                cmdArgs.add("BUCKETTIMESTAMP");
                cmdArgs.add(params.getBucketTimestamp().getValue());
            }
            if (params.isEmpty()) {
                cmdArgs.add("EMPTY");
            }
        }
    }

}
