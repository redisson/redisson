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
import org.redisson.api.RTDigest;
import org.redisson.api.TDigestInfo;
import org.redisson.api.tdigest.TDigestMergeArgs;
import org.redisson.api.tdigest.TDigestMergeArgsImpl;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.codec.TDigestDoubleCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Distributed implementation of t-digest
 * based on Redis Bloom module {@code TDIGEST.*} commands.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTDigest extends RedissonExpirable implements RTDigest {

    public RedissonTDigest(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    @Override
    public void create() {
        get(createAsync());
    }

    @Override
    public RFuture<Void> createAsync() {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TDIGEST_CREATE, getRawName());
    }

    @Override
    public void create(int compression) {
        get(createAsync(compression));
    }

    @Override
    public RFuture<Void> createAsync(int compression) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TDIGEST_CREATE, getRawName(), "COMPRESSION", compression);
    }

    @Override
    public void add(double value) {
        get(addAsync(value));
    }

    @Override
    public RFuture<Void> addAsync(double value) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TDIGEST_ADD, getRawName(), toStr(value));
    }

    @Override
    public void add(double... values) {
        get(addAsync(values));
    }

    @Override
    public RFuture<Void> addAsync(double... values) {
        List<Object> params = new ArrayList<>(values.length + 1);
        params.add(getRawName());
        for (double value : values) {
            params.add(toStr(value));
        }
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TDIGEST_ADD, params.toArray());
    }

    @Override
    public void mergeWith(String... keys) {
        get(mergeWithAsync(keys));
    }

    @Override
    public RFuture<Void> mergeWithAsync(String... keys) {
        List<Object> params = new ArrayList<>(keys.length + 2);
        params.add(getRawName());
        params.add(keys.length);
        for (String key : keys) {
            params.add(key);
        }
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TDIGEST_MERGE, params.toArray());
    }

    @Override
    public void mergeWith(TDigestMergeArgs args) {
        get(mergeWithAsync(args));
    }

    @Override
    public RFuture<Void> mergeWithAsync(TDigestMergeArgs args) {
        TDigestMergeArgsImpl a = (TDigestMergeArgsImpl) args;
        List<String> keys = new ArrayList<>(a.getKeys());

        List<Object> params = new ArrayList<>(keys.size() + 5);
        params.add(getRawName());
        params.add(keys.size());
        params.addAll(keys);

        if (a.getCompression() != null) {
            params.add("COMPRESSION");
            params.add(a.getCompression());
        }
        if (a.isOverride()) {
            params.add("OVERRIDE");
        }

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TDIGEST_MERGE, params.toArray());
    }

    @Override
    public double getMin() {
        return get(getMinAsync());
    }

    @Override
    public RFuture<Double> getMinAsync() {
        return commandExecutor.readAsync(getRawName(), TDigestDoubleCodec.INSTANCE,
                RedisCommands.TDIGEST_MIN, getRawName());
    }

    @Override
    public double getMax() {
        return get(getMaxAsync());
    }

    @Override
    public RFuture<Double> getMaxAsync() {
        return commandExecutor.readAsync(getRawName(), TDigestDoubleCodec.INSTANCE,
                RedisCommands.TDIGEST_MAX, getRawName());
    }

    @Override
    public List<Double> quantile(double... quantiles) {
        return get(quantileAsync(quantiles));
    }

    @Override
    public RFuture<List<Double>> quantileAsync(double... quantiles) {
        return commandExecutor.readAsync(getRawName(), TDigestDoubleCodec.INSTANCE,
                RedisCommands.TDIGEST_QUANTILE, doubleParams(quantiles));
    }

    @Override
    public List<Double> cumulativeProbability(double... values) {
        return get(cumulativeProbabilityAsync(values));
    }

    @Override
    public RFuture<List<Double>> cumulativeProbabilityAsync(double... values) {
        return commandExecutor.readAsync(getRawName(), TDigestDoubleCodec.INSTANCE,
                RedisCommands.TDIGEST_CDF, doubleParams(values));
    }

    @Override
    public double trimmedMean(double lowCutQuantile, double highCutQuantile) {
        return get(trimmedMeanAsync(lowCutQuantile, highCutQuantile));
    }

    @Override
    public RFuture<Double> trimmedMeanAsync(double lowCutQuantile, double highCutQuantile) {
        return commandExecutor.readAsync(getRawName(), TDigestDoubleCodec.INSTANCE,
                RedisCommands.TDIGEST_TRIMMED_MEAN,
                getRawName(), toStr(lowCutQuantile), toStr(highCutQuantile));
    }

    @Override
    public List<Long> rank(double... values) {
        return get(rankAsync(values));
    }

    @Override
    public RFuture<List<Long>> rankAsync(double... values) {
        return commandExecutor.readAsync(getRawName(), LongCodec.INSTANCE,
                RedisCommands.TDIGEST_RANK, doubleParams(values));
    }

    @Override
    public List<Long> revRank(double... values) {
        return get(revRankAsync(values));
    }

    @Override
    public RFuture<List<Long>> revRankAsync(double... values) {
        return commandExecutor.readAsync(getRawName(), LongCodec.INSTANCE,
                RedisCommands.TDIGEST_REVRANK, doubleParams(values));
    }

    @Override
    public List<Double> byRank(long... ranks) {
        return get(byRankAsync(ranks));
    }

    @Override
    public RFuture<List<Double>> byRankAsync(long... ranks) {
        return commandExecutor.readAsync(getRawName(), TDigestDoubleCodec.INSTANCE,
                RedisCommands.TDIGEST_BYRANK, longParams(ranks));
    }

    @Override
    public List<Double> byRevRank(long... ranks) {
        return get(byRevRankAsync(ranks));
    }

    @Override
    public RFuture<List<Double>> byRevRankAsync(long... ranks) {
        return commandExecutor.readAsync(getRawName(), TDigestDoubleCodec.INSTANCE,
                RedisCommands.TDIGEST_BYREVRANK, longParams(ranks));
    }

    @Override
    public TDigestInfo getInfo() {
        return get(getInfoAsync());
    }

    @Override
    public RFuture<TDigestInfo> getInfoAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TDIGEST_INFO, getRawName());
    }

    @Override
    public void reset() {
        get(resetAsync());
    }

    @Override
    public RFuture<Void> resetAsync() {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.TDIGEST_RESET, getRawName());
    }

    private Object[] doubleParams(double... values) {
        List<Object> params = new ArrayList<>(values.length + 1);
        params.add(getRawName());
        for (double value : values) {
            params.add(toStr(value));
        }
        return params.toArray();
    }

    private Object[] longParams(long... values) {
        List<Object> params = new ArrayList<>(values.length + 1);
        params.add(getRawName());
        for (long value : values) {
            params.add(value);
        }
        return params.toArray();
    }

    private static String toStr(double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (value == Double.POSITIVE_INFINITY) {
            return "inf";
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "-inf";
        }
        return BigDecimal.valueOf(value).toPlainString();
    }

}
