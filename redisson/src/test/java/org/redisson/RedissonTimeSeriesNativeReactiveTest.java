package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RTimeSeriesNativeReactive;
import org.redisson.api.RTimeSeriesNativesReactive;
import org.redisson.api.tsnative.TSAddArgs;
import org.redisson.api.tsnative.TSAggregation;
import org.redisson.api.tsnative.TSAlterArgs;
import org.redisson.api.tsnative.TSCreateArgs;
import org.redisson.api.tsnative.TSDuplicatePolicy;
import org.redisson.api.tsnative.TSGroupedRangeArgs;
import org.redisson.api.tsnative.TSIncrArgs;
import org.redisson.api.tsnative.TSMultiGetArgs;
import org.redisson.api.tsnative.TSMultiRangeArgs;
import org.redisson.api.tsnative.TSRangeArgs;
import org.redisson.api.tsnative.TSReadArgs;
import org.redisson.api.tsnative.TSRuleArgs;
import org.redisson.api.tsnative.TSSample;
import org.redisson.api.tsnative.TSSeriesSample;
import org.redisson.api.tsnative.TSSeriesSamples;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTimeSeriesNativeReactiveTest extends BaseReactiveTest {

    @Test
    public void testWritesAndReads() {
        RTimeSeriesNativeReactive ts = redisson.getTimeSeriesNative("temperature");

        sync(ts.create());
        assertThat(sync(ts.createIfAbsent(TSCreateArgs.defaults()))).isFalse();
        sync(ts.delete());

        sync(ts.create(TSCreateArgs.defaults().label("area", "32")));
        sync(ts.alter(TSAlterArgs.defaults().duplicatePolicy(TSDuplicatePolicy.LAST).label("area", "33")));

        assertThat(sync(ts.add(1000, 10))).isEqualTo(1000);
        assertThat(sync(ts.add(TSAddArgs.sample(2000, 20)))).isEqualTo(2000);

        assertThat(sync(ts.incrementBy(TSIncrArgs.value(1).timestamp(9000)))).isEqualTo(9000);
        assertThat(sync(ts.decrementBy(TSIncrArgs.value(1).timestamp(9500)))).isEqualTo(9500);
        assertThat(sync(ts.addCurrent(30))).isPositive();
        assertThat(sync(ts.incrementBy(2))).isPositive();
        assertThat(sync(ts.decrementBy(2))).isPositive();

        assertThat(sync(ts.get())).isNotNull();
        assertThat(sync(ts.getLatest())).isNotNull();
        assertThat(sync(ts.range(0, Long.MAX_VALUE))).isNotEmpty();
        assertThat(sync(ts.range(TSRangeArgs.all()))).isNotEmpty();
        assertThat(sync(ts.rangeReversed(0, Long.MAX_VALUE))).isNotEmpty();
        assertThat(sync(ts.rangeReversed(TSRangeArgs.all().count(1)))).hasSize(1);
        assertThat(sync(ts.read(0))).isNotEmpty();
        assertThat(sync(ts.read(TSReadArgs.fromEarliest().maxCount(1)))).hasSize(1);

        assertThat(sync(ts.removeRange(1000, 1000))).isEqualTo(1);
        assertThat(sync(ts.getInfo()).getLabels()).containsEntry("area", "33");
        assertThat(sync(ts.getLabels())).containsEntry("area", "33");
        assertThat(sync(ts.getDebugInfo()).getKeySelfName()).isEqualTo("temperature");
    }

    @Test
    public void testEmptySeriesCompletesEmpty() {
        RTimeSeriesNativeReactive ts = redisson.getTimeSeriesNative("empty");
        sync(ts.create());

        assertThat(ts.get().blockOptional()).isEmpty();
        assertThat(ts.getLatest().blockOptional()).isEmpty();
    }

    @Test
    public void testRules() {
        RTimeSeriesNativeReactive source = redisson.getTimeSeriesNative("src");
        RTimeSeriesNativeReactive destination = redisson.getTimeSeriesNative("dst");
        sync(source.create());
        sync(destination.create());

        sync(source.createRule(TSRuleArgs.destination("dst", TSAggregation.AVG, Duration.ofSeconds(2))));
        assertThat(sync(source.getInfo()).getRules()).hasSize(1);

        sync(source.deleteRule("dst"));
        assertThat(sync(source.getInfo()).getRules()).isEmpty();
    }

    @Test
    public void testMultiSeries() {
        RTimeSeriesNativesReactive multi = redisson.getTimeSeriesNatives();
        sync(redisson.getTimeSeriesNative("cpu:1").create(TSCreateArgs.defaults().label("area", "32")));
        sync(redisson.getTimeSeriesNative("cpu:2").create(TSCreateArgs.defaults().label("area", "32")));

        Map<String, Map<Long, Double>> samples = new LinkedHashMap<>();
        samples.put("cpu:1", Collections.singletonMap(1000L, 10.0));
        samples.put("cpu:2", Collections.singletonMap(1000L, 100.0));

        Map<String, List<Long>> added = sync(multi.addAll(samples));
        assertThat(added).containsOnlyKeys("cpu:1", "cpu:2");
        assertThat(added.get("cpu:1")).containsExactly(1000L);

        Map<String, TSSeriesSample> latest =
                sync(multi.getAll(TSMultiGetArgs.filter("area=32").withLabels()));
        assertThat(latest).containsOnlyKeys("cpu:1", "cpu:2");

        Map<String, TSSeriesSamples> ranges = sync(multi.range(TSMultiRangeArgs.filter("area=32")));
        assertThat(ranges).containsOnlyKeys("cpu:1", "cpu:2");
        assertThat(sync(multi.rangeReversed(TSMultiRangeArgs.filter("area=32"))))
                .containsOnlyKeys("cpu:1", "cpu:2");

        List<TSSample> grouped =
                sync(multi.groupedRange(TSGroupedRangeArgs.keys("cpu:1", "cpu:2").all()));
        assertThat(grouped).hasSize(1);
        assertThat(sync(multi.groupedRangeReversed(TSGroupedRangeArgs.keys("cpu:1", "cpu:2").all()))).hasSize(1);

        assertThat(sync(multi.queryIndex("area=32"))).containsExactlyInAnyOrder("cpu:1", "cpu:2");
        assertThat(sync(multi.labelNames("area=32"))).containsExactly("area");
        assertThat(sync(multi.labelValues("area", "area=32"))).containsExactly("32");
    }

    @Test
    public void testEmptyAddAll() {
        assertThat(sync(redisson.getTimeSeriesNatives().addAll(Collections.emptyMap()))).isEmpty();
    }

    @Test
    public void testAdditions() {
        RTimeSeriesNativeReactive ts = redisson.getTimeSeriesNative("extra");

        assertThat(sync(ts.createIfAbsent())).isTrue();
        assertThat(sync(ts.createIfAbsent())).isFalse();

        Map<Long, Double> samples = new LinkedHashMap<>();
        samples.put(1000L, 10.0);
        samples.put(2000L, 20.0);
        assertThat(sync(ts.addAll(samples))).containsExactly(1000L, 2000L);
        assertThat(sync(ts.addAll(Collections.emptyMap()))).isEmpty();

        assertThat(sync(ts.first())).isEqualTo(new TSSample(1000, new double[]{10}));
        assertThat(sync(ts.size())).isEqualTo(2);
        assertThat(sync(ts.firstTimestamp())).isEqualTo(1000);
        assertThat(sync(ts.lastTimestamp())).isEqualTo(2000);
    }

    @Test
    public void testFirstOnEmptySeriesCompletesEmpty() {
        RTimeSeriesNativeReactive ts = redisson.getTimeSeriesNative("blank");
        sync(ts.create());

        assertThat(ts.first().blockOptional()).isEmpty();
    }

    @Test
    public void testIteratorPagesLazily() {
        RTimeSeriesNativeReactive ts = redisson.getTimeSeriesNative("walk");
        Map<Long, Double> samples = new LinkedHashMap<>();
        for (int i = 1; i <= 25; i++) {
            samples.put((long) i * 1000, (double) i);
        }
        sync(ts.create());
        sync(ts.addAll(samples));

        assertThat(ts.iterator(10).collectList().block()).hasSize(25);
        assertThat(ts.iterator().map(TSSample::getTimestamp).collectList().block())
                .isSorted().hasSize(25);

        assertThat(ts.iterator(2).take(3).map(TSSample::getValue).collectList().block())
                .containsExactly(1.0, 2.0, 3.0);
    }

    @Test
    public void testAggregationValues() {
        RTimeSeriesNativeReactive ts = redisson.getTimeSeriesNative("agg");
        sync(ts.add(1000, 10));
        sync(ts.add(1500, 20));

        List<TSSample> samples = sync(ts.range(TSRangeArgs.all()
                .aggregation(Duration.ofSeconds(2), TSAggregation.MIN, TSAggregation.MAX)));

        assertThat(samples.get(0).getValues()).containsExactly(10, 20);
    }

}
