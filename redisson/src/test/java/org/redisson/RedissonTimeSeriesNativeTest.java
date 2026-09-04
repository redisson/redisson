package org.redisson;

import io.netty.util.concurrent.DefaultPromise;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.AsyncIterator;
import org.redisson.api.BatchResult;
import org.redisson.api.RBatch;
import org.redisson.api.RFuture;
import org.redisson.api.RTimeSeriesNative;
import org.redisson.api.RTimeSeriesNativeAsync;
import org.redisson.api.RType;
import org.redisson.api.RedissonClient;
import org.redisson.api.tsnative.TSAddArgs;
import org.redisson.api.tsnative.TSAggregation;
import org.redisson.api.tsnative.TSAlterArgs;
import org.redisson.api.tsnative.TSBucketTimestamp;
import org.redisson.api.tsnative.TSCreateArgs;
import org.redisson.api.tsnative.TSDuplicatePolicy;
import org.redisson.api.tsnative.TSEncoding;
import org.redisson.api.tsnative.TSIncrArgs;
import org.redisson.api.tsnative.TSInfo;
import org.redisson.api.tsnative.TSRangeArgs;
import org.redisson.api.tsnative.TSReadArgs;
import org.redisson.api.tsnative.TSRuleArgs;
import org.redisson.api.tsnative.TSSample;
import org.redisson.client.RedisException;
import org.redisson.config.Config;
import org.redisson.config.Protocol;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTimeSeriesNativeTest extends RedisDockerTest {

    private RTimeSeriesNative ts;

    @BeforeEach
    public void setUp() {
        ts = redisson.getTimeSeriesNative("temperature");
    }

    private void withResp3(Consumer<RedissonClient> body) {
        Config config = redisson.getConfig();
        config.setProtocol(Protocol.RESP3);
        RedissonClient resp3 = Redisson.create(config);
        try {
            body.accept(resp3);
        } finally {
            resp3.shutdown();
        }
    }

    @Test
    public void testCreate() {
        ts.create();

        TSInfo info = ts.getInfo();
        assertThat(info.getTotalSamples()).isZero();
        assertThat(info.getChunkType()).isEqualTo(TSEncoding.COMPRESSED);
        assertThat(info.getRetentionTime()).isEqualTo(Duration.ZERO);
        assertThat(info.getLabels()).isEmpty();
        assertThat(info.getRules()).isEmpty();
        assertThat(info.getSourceKey()).isNull();
        assertThat(info.getChunks()).isEmpty();
    }

    @Test
    public void testCreateWithArgs() {
        Map<String, String> labels = new HashMap<>();
        labels.put("area", "32");
        labels.put("sensor", "a");

        ts.create(TSCreateArgs.defaults()
                .retention(Duration.ofHours(1))
                .encoding(TSEncoding.UNCOMPRESSED)
                .chunkSize(128)
                .duplicatePolicy(TSDuplicatePolicy.LAST)
                .ignore(Duration.ofMillis(5), 0.5)
                .labels(labels));

        TSInfo info = ts.getInfo();
        assertThat(info.getRetentionTime()).isEqualTo(Duration.ofHours(1));
        assertThat(info.getChunkType()).isEqualTo(TSEncoding.UNCOMPRESSED);
        assertThat(info.getChunkSize()).isEqualTo(128);
        assertThat(info.getDuplicatePolicy()).isEqualTo(TSDuplicatePolicy.LAST);
        assertThat(info.getIgnoreMaxTimeDiff()).isEqualTo(Duration.ofMillis(5));
        assertThat(info.getIgnoreMaxValueDiff()).isEqualTo(0.5);
        assertThat(info.getLabels()).containsExactlyInAnyOrderEntriesOf(labels);
    }

    @Test
    public void testCreateLabelByLabel() {
        ts.create(TSCreateArgs.defaults().label("area", "32").label("sensor", "a"));

        assertThat(ts.getInfo().getLabels()).containsOnly(entry("area", "32"), entry("sensor", "a"));
    }

    @Test
    public void testCreateTwiceFails() {
        ts.create();

        assertThatThrownBy(() -> ts.create()).isInstanceOf(RedisException.class);
    }

    @Test
    public void testCreateIfAbsent() {
        assertThat(ts.createIfAbsent(TSCreateArgs.defaults().label("area", "32"))).isTrue();
        assertThat(ts.createIfAbsent(TSCreateArgs.defaults().label("area", "99"))).isFalse();

        assertThat(ts.getInfo().getLabels()).containsEntry("area", "32");
    }

    @Test
    public void testCreateIfAbsentOnAKeyOfAnotherType() {
        redisson.getBucket("temperature").set("not a series");

        assertThat(ts.createIfAbsent(TSCreateArgs.defaults())).isFalse();
        assertThat(redisson.getKeys().getType("temperature")).isEqualTo(RType.OBJECT);
    }

    @Test
    public void testAlter() {
        ts.create(TSCreateArgs.defaults()
                .retention(Duration.ofHours(1))
                .label("area", "32")
                .label("sensor", "a"));

        ts.alter(TSAlterArgs.defaults()
                .retention(Duration.ofHours(2))
                .duplicatePolicy(TSDuplicatePolicy.MAX)
                .label("area", "33"));

        TSInfo info = ts.getInfo();
        assertThat(info.getRetentionTime()).isEqualTo(Duration.ofHours(2));
        assertThat(info.getDuplicatePolicy()).isEqualTo(TSDuplicatePolicy.MAX);

        assertThat(info.getLabels()).containsOnlyKeys("area");
        assertThat(info.getLabels()).containsEntry("area", "33");
    }

    @Test
    public void testAlterKeepsLabelsWhenNotGiven() {
        ts.create(TSCreateArgs.defaults().label("area", "32"));

        ts.alter(TSAlterArgs.defaults().retention(Duration.ofMinutes(5)));

        assertThat(ts.getInfo().getLabels()).containsEntry("area", "32");
    }

    @Test
    public void testAdd() {
        assertThat(ts.add(1000, 10)).isEqualTo(1000);
        assertThat(ts.add(2000, 20)).isEqualTo(2000);

        assertThat(ts.getInfo().getTotalSamples()).isEqualTo(2);
        assertThat(ts.getInfo().getFirstTimestamp()).isEqualTo(1000);
        assertThat(ts.getInfo().getLastTimestamp()).isEqualTo(2000);
    }

    @Test
    public void testAddCurrent() {
        long before = System.currentTimeMillis();
        long assigned = ts.addCurrent(10);

        assertThat(assigned).isGreaterThanOrEqualTo(before);
        assertThat(ts.get().getValue()).isEqualTo(10);
    }

    @Test
    public void testAddWithCreationArgs() {
        ts.add(TSAddArgs.sample(1000, 10)
                .retention(Duration.ofHours(1))
                .duplicatePolicy(TSDuplicatePolicy.LAST)
                .label("area", "32"));

        TSInfo info = ts.getInfo();
        assertThat(info.getRetentionTime()).isEqualTo(Duration.ofHours(1));
        assertThat(info.getLabels()).containsEntry("area", "32");
    }

    @Test
    public void testAddOnDuplicate() {
        ts.create(TSCreateArgs.defaults().duplicatePolicy(TSDuplicatePolicy.BLOCK));
        ts.add(1000, 10);

        assertThatThrownBy(() -> ts.add(1000, 20)).isInstanceOf(RedisException.class);

        ts.add(TSAddArgs.sample(1000, 20).onDuplicate(TSDuplicatePolicy.SUM));
        assertThat(ts.get().getValue()).isEqualTo(30);
    }

    @Test
    public void testAddCurrentSampleArgs() {
        long before = System.currentTimeMillis();

        long assigned = ts.add(TSAddArgs.currentSample(7).label("area", "32"));

        assertThat(assigned).isGreaterThanOrEqualTo(before);
        assertThat(ts.getInfo().getLabels()).containsEntry("area", "32");
    }

    @Test
    public void testAddIgnoresNearDuplicate() {
        ts.create(TSCreateArgs.defaults()
                .duplicatePolicy(TSDuplicatePolicy.LAST)
                .ignore(Duration.ofMillis(100), 5));
        ts.add(1000, 10);

        assertThat(ts.add(1050, 12)).isEqualTo(1000);
        assertThat(ts.getInfo().getTotalSamples()).isEqualTo(1);

        assertThat(ts.add(1060, 100)).isEqualTo(1060);
        assertThat(ts.getInfo().getTotalSamples()).isEqualTo(2);
    }

    @Test
    public void testIncrementBy() {
        ts.add(1000, 10);

        long timestamp = ts.incrementBy(5);

        assertThat(timestamp).isGreaterThan(1000);
        assertThat(ts.get().getValue()).isEqualTo(15);
    }

    @Test
    public void testIncrementByArgs() {
        ts.add(1000, 10);

        assertThat(ts.incrementBy(TSIncrArgs.value(5).timestamp(2000))).isEqualTo(2000);

        assertThat(ts.get()).isEqualTo(new TSSample(2000, new double[]{15}));
    }

    @Test
    public void testIncrementByCreatesSeries() {
        assertThat(ts.isExists()).isFalse();

        ts.incrementBy(TSIncrArgs.value(3).timestamp(1000).label("area", "32"));

        assertThat(ts.get().getValue()).isEqualTo(3);
        assertThat(ts.getInfo().getLabels()).containsEntry("area", "32");
    }

    @Test
    public void testDecrementBy() {
        ts.add(1000, 10);

        assertThat(ts.decrementBy(TSIncrArgs.value(4).timestamp(2000))).isEqualTo(2000);
        assertThat(ts.get().getValue()).isEqualTo(6);
    }

    @Test
    public void testDecrementByCurrentTimestamp() {
        ts.add(1000, 10);

        long timestamp = ts.decrementBy(4);

        assertThat(timestamp).isGreaterThan(1000);
        assertThat(ts.get().getValue()).isEqualTo(6);
    }

    @Test
    public void testRemoveRange() {
        ts.add(1000, 10);
        ts.add(2000, 20);
        ts.add(3000, 30);

        assertThat(ts.removeRange(1500, 2500)).isEqualTo(1);
        assertThat(ts.range(0, 5000)).extracting(TSSample::getTimestamp).containsExactly(1000L, 3000L);
    }

    @Test
    public void testGetOnEmptySeries() {
        ts.create();

        assertThat(ts.get()).isNull();
        assertThat(ts.getLatest()).isNull();
    }

    @Test
    public void testGet() {
        ts.add(1000, 10);
        ts.add(2000, 20);

        assertThat(ts.get()).isEqualTo(new TSSample(2000, new double[]{20}));
    }

    @Test
    public void testGetLatestOnCompaction() {
        RTimeSeriesNative compacted = redisson.getTimeSeriesNative("temperature:avg");
        compacted.create();
        ts.create();
        ts.createRule(TSRuleArgs.destination("temperature:avg", TSAggregation.AVG, Duration.ofSeconds(10)));

        ts.add(1000, 10);
        ts.add(2000, 20);

        // The bucket is still open, so a plain read skips it and LATEST compacts and reports it.
        assertThat(compacted.get()).isNull();
        assertThat(compacted.getLatest()).isEqualTo(new TSSample(0, new double[]{15}));
    }

    @Test
    public void testRange() {
        ts.add(1000, 10);
        ts.add(2000, 20);
        ts.add(3000, 30);

        assertThat(ts.range(1000, 3000)).containsExactly(
                new TSSample(1000, new double[]{10}),
                new TSSample(2000, new double[]{20}),
                new TSSample(3000, new double[]{30}));

        assertThat(ts.range(1500, 2500)).containsExactly(new TSSample(2000, new double[]{20}));
    }

    @Test
    public void testRangeReversed() {
        ts.add(1000, 10);
        ts.add(2000, 20);

        assertThat(ts.rangeReversed(0, 5000))
                .extracting(TSSample::getTimestamp).containsExactly(2000L, 1000L);
    }

    @Test
    public void testRangeArgsSentinels() {
        ts.add(1000, 10);
        ts.add(2000, 20);
        ts.add(3000, 30);

        assertThat(ts.range(TSRangeArgs.all())).hasSize(3);
        assertThat(ts.range(TSRangeArgs.from(2000)))
                .extracting(TSSample::getTimestamp).containsExactly(2000L, 3000L);
        assertThat(ts.range(TSRangeArgs.to(2000)))
                .extracting(TSSample::getTimestamp).containsExactly(1000L, 2000L);
    }

    @Test
    public void testRangeCount() {
        ts.add(1000, 10);
        ts.add(2000, 20);
        ts.add(3000, 30);

        assertThat(ts.range(TSRangeArgs.all().count(2)))
                .extracting(TSSample::getTimestamp).containsExactly(1000L, 2000L);
        // COUNT keeps the highest timestamps when the range is read backwards.
        assertThat(ts.rangeReversed(TSRangeArgs.all().count(2)))
                .extracting(TSSample::getTimestamp).containsExactly(3000L, 2000L);
    }

    @Test
    public void testRangeFilterByValue() {
        ts.add(1000, 10);
        ts.add(2000, 20);
        ts.add(3000, 30);

        assertThat(ts.range(TSRangeArgs.all().filterByValue(15, 25)))
                .containsExactly(new TSSample(2000, new double[]{20}));
    }

    @Test
    public void testRangeFilterByTimestamp() {
        ts.add(1000, 10);
        ts.add(2000, 20);
        ts.add(3000, 30);

        assertThat(ts.range(TSRangeArgs.all().filterByTimestamp(1000, 3000)))
                .extracting(TSSample::getTimestamp).containsExactly(1000L, 3000L);
    }

    @Test
    public void testRangeSingleAggregation() {
        ts.add(1000, 10);
        ts.add(1500, 20);
        ts.add(3000, 30);

        List<TSSample> samples = ts.range(TSRangeArgs.all()
                .aggregation(Duration.ofSeconds(2), TSAggregation.AVG));

        assertThat(samples).containsExactly(
                new TSSample(0, new double[]{15}),
                new TSSample(2000, new double[]{30}));
    }

    @Test
    public void testRangeMultipleAggregations() {
        ts.add(1000, 10);
        ts.add(1500, 20);

        List<TSSample> samples = ts.range(TSRangeArgs.all()
                .aggregation(Duration.ofSeconds(2), TSAggregation.MIN, TSAggregation.AVG, TSAggregation.MAX));

        assertThat(samples).hasSize(1);

        assertThat(samples.get(0).getValues()).containsExactly(10, 15, 20);
        assertThat(samples.get(0).getValue()).isEqualTo(10);
    }

    @Test
    public void testRangeAllAggregators() {
        ts.add(1000, 10);
        ts.add(1500, 20);
        ts.add(1800, 30);

        for (TSAggregation aggregation : TSAggregation.values()) {
            List<TSSample> samples = ts.range(TSRangeArgs.all()
                    .aggregation(Duration.ofSeconds(2), aggregation));

            if (aggregation == TSAggregation.COUNT_NAN) {
                assertThat(samples).isEmpty();
                assertThat(ts.range(TSRangeArgs.range(1000, 1800)
                        .aggregation(Duration.ofSeconds(2), aggregation)
                        .empty())).containsExactly(new TSSample(0, new double[]{0}));
                continue;
            }
            assertThat(samples).as("aggregator %s", aggregation).hasSize(1);
        }
    }

    @Test
    public void testRangeEmptyBuckets() {
        ts.add(1000, 10);
        ts.add(9000, 90);

        List<TSSample> samples = ts.range(TSRangeArgs.range(1000, 9000)
                .aggregation(Duration.ofSeconds(2), TSAggregation.AVG)
                .empty());

        assertThat(samples).hasSize(5);
        assertThat(samples.get(1).getValue()).isNaN();
        assertThat(samples.get(4).getValue()).isEqualTo(90);
    }

    @Test
    public void testRangeAlignAndBucketTimestamp() {
        ts.add(1000, 10);
        ts.add(9000, 90);

        assertThat(ts.range(TSRangeArgs.range(1000, 9000)
                .alignStart()
                .aggregation(Duration.ofSeconds(2), TSAggregation.AVG)))
                .extracting(TSSample::getTimestamp).containsExactly(1000L, 9000L);

        assertThat(ts.range(TSRangeArgs.range(1000, 9000)
                .align(500)
                .aggregation(Duration.ofSeconds(2), TSAggregation.AVG)))
                .extracting(TSSample::getTimestamp).containsExactly(500L, 8500L);

        assertThat(ts.range(TSRangeArgs.range(1000, 9000)
                .alignEnd()
                .aggregation(Duration.ofSeconds(2), TSAggregation.AVG)
                .bucketTimestamp(TSBucketTimestamp.END)))
                .extracting(TSSample::getTimestamp).containsExactly(3000L, 11000L);
    }

    @Test
    public void testRangeLatestOnCompaction() {
        RTimeSeriesNative compacted = redisson.getTimeSeriesNative("temperature:avg");
        compacted.create();
        ts.create();
        ts.createRule(TSRuleArgs.destination("temperature:avg", TSAggregation.AVG, Duration.ofSeconds(10)));
        ts.add(1000, 10);
        ts.add(2000, 20);

        assertThat(compacted.range(TSRangeArgs.all())).isEmpty();
        assertThat(compacted.range(TSRangeArgs.all().latest())).hasSize(1);
    }

    @Test
    public void testRead() {
        ts.add(1000, 10);
        ts.add(2000, 20);
        ts.add(3000, 30);

        assertThat(ts.read(2000))
                .extracting(TSSample::getTimestamp).containsExactly(2000L, 3000L);
    }

    @Test
    public void testReadSentinelsAndMaxCount() {
        ts.add(1000, 10);
        ts.add(2000, 20);
        ts.add(3000, 30);

        assertThat(ts.read(TSReadArgs.fromEarliest())).hasSize(3);
        assertThat(ts.read(TSReadArgs.fromEarliest().maxCount(2))).hasSize(2);
        assertThat(ts.read(TSReadArgs.fromLast()))
                .containsExactly(new TSSample(3000, new double[]{30}));

        assertThat(ts.read(TSReadArgs.fromNext())).isEmpty();
    }

    @Test
    public void testReadBlockingTimesOut() {
        ts.add(1000, 10);

        long started = System.currentTimeMillis();
        List<TSSample> samples = ts.read(TSReadArgs.fromNext().block(Duration.ofMillis(300), 1));

        assertThat(samples).isEmpty();
        assertThat(System.currentTimeMillis() - started).isGreaterThanOrEqualTo(250);
    }

    @Test
    public void testReadBlockingSeesLateSample() {
        ts.add(1000, 10);

        Executors.newSingleThreadScheduledExecutor()
                .schedule(() -> ts.add(2000, 20), 300, TimeUnit.MILLISECONDS);

        List<TSSample> samples = ts.read(TSReadArgs.fromNext().block(Duration.ofSeconds(10), 1));

        assertThat(samples).containsExactly(new TSSample(2000, new double[]{20}));
    }

    @Test
    public void testCreateRule() {
        RTimeSeriesNative compacted = redisson.getTimeSeriesNative("temperature:avg");
        compacted.create();
        ts.create();

        ts.createRule(TSRuleArgs.destination("temperature:avg", TSAggregation.AVG, Duration.ofSeconds(2)));

        assertThat(ts.getInfo().getRules()).hasSize(1);
        assertThat(ts.getInfo().getRules().get(0).getDestinationKey()).isEqualTo("temperature:avg");
        assertThat(ts.getInfo().getRules().get(0).getAggregation()).isEqualTo(TSAggregation.AVG);
        assertThat(ts.getInfo().getRules().get(0).getBucketDuration()).isEqualTo(Duration.ofSeconds(2));
        assertThat(compacted.getInfo().getSourceKey()).isEqualTo("temperature");

        ts.add(1000, 10);
        ts.add(1500, 20);
        ts.add(5000, 50);

        assertThat(compacted.range(0, 10000)).containsExactly(new TSSample(0, new double[]{15}));
    }

    @Test
    public void testCreateRuleWithAlignment() {
        redisson.getTimeSeriesNative("temperature:avg").create();
        ts.create();

        ts.createRule(TSRuleArgs.destination("temperature:avg", TSAggregation.SUM, Duration.ofSeconds(2))
                .alignTimestamp(500));

        assertThat(ts.getInfo().getRules().get(0).getAlignTimestamp()).isEqualTo(500);
    }

    @Test
    public void testDeleteRule() {
        redisson.getTimeSeriesNative("temperature:avg").create();
        ts.create();
        ts.createRule(TSRuleArgs.destination("temperature:avg", TSAggregation.AVG, Duration.ofSeconds(2)));

        ts.deleteRule("temperature:avg");

        assertThat(ts.getInfo().getRules()).isEmpty();
    }

    @Test
    public void testDebugInfo() {
        ts.add(1000, 10);
        ts.add(2000, 20);

        assertThat(ts.getInfo().getKeySelfName()).isNull();
        assertThat(ts.getInfo().getChunks()).isEmpty();

        TSInfo info = ts.getDebugInfo();
        assertThat(info.getKeySelfName()).isEqualTo("temperature");
        assertThat(info.getChunks()).hasSize(1);
        assertThat(info.getChunks().get(0).getStartTimestamp()).isEqualTo(1000);
        assertThat(info.getChunks().get(0).getEndTimestamp()).isEqualTo(2000);
        assertThat(info.getChunks().get(0).getSamples()).isEqualTo(2);
        assertThat(info.getChunks().get(0).getSize()).isPositive();
        assertThat(info.getChunks().get(0).getBytesPerSample()).isGreaterThan(0);
    }

    @Test
    public void testExpirable() {
        ts.add(1000, 10);

        assertThat(ts.expire(Duration.ofSeconds(30))).isTrue();
        assertThat(ts.remainTimeToLive()).isGreaterThan(0);
        assertThat(ts.delete()).isTrue();
        assertThat(ts.isExists()).isFalse();
    }

    @Test
    public void testRetentionDropsOldSamples() {
        ts.create(TSCreateArgs.defaults().retention(Duration.ofMillis(1000)));
        ts.add(1000, 10);
        ts.add(3000, 30);

        assertThat(ts.range(0, 5000)).containsExactly(new TSSample(3000, new double[]{30}));
    }

    @Test
    public void testResp3ReadsMatchResp2() {
        ts.create(TSCreateArgs.defaults()
                .retention(Duration.ofHours(1))
                .duplicatePolicy(TSDuplicatePolicy.LAST)
                .label("area", "32"));
        ts.add(1000, 10);
        ts.add(2000, 20);
        redisson.getTimeSeriesNative("temperature:avg").create();
        ts.createRule(TSRuleArgs.destination("temperature:avg", TSAggregation.AVG, Duration.ofSeconds(2)));

        List<TSSample> expectedRange = ts.range(0, 5000);
        TSSample expectedLast = ts.get();
        TSInfo expectedInfo = ts.getInfo();

        withResp3(resp3 -> {
            RTimeSeriesNative other = resp3.getTimeSeriesNative("temperature");

            assertThat(other.range(0, 5000)).isEqualTo(expectedRange);
            assertThat(other.get()).isEqualTo(expectedLast);

            TSInfo info = other.getInfo();
            assertThat(info.getTotalSamples()).isEqualTo(expectedInfo.getTotalSamples());
            assertThat(info.getRetentionTime()).isEqualTo(expectedInfo.getRetentionTime());
            assertThat(info.getDuplicatePolicy()).isEqualTo(expectedInfo.getDuplicatePolicy());
            assertThat(info.getChunkType()).isEqualTo(expectedInfo.getChunkType());
            assertThat(info.getLabels()).isEqualTo(expectedInfo.getLabels());
            assertThat(info.getRules()).isEqualTo(expectedInfo.getRules());
            assertThat(info.getIgnoreMaxTimeDiff()).isEqualTo(expectedInfo.getIgnoreMaxTimeDiff());

            TSInfo debug = other.getDebugInfo();
            assertThat(debug.getKeySelfName()).isEqualTo("temperature");
            assertThat(debug.getChunks()).hasSize(1);
            assertThat(debug.getChunks().get(0).getSamples()).isEqualTo(2);
            assertThat(debug.getChunks().get(0).getBytesPerSample()).isGreaterThan(0);
        });
    }

    @Test
    public void testResp3AggregationAndEmptyBuckets() {
        ts.add(1000, 10);
        ts.add(9000, 90);

        List<TSSample> expected = ts.range(TSRangeArgs.range(1000, 9000)
                .aggregation(Duration.ofSeconds(2), TSAggregation.MIN, TSAggregation.MAX)
                .empty());

        withResp3(resp3 -> {
            List<TSSample> actual = resp3.getTimeSeriesNative("temperature")
                    .range(TSRangeArgs.range(1000, 9000)
                            .aggregation(Duration.ofSeconds(2), TSAggregation.MIN, TSAggregation.MAX)
                            .empty());

            assertThat(actual).isEqualTo(expected);
            assertThat(actual.get(1).getValues()).containsExactly(Double.NaN, Double.NaN);
        });
    }

    @Test
    public void testAsync() throws Exception {
        assertThat(ts.createAsync(TSCreateArgs.defaults().label("area", "32")).get()).isNull();
        assertThat(ts.addAsync(1000, 10).get()).isEqualTo(1000);
        assertThat(ts.rangeAsync(0, 5000).get()).containsExactly(new TSSample(1000, new double[]{10}));
        assertThat(ts.getInfoAsync().get().getLabels()).containsEntry("area", "32");
    }

    @Test
    public void testReadBlockingOutlastsTheResponseTimeout() {
        ts.add(1000, 10);

        long started = System.currentTimeMillis();
        List<TSSample> samples = ts.read(TSReadArgs.fromNext().block(Duration.ofMillis(4500), 1));
        long elapsed = System.currentTimeMillis() - started;

        assertThat(samples).isEmpty();
        assertThat(elapsed).isGreaterThanOrEqualTo(4000).isLessThan(12000);
    }

    @Test
    public void testBlockRejectsATimeoutThatWouldWaitForever() {
        assertThatThrownBy(() -> TSReadArgs.fromNext().block(Duration.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1ms");
        assertThatThrownBy(() -> TSReadArgs.fromNext().block(Duration.ofNanos(500), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testSubMillisecondRetentionIsRejected() {
        assertThatThrownBy(() -> ts.create(TSCreateArgs.defaults().retention(Duration.ofNanos(999999))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never expires");

        ts.create(TSCreateArgs.defaults().retention(Duration.ZERO));
        assertThat(ts.getInfo().getRetentionTime()).isEqualTo(Duration.ZERO);
    }

    @Test
    public void testAlignWithoutAggregationIsRejected() {
        ts.add(1000, 10);

        assertThatThrownBy(() -> ts.range(TSRangeArgs.all().alignStart()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("align() requires aggregation()");
        assertThatThrownBy(() -> ts.range(TSRangeArgs.all().align(500)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testAlignNeedsTheBoundItAlignsOn() {
        ts.add(1000, 10);
        ts.add(9000, 90);

        assertThatThrownBy(() -> ts.range(TSRangeArgs.all()
                .alignStart()
                .aggregation(Duration.ofSeconds(2), TSAggregation.AVG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicit start");
        assertThatThrownBy(() -> ts.range(TSRangeArgs.from(1000)
                .alignEnd()
                .aggregation(Duration.ofSeconds(2), TSAggregation.AVG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicit end");

        // An explicit start is enough for alignStart, and the open end does not matter to it.
        assertThat(ts.range(TSRangeArgs.from(1000)
                .alignStart()
                .aggregation(Duration.ofSeconds(2), TSAggregation.AVG))).isNotEmpty();
    }

    @Test
    public void testResp3DecodesInfinity() {
        ts.add(1000, 1.7e308);
        ts.incrementBy(TSIncrArgs.value(1.7e308).timestamp(2000));

        assertThat(ts.get().getValue()).isInfinite();
        TSSample expected = ts.get();
        List<TSSample> expectedRange = ts.range(0, 5000);

        withResp3(resp3 -> {
            RTimeSeriesNative other = resp3.getTimeSeriesNative("temperature");
            assertThat(other.get()).isEqualTo(expected);
            assertThat(other.get().getValue()).isEqualTo(Double.POSITIVE_INFINITY);
            assertThat(other.range(0, 5000)).isEqualTo(expectedRange);
        });
    }

    @Test
    public void testResp3DecodesNegativeInfinity() {
        ts.add(1000, -1.7e308);
        ts.decrementBy(TSIncrArgs.value(1.7e308).timestamp(2000));

        assertThat(ts.get().getValue()).isEqualTo(Double.NEGATIVE_INFINITY);

        withResp3(resp3 -> assertThat(resp3.getTimeSeriesNative("temperature").get().getValue())
                .isEqualTo(Double.NEGATIVE_INFINITY));
    }

    @Test
    public void testBlockingReadOnASeriesNamedBlock() {
        try (LogCaptor logCaptor = LogCaptor.forClass(DefaultPromise.class)) {
            RTimeSeriesNative named = redisson.getTimeSeriesNative("BLOCK");
            named.add(1000, 10);

            assertThat(named.read(TSReadArgs.fromEarliest().maxCount(1))).hasSize(1);

            assertThat(named.read(TSReadArgs.fromNext().block(Duration.ofMillis(4500), 1))).isEmpty();

            assertThat(logCaptor.getLogs())
                    .noneMatch(m -> m.contains("An exception was thrown by"));
            assertThat(logCaptor.getLogEvents())
                    .flatExtracting(event -> event.getThrowable().map(Throwable::getClass).stream()
                            .map(Class::getName).collect(Collectors.toList()))
                    .doesNotContain(NumberFormatException.class.getName());
        }
    }

    @Test
    public void testCreateIfAbsentNoArgs() {
        assertThat(ts.createIfAbsent()).isTrue();
        assertThat(ts.createIfAbsent()).isFalse();
        assertThat(ts.getInfo().getTotalSamples()).isZero();
    }

    @Test
    public void testAddAll() {
        ts.create();

        Map<Long, Double> samples = new LinkedHashMap<>();
        samples.put(1000L, 10.0);
        samples.put(2000L, 20.0);
        samples.put(3000L, 30.0);

        assertThat(ts.addAll(samples)).containsExactly(1000L, 2000L, 3000L);
        assertThat(ts.range(0, 5000)).extracting(TSSample::getValue).containsExactly(10.0, 20.0, 30.0);
    }

    @Test
    public void testAddAllEmpty() {
        assertThat(ts.addAll(Collections.emptyMap())).isEmpty();
    }

    @Test
    public void testAddAllDoesNotCreateTheSeries() {
        assertThat(ts.isExists()).isFalse();

        assertThatThrownBy(() -> ts.addAll(Collections.singletonMap(1000L, 10.0)))
                .isInstanceOf(RedisException.class);
    }

    @Test
    public void testFirst() {
        assertThat(ts.createIfAbsent()).isTrue();
        assertThat(ts.first()).isNull();

        ts.add(2000, 20);
        ts.add(1000, 10);
        ts.add(3000, 30);

        assertThat(ts.first()).isEqualTo(new TSSample(1000, new double[]{10}));
        assertThat(ts.get()).isEqualTo(new TSSample(3000, new double[]{30}));
    }

    @Test
    public void testSizeAndTimestamps() {
        ts.create();
        assertThat(ts.size()).isZero();

        assertThat(ts.firstTimestamp()).isZero();
        assertThat(ts.lastTimestamp()).isZero();

        ts.add(1000, 10);
        ts.add(5000, 50);

        assertThat(ts.size()).isEqualTo(2);
        assertThat(ts.firstTimestamp()).isEqualTo(1000);
        assertThat(ts.lastTimestamp()).isEqualTo(5000);
    }

    @Test
    public void testIteratorPagesAcrossWindows() {
        for (int i = 1; i <= 25; i++) {
            ts.add(i * 1000, i);
        }

        List<TSSample> seen = new ArrayList<>();
        ts.iterator(10).forEachRemaining(seen::add);

        assertThat(seen).hasSize(25);
        assertThat(seen).extracting(TSSample::getTimestamp).isSorted();
        assertThat(seen.get(0).getValue()).isEqualTo(1);
        assertThat(seen.get(24).getValue()).isEqualTo(25);
    }

    @Test
    public void testIteratorWhenTheLastWindowIsExactlyFull() {
        for (int i = 1; i <= 20; i++) {
            ts.add(i * 1000, i);
        }

        assertThat(ts.stream(10)).hasSize(20);
    }

    @Test
    public void testIteratorOnEmptyAndAbsentSeries() {
        assertThatThrownBy(() -> ts.iterator().hasNext()).isInstanceOf(RedisException.class);

        ts.create();
        assertThat(ts.iterator().hasNext()).isFalse();
        assertThatThrownBy(() -> ts.iterator().next()).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void testIteratorFailsRatherThanTruncatingWhenTheSeriesGoes() {
        for (int i = 1; i <= 5; i++) {
            ts.add(i * 1000, i);
        }

        Iterator<TSSample> iterator = ts.iterator(1);
        assertThat(iterator.next().getTimestamp()).isEqualTo(1000);
        ts.delete();

        assertThatThrownBy(iterator::hasNext).isInstanceOf(RedisException.class);
    }

    @Test
    public void testIteratorIsAViewNotASnapshot() {
        ts.add(1000, 10);
        ts.add(2000, 20);

        Iterator<TSSample> iterator = ts.iterator(1);
        assertThat(iterator.next().getTimestamp()).isEqualTo(1000);

        ts.add(3000, 30);
        assertThat(iterator.next().getTimestamp()).isEqualTo(2000);
        assertThat(iterator.next().getTimestamp()).isEqualTo(3000);
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    public void testIterable() {
        ts.add(1000, 10);
        ts.add(2000, 20);

        List<TSSample> seen = new ArrayList<>();
        for (TSSample sample : ts) {
            seen.add(sample);
        }

        assertThat(seen).extracting(TSSample::getTimestamp).containsExactly(1000L, 2000L);
    }

    @Test
    public void testStream() {
        for (int i = 1; i <= 12; i++) {
            ts.add(i * 1000, i);
        }

        assertThat(ts.stream().mapToDouble(TSSample::getValue).sum()).isEqualTo(78);
        assertThat(ts.stream(3).map(TSSample::getTimestamp)).containsExactly(
                1000L, 2000L, 3000L, 4000L, 5000L, 6000L, 7000L, 8000L, 9000L, 10000L, 11000L, 12000L);
    }

    @Test
    public void testIteratorAsync() throws Exception {
        for (int i = 1; i <= 25; i++) {
            ts.add(i * 1000, i);
        }

        AsyncIterator<TSSample> iterator = ts.iteratorAsync(10);
        List<TSSample> seen = new ArrayList<>();
        while (iterator.hasNext().toCompletableFuture().get()) {
            seen.add(iterator.next().toCompletableFuture().get());
        }

        assertThat(seen).hasSize(25);
        assertThat(seen).extracting(TSSample::getTimestamp).isSorted();
    }

    @Test
    public void testGetLabels() {
        RTimeSeriesNative labelled = redisson.getTimeSeriesNative("labelled");
        labelled.create(TSCreateArgs.defaults().label("area", "32").label("host", "a"));

        assertThat(labelled.getLabels()).containsOnly(entry("area", "32"), entry("host", "a"));

        labelled.alter(TSAlterArgs.defaults().label("host", "b"));
        assertThat(labelled.getLabels()).containsOnly(entry("host", "b"));
    }

    @Test
    public void testGetLabelsOnAnUnlabelledSeries() {
        RTimeSeriesNative plain = redisson.getTimeSeriesNative("plain");
        plain.create();

        assertThat(plain.getLabels()).isEmpty();
    }

    @Test
    public void testEveryBatchResponseMatchesItsMethod() {
        RBatch batch = redisson.createBatch();
        RTimeSeriesNativeAsync batched = batch.getTimeSeriesNative("batched");
        batched.createAsync(TSCreateArgs.defaults().label("area", "32"));
        batched.addAsync(1000, 10);
        RFuture<Map<String, String>> labels = batched.getLabelsAsync();
        RFuture<Long> size = batched.sizeAsync();
        RFuture<Long> first = batched.firstTimestampAsync();
        RFuture<Long> last = batched.lastTimestampAsync();
        RFuture<TSSample> firstSample = batched.firstAsync();

        RFuture<Boolean> present = batched.createIfAbsentAsync();

        List<?> responses = batch.execute().getResponses();

        assertThat(responses).hasSize(8);
        assertThat(responses.get(2)).isInstanceOf(Map.class).isEqualTo(labels.toCompletableFuture().join());
        assertThat(responses.get(3)).isEqualTo(1L);
        assertThat(responses.get(4)).isEqualTo(1000L);
        assertThat(responses.get(5)).isEqualTo(1000L);
        assertThat(responses.get(6)).isInstanceOf(TSSample.class)
                .isEqualTo(new TSSample(1000, new double[]{10}));
        assertThat(responses.get(7)).isEqualTo(false);

        assertThat(labels.toCompletableFuture().join()).containsExactly(entry("area", "32"));
        assertThat(size.toCompletableFuture().join()).isEqualTo(1);
        assertThat(first.toCompletableFuture().join()).isEqualTo(1000);
        assertThat(last.toCompletableFuture().join()).isEqualTo(1000);
        assertThat(firstSample.toCompletableFuture().join()).isEqualTo(new TSSample(1000, new double[]{10}));
        assertThat(present.toCompletableFuture().join()).isFalse();
    }

    @Test
    public void testInBatch() {
        RBatch batch = redisson.createBatch();
        RTimeSeriesNativeAsync batched = batch.getTimeSeriesNative("temperature");
        batched.createAsync(TSCreateArgs.defaults().label("area", "32"));
        batched.addAsync(1000, 10);
        batched.addAsync(2000, 20);
        RFuture<List<TSSample>> range = batched.rangeAsync(0, 5000);

        BatchResult<?> result = batch.execute();

        assertThat(result.getResponses()).hasSize(4);
        assertThat(range.toCompletableFuture().join())
                .extracting(TSSample::getTimestamp).containsExactly(1000L, 2000L);
        assertThat(ts.getInfo().getLabels()).containsEntry("area", "32");
    }

    @Test
    public void testSamplesAreValueObjects() {
        TSSample first = new TSSample(1000, new double[]{10, 20});
        TSSample same = new TSSample(1000, new double[]{10, 20});
        TSSample other = new TSSample(1000, new double[]{10, 21});

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(other);
        assertThat(first.toString()).contains("1000").contains("20.0");
        assertThat(Arrays.asList(first)).containsExactly(same);
    }

}
