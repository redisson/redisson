package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RTimeSeriesNative;
import org.redisson.api.RTimeSeriesNatives;
import org.redisson.api.RedissonClient;
import org.redisson.api.tsnative.TSAggregation;
import org.redisson.api.tsnative.TSCreateArgs;
import org.redisson.api.tsnative.TSGroupedRangeArgs;
import org.redisson.api.tsnative.TSMultiGetArgs;
import org.redisson.api.tsnative.TSMultiRangeArgs;
import org.redisson.api.tsnative.TSReducer;
import org.redisson.api.tsnative.TSSample;
import org.redisson.api.tsnative.TSSeriesSample;
import org.redisson.api.tsnative.TSSeriesSamples;
import org.redisson.client.RedisException;
import org.redisson.config.Config;
import org.redisson.config.Protocol;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTimeSeriesNativesTest extends RedisDockerTest {

    private RTimeSeriesNatives multi;

    @BeforeEach
    public void setUp() {
        multi = redisson.getTimeSeriesNatives();

        RTimeSeriesNative first = redisson.getTimeSeriesNative("cpu:1");
        first.create(TSCreateArgs.defaults().label("area", "32").label("host", "a"));
        RTimeSeriesNative second = redisson.getTimeSeriesNative("cpu:2");
        second.create(TSCreateArgs.defaults().label("area", "32").label("host", "b"));

        first.add(1000, 10);
        first.add(2000, 20);
        first.add(3000, 30);
        second.add(1000, 100);
        second.add(2000, 200);
        second.add(3000, 300);
    }

    private static Map<Long, Double> ordered(Object... timestampsAndValues) {
        Map<Long, Double> samples = new LinkedHashMap<>();
        for (int i = 0; i < timestampsAndValues.length; i += 2) {
            samples.put((Long) timestampsAndValues[i], (Double) timestampsAndValues[i + 1]);
        }
        return samples;
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
    public void testAddAll() {
        Map<String, Map<Long, Double>> samples = new LinkedHashMap<>();
        samples.put("cpu:1", ordered(4000L, 40.0, 5000L, 50.0));
        samples.put("cpu:2", ordered(4000L, 400.0));

        Map<String, List<Long>> timestamps = multi.addAll(samples);

        assertThat(timestamps).containsOnlyKeys("cpu:1", "cpu:2");
        assertThat(timestamps.get("cpu:1")).containsExactly(4000L, 5000L);
        assertThat(timestamps.get("cpu:2")).containsExactly(4000L);
        assertThat(redisson.getTimeSeriesNative("cpu:1").getInfo().getTotalSamples()).isEqualTo(5);
        assertThat(redisson.getTimeSeriesNative("cpu:2").getInfo().getTotalSamples()).isEqualTo(4);
    }

    @Test
    public void testAddAllEmpty() {
        assertThat(multi.addAll(Collections.emptyMap())).isEmpty();
    }

    @Test
    public void testAddAllDoesNotCreateSeries() {
        Map<String, Map<Long, Double>> samples = new LinkedHashMap<>();
        samples.put("cpu:1", ordered(4000L, 40.0));
        samples.put("cpu:absent", ordered(4000L, 40.0));

        assertThatThrownBy(() -> multi.addAll(samples))
                .isInstanceOf(RedisException.class);
    }

    @Test
    public void testGetAll() {
        Map<String, TSSeriesSample> samples = multi.getAll(TSMultiGetArgs.filter("area=32"));

        assertThat(samples).containsOnlyKeys("cpu:1", "cpu:2");
        assertThat(samples.values()).allSatisfy(s -> assertThat(s.getLabels()).isEmpty());
        assertThat(samples.values()).extracting(s -> s.getSample().getTimestamp()).containsOnly(3000L);
    }

    @Test
    public void testGetAllWithLabels() {
        Map<String, TSSeriesSample> samples = multi.getAll(TSMultiGetArgs.filter("area=32").withLabels());

        assertThat(samples).containsOnlyKeys("cpu:1", "cpu:2");
        assertThat(samples.values()).allSatisfy(s -> assertThat(s.getLabels()).containsEntry("area", "32"));
        assertThat(samples.get("cpu:1").getLabels()).containsEntry("host", "a");
        assertThat(samples.get("cpu:2").getLabels()).containsEntry("host", "b");
    }

    @Test
    public void testGetAllSelectedLabels() {
        Map<String, TSSeriesSample> samples = multi.getAll(
                TSMultiGetArgs.filter("area=32").selectedLabels("host", "absent"));

        assertThat(samples.values()).allSatisfy(s -> {
            assertThat(s.getLabels()).containsOnlyKeys("host", "absent");

            assertThat(s.getLabels().get("absent")).isNull();
        });
    }

    @Test
    public void testGetAllRequiresFilter() {
        assertThatThrownBy(() -> multi.getAll(TSMultiGetArgs.filter()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testGetAllFilterExpressions() {
        assertThat(multi.getAll(TSMultiGetArgs.filter("area=32", "host=a"))).containsOnlyKeys("cpu:1");
        assertThat(multi.getAll(TSMultiGetArgs.filter("area=32", "host!=a"))).containsOnlyKeys("cpu:2");
        assertThat(multi.getAll(TSMultiGetArgs.filter("area=32", "host=(a,b)"))).hasSize(2);
    }

    @Test
    public void testRange() {
        Map<String, TSSeriesSamples> series = multi.range(TSMultiRangeArgs.filter("area=32").withLabels());

        assertThat(series).containsOnlyKeys("cpu:1", "cpu:2");

        TSSeriesSamples first = series.get("cpu:1");
        assertThat(first.getLabels()).contains(entry("area", "32"), entry("host", "a"));
        assertThat(first.getSamples()).containsExactly(
                new TSSample(1000, new double[]{10}),
                new TSSample(2000, new double[]{20}),
                new TSSample(3000, new double[]{30}));
    }

    @Test
    public void testRangeReversed() {
        Map<String, TSSeriesSamples> series = multi.rangeReversed(TSMultiRangeArgs.filter("host=a"));

        assertThat(series).containsOnlyKeys("cpu:1");
        assertThat(series.get("cpu:1").getSamples())
                .extracting(TSSample::getTimestamp).containsExactly(3000L, 2000L, 1000L);
    }

    @Test
    public void testRangeBoundsAndCount() {
        // host=a selects cpu:1 alone, so every assertion below reads that one series by name.
        assertThat(multi.range(TSMultiRangeArgs.filter("host=a").range(2000, 3000)).get("cpu:1").getSamples())
                .extracting(TSSample::getTimestamp).containsExactly(2000L, 3000L);
        assertThat(multi.range(TSMultiRangeArgs.filter("host=a").from(3000)).get("cpu:1").getSamples())
                .extracting(TSSample::getTimestamp).containsExactly(3000L);
        assertThat(multi.range(TSMultiRangeArgs.filter("host=a").to(1000)).get("cpu:1").getSamples())
                .extracting(TSSample::getTimestamp).containsExactly(1000L);
        assertThat(multi.range(TSMultiRangeArgs.filter("host=a").count(2)).get("cpu:1").getSamples())
                .hasSize(2);
    }

    @Test
    public void testRangeFilters() {
        assertThat(multi.range(TSMultiRangeArgs.filter("host=a").filterByValue(15, 25))
                .get("cpu:1").getSamples()).containsExactly(new TSSample(2000, new double[]{20}));
        assertThat(multi.range(TSMultiRangeArgs.filter("host=a").filterByTimestamp(1000, 3000))
                .get("cpu:1").getSamples()).hasSize(2);
    }

    @Test
    public void testRangeAggregation() {
        Map<String, TSSeriesSamples> series = multi.range(TSMultiRangeArgs.filter("host=a")
                .aggregation(Duration.ofSeconds(2), TSAggregation.MIN, TSAggregation.MAX));

        List<TSSample> buckets = series.get("cpu:1").getSamples();
        assertThat(buckets).hasSize(2);
        assertThat(buckets.get(0).getValues()).containsExactly(10, 10);
        assertThat(buckets.get(1).getValues()).containsExactly(20, 30);
    }

    @Test
    public void testRangeGroupBy() {
        Map<String, TSSeriesSamples> groups = multi.range(TSMultiRangeArgs.filter("area=32")
                .groupBy("area", TSReducer.SUM));

        assertThat(groups).containsOnlyKeys("area=32");
        assertThat(groups.get("area=32").getSamples()).containsExactly(
                new TSSample(1000, new double[]{110}),
                new TSSample(2000, new double[]{220}),
                new TSSample(3000, new double[]{330}));
    }

    @Test
    public void testRangeAllReducers() {
        for (TSReducer reducer : TSReducer.values()) {
            Map<String, TSSeriesSamples> groups = multi.range(TSMultiRangeArgs.filter("area=32")
                    .groupBy("area", reducer));

            assertThat(groups).as("reducer %s", reducer).containsOnlyKeys("area=32");
        }
    }

    @Test
    public void testRangeExcludeEmpty() {
        redisson.getTimeSeriesNative("cpu:3").create(TSCreateArgs.defaults().label("area", "99"));
        redisson.getTimeSeriesNative("cpu:4").create(TSCreateArgs.defaults().label("area", "99"));
        redisson.getTimeSeriesNative("cpu:4").add(1000, 1);

        Map<String, TSSeriesSamples> all = multi.range(TSMultiRangeArgs.filter("area=99"));
        Map<String, TSSeriesSamples> nonEmpty =
                multi.range(TSMultiRangeArgs.filter("area=99").excludeEmpty());

        assertThat(all).containsOnlyKeys("cpu:3", "cpu:4");
        assertThat(nonEmpty).containsOnlyKeys("cpu:4");
    }

    @Test
    public void testGroupByAndExcludeEmptyAreExclusive() {
        assertThatThrownBy(() -> multi.range(TSMultiRangeArgs.filter("area=32")
                .groupBy("area", TSReducer.SUM)
                .excludeEmpty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    public void testGroupedRange() {
        List<TSSample> rows = multi.groupedRange(
                TSGroupedRangeArgs.keys("cpu:1", "cpu:2").range(1000, 3000));

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getTimestamp()).isEqualTo(1000);

        assertThat(rows.get(0).getValues()).containsExactly(10, 100);
        assertThat(rows.get(2).getValues()).containsExactly(30, 300);

        assertThat(rows.get(0).getValue()).isEqualTo(10);
    }

    @Test
    public void testGroupedRangeReversed() {
        List<TSSample> rows = multi.groupedRangeReversed(
                TSGroupedRangeArgs.keys("cpu:1", "cpu:2").range(1000, 3000));

        assertThat(rows).extracting(TSSample::getTimestamp).containsExactly(3000L, 2000L, 1000L);
    }

    @Test
    public void testGroupedRangeFillsMissingWithNaN() {
        redisson.getTimeSeriesNative("cpu:3").create();
        redisson.getTimeSeriesNative("cpu:3").add(2000, 7);

        List<TSSample> rows = multi.groupedRange(
                TSGroupedRangeArgs.keys("cpu:1", "cpu:3").range(1000, 3000));

        assertThat(rows.get(0).getValues()[1]).isNaN();
        assertThat(rows.get(1).getValues()).containsExactly(20, 7);
        assertThat(rows.get(2).getValues()[1]).isNaN();
    }

    @Test
    public void testGroupedRangeSameAggregationForEveryKey() {
        List<TSSample> rows = multi.groupedRange(TSGroupedRangeArgs.keys("cpu:1", "cpu:2")
                .range(1000, 3000)
                .aggregation(Duration.ofSeconds(2), TSAggregation.AVG));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getValues()).containsExactly(10, 100);
        assertThat(rows.get(1).getValues()).containsExactly(25, 250);
    }

    @Test
    public void testGroupedRangePerKeyAggregations() {
        List<List<TSAggregation>> perKey = new ArrayList<>();
        perKey.add(Arrays.asList(TSAggregation.MIN, TSAggregation.MAX));
        perKey.add(Collections.singletonList(TSAggregation.SUM));

        List<TSSample> rows = multi.groupedRange(TSGroupedRangeArgs.keys("cpu:1", "cpu:2")
                .range(1000, 3000)
                .aggregations(Duration.ofSeconds(2), perKey));

        assertThat(rows.get(0).getValues()).containsExactly(10, 10, 100);
        assertThat(rows.get(1).getValues()).containsExactly(20, 30, 500);
    }

    @Test
    public void testGroupedRangeRejectsMismatchedAggregations() {
        List<List<TSAggregation>> perKey =
                Collections.singletonList(Collections.singletonList(TSAggregation.AVG));

        assertThatThrownBy(() -> multi.groupedRange(TSGroupedRangeArgs.keys("cpu:1", "cpu:2")
                .range(1000, 3000)
                .aggregations(Duration.ofSeconds(2), perKey)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one entry per key");
    }

    @Test
    public void testGroupedRangeRejectsNoKeys() {
        assertThatThrownBy(() -> multi.groupedRange(TSGroupedRangeArgs.keys().all()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testQueryIndex() {
        assertThat(multi.queryIndex("area=32")).containsExactlyInAnyOrder("cpu:1", "cpu:2");
        assertThat(multi.queryIndex("area=32", "host=b")).containsExactly("cpu:2");
        assertThat(multi.queryIndex("area=99")).isEmpty();
    }

    @Test
    public void testQueryIndexRequiresFilter() {
        assertThatThrownBy(() -> multi.queryIndex())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testLabelNames() {
        assertThat(multi.labelNames("area=32")).containsExactlyInAnyOrder("area", "host");
        assertThat(multi.labelNames()).contains("area", "host");
    }

    @Test
    public void testLabelValues() {
        assertThat(multi.labelValues("host", "area=32")).containsExactlyInAnyOrder("a", "b");
        assertThat(multi.labelValues("area")).containsExactly("32");
    }

    @Test
    public void testResp3ReadsMatchResp2() {
        Map<String, TSSeriesSample> expectedGet = multi.getAll(TSMultiGetArgs.filter("area=32").withLabels());
        Map<String, TSSeriesSamples> expectedRange = multi.range(TSMultiRangeArgs.filter("area=32").withLabels());
        List<TSSample> expectedGrouped =
                multi.groupedRange(TSGroupedRangeArgs.keys("cpu:1", "cpu:2").all());

        withResp3(resp3 -> {
            RTimeSeriesNatives other = resp3.getTimeSeriesNatives();

            assertThat(other.getAll(TSMultiGetArgs.filter("area=32").withLabels()))
                    .containsExactlyInAnyOrderEntriesOf(expectedGet);
            assertThat(other.range(TSMultiRangeArgs.filter("area=32").withLabels()))
                    .containsExactlyInAnyOrderEntriesOf(expectedRange);
            assertThat(other.groupedRange(TSGroupedRangeArgs.keys("cpu:1", "cpu:2").all()))
                    .isEqualTo(expectedGrouped);
            assertThat(other.queryIndex("area=32")).containsExactlyInAnyOrder("cpu:1", "cpu:2");
            assertThat(other.labelValues("host", "area=32")).containsExactlyInAnyOrder("a", "b");
        });
    }

    @Test
    public void testResp3SelectedLabelsKeepsAbsentAsNull() {
        withResp3(resp3 -> {
            Map<String, TSSeriesSample> samples = resp3.getTimeSeriesNatives()
                    .getAll(TSMultiGetArgs.filter("area=32").selectedLabels("host", "absent"));

            assertThat(samples.values()).allSatisfy(s -> {
                assertThat(s.getLabels()).containsOnlyKeys("host", "absent");
                assertThat(s.getLabels().get("absent")).isNull();
            });
        });
    }

    @Test
    public void testGroupByWithSeveralAggregatorsIsExclusive() {
        assertThatThrownBy(() -> multi.range(TSMultiRangeArgs.filter("area=32")
                .aggregation(Duration.ofSeconds(2), TSAggregation.AVG, TSAggregation.SUM)
                .groupBy("area", TSReducer.SUM)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");

        assertThat(multi.range(TSMultiRangeArgs.filter("area=32")
                .aggregation(Duration.ofSeconds(2), TSAggregation.AVG)
                .groupBy("area", TSReducer.SUM))).hasSize(1);
    }

    @Test
    public void testWithLabelsAndSelectedLabelsAreExclusive() {
        assertThatThrownBy(() -> multi.range(TSMultiRangeArgs.filter("area=32")
                .withLabels()
                .selectedLabels("host")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");

        assertThatThrownBy(() -> multi.getAll(TSMultiGetArgs.filter("area=32")
                .withLabels()
                .selectedLabels("host")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testSelectedLabelsRequiresALabel() {
        // SELECTED_LABELS with nothing after it is "should have at least 1 parameter" server-side.
        assertThatThrownBy(() -> multi.range(TSMultiRangeArgs.filter("area=32").selectedLabels()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one label");
    }

    @Test
    public void testAlignNeedsTheBoundItAlignsOn() {
        assertThatThrownBy(() -> multi.range(TSMultiRangeArgs.filter("area=32")
                .alignStart()
                .aggregation(Duration.ofSeconds(2), TSAggregation.AVG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicit start");

        assertThatThrownBy(() -> multi.range(TSMultiRangeArgs.filter("area=32")
                .alignEnd()
                .aggregation(Duration.ofSeconds(2), TSAggregation.AVG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicit end");

        assertThat(multi.range(TSMultiRangeArgs.filter("area=32")
                .range(1000, 3000)
                .alignStart()
                .aggregation(Duration.ofSeconds(2), TSAggregation.AVG))).hasSize(2);
    }

    @Test
    public void testResp3GroupByLabelsMatchResp2() {
        Map<String, TSSeriesSamples> expected = multi.range(TSMultiRangeArgs.filter("area=32")
                .withLabels()
                .groupBy("area", TSReducer.SUM));

        assertThat(expected).containsOnlyKeys("area=32");
        assertThat(expected.get("area=32").getLabels())
                .containsEntry("area", "32")
                .containsEntry("__reducer__", "sum")
                .containsEntry("__source__", "cpu:1,cpu:2");

        withResp3(resp3 -> assertThat(resp3.getTimeSeriesNatives().range(TSMultiRangeArgs.filter("area=32")
                .withLabels()
                .groupBy("area", TSReducer.SUM))).isEqualTo(expected));
    }

    @Test
    public void testResp3AggregatorsEntryIsNotReportedAsALabel() {
        Map<String, TSSeriesSamples> expected = multi.range(TSMultiRangeArgs.filter("host=a")
                .withLabels()
                .aggregation(Duration.ofSeconds(2), TSAggregation.AVG));

        withResp3(resp3 -> {
            Map<String, TSSeriesSamples> actual = resp3.getTimeSeriesNatives().range(TSMultiRangeArgs.filter("host=a")
                    .withLabels()
                    .aggregation(Duration.ofSeconds(2), TSAggregation.AVG));

            assertThat(actual).isEqualTo(expected);
            assertThat(actual.get("cpu:1").getLabels()).containsOnlyKeys("area", "host");
        });
    }

    @Test
    public void testAsync() throws Exception {
        assertThat(multi.queryIndexAsync("area=32").get()).containsExactlyInAnyOrder("cpu:1", "cpu:2");
        assertThat(multi.addAllAsync(Collections.singletonMap("cpu:1", ordered(4000L, 40.0))).get())
                .containsEntry("cpu:1", Collections.singletonList(4000L));
    }

    @Test
    public void testInClusterFansOutAndSplitsBySlot() {
        testInCluster(cluster -> {
            RTimeSeriesNatives clustered = cluster.getTimeSeriesNatives();
            cluster.getTimeSeriesNative("{a}m:1").create(TSCreateArgs.defaults().label("g", "1"));
            cluster.getTimeSeriesNative("{b}m:2").create(TSCreateArgs.defaults().label("g", "1"));

            // The two keys hash to different slots, so this exercises the per-slot split — and
            // the results must still come back in the order the samples were given.
            Map<String, Map<Long, Double>> samples = new LinkedHashMap<>();
            samples.put("{a}m:1", ordered(1000L, 10.0, 3000L, 30.0));
            samples.put("{b}m:2", ordered(2000L, 20.0));

            Map<String, List<Long>> timestamps = clustered.addAll(samples);
            assertThat(timestamps.get("{a}m:1")).containsExactly(1000L, 3000L);
            assertThat(timestamps.get("{b}m:2")).containsExactly(2000L);

            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                // A filter-based command has no slot; the module keeps a cluster-wide index, so
                // one node answers for the whole keyspace.
                assertThat(clustered.queryIndex("g=1")).containsExactlyInAnyOrder("{a}m:1", "{b}m:2");
                assertThat(clustered.labelValues("g")).containsExactly("1");
                assertThat(clustered.getAll(TSMultiGetArgs.filter("g=1")))
                        .containsOnlyKeys("{a}m:1", "{b}m:2");
                assertThat(clustered.range(TSMultiRangeArgs.filter("g=1")))
                        .containsOnlyKeys("{a}m:1", "{b}m:2");

                // TS.NRANGE names its keys, so they must share a slot for the server to align them.
                assertThat(clustered.groupedRange(TSGroupedRangeArgs.keys("{a}m:1").all()))
                        .extracting(TSSample::getTimestamp).containsExactly(1000L, 3000L);
            });
        });
    }

}
