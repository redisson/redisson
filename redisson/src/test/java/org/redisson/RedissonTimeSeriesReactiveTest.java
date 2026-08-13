package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RTimeSeriesReactive;
import org.redisson.api.TimeSeriesEntry;
import org.redisson.api.ts.TimeSeriesAddArgs;
import org.redisson.api.ts.TimeSeriesAggregationArgs;
import org.redisson.api.ts.TimeSeriesBucket;
import org.redisson.api.ts.TimeSeriesInfo;
import org.redisson.api.ts.TimeSeriesReadArgs;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTimeSeriesReactiveTest extends BaseReactiveTest {

    @Test
    public void testOrder() {
        RTimeSeriesReactive<String, Object> t = redisson.getTimeSeries("test");
        sync(t.add(TimeSeriesAddArgs.entry(4, "40")));
        sync(t.add(TimeSeriesAddArgs.entry(2, "20")));
        sync(t.add(TimeSeriesAddArgs.entry(1, "10").timeToLive(Duration.ofSeconds(1))));

        Collection<TimeSeriesEntry<String, Object>> r11 = sync(t.entryRange(1, 5));
        assertThat(r11).containsExactly(new TimeSeriesEntry<>(1,"10"),
                                        new TimeSeriesEntry<>(2, "20"),
                                        new TimeSeriesEntry<>(4, "40"));
    }

    @Test
    public void testLabels() {
        RTimeSeriesReactive<String, String> t = redisson.getTimeSeries("test");
        sync(t.add(TimeSeriesAddArgs.entry(1, "a1", "cpu")));
        sync(t.add(TimeSeriesAddArgs.entry(2, "b1", "mem")));
        sync(t.add(TimeSeriesAddArgs.entry(3, "plain")));

        assertThat(sync(t.rangeByLabel(0, 10, "cpu"))).containsExactly("a1");
        assertThat(sync(t.rangeReversedByLabel(0, 10, "cpu", 1))).containsExactly("a1");
        assertThat(sync(t.entryRangeByLabel(0, 10, "mem")))
                .containsExactly(new TimeSeriesEntry<>(2, "b1", "mem"));
        assertThat(sync(t.entryRangeReversedByLabel(0, 10, "mem", 1)))
                .containsExactly(new TimeSeriesEntry<>(2, "b1", "mem"));
        assertThat(sync(t.rangeByLabel(0, 10, null))).containsExactly("plain");
        assertThat(sync(t.labels())).containsExactlyInAnyOrder("cpu", "mem");
        assertThat(sync(t.labels(2, 2))).containsExactlyInAnyOrder("mem");
        assertThat(sync(t.removeRangeByLabel(0, 10, "cpu"))).isEqualTo(1);
    }

    @Test
    public void testAggregate() {
        RTimeSeriesReactive<String, String> t = redisson.getTimeSeries("agg", StringCodec.INSTANCE);
        sync(t.add(TimeSeriesAddArgs.entry(1, "10")));
        sync(t.add(TimeSeriesAddArgs.entry(2, "20")));
        sync(t.add(TimeSeriesAddArgs.entry(500, "30")));

        Collection<TimeSeriesBucket> buckets = sync(t.aggregate(
                TimeSeriesAggregationArgs.<String>between(0, 1000)
                        .bucket(Duration.ofMillis(100))
                        .avg().count()));

        assertThat(buckets).hasSize(2);
        assertThat(buckets.iterator().next().getAvg()).isEqualTo(15.0);
    }

    @Test
    public void testReadTailAndInfo() {
        RTimeSeriesReactive<String, String> t = redisson.getTimeSeries("tail");
        sync(t.add(TimeSeriesAddArgs.entry(10, "a")));
        sync(t.add(TimeSeriesAddArgs.entry(20, "b")));

        assertThat(sync(t.readTail(TimeSeriesReadArgs.after(10))))
                .containsExactly(new TimeSeriesEntry<>(20, "b"));
        assertThat(sync(t.readTail(TimeSeriesReadArgs.after(20)))).isEmpty();

        TimeSeriesInfo info = sync(t.info());
        assertThat(info.getSize()).isEqualTo(2);
        assertThat(info.getFirstTimestamp()).isEqualTo(10);
        assertThat(info.getLastTimestamp()).isEqualTo(20);
    }

    @Test
    public void testComparingPolicies() {
        RTimeSeriesReactive<String, String> t = redisson.getTimeSeries("cmp", StringCodec.INSTANCE);

        assertThat(sync(t.addIfLess(TimeSeriesAddArgs.entry(1, "10")))).isTrue();
        assertThat(sync(t.addIfLess(TimeSeriesAddArgs.entry(1, "4")))).isTrue();
        assertThat(sync(t.addIfLess(TimeSeriesAddArgs.entry(1, "9")))).isFalse();
        assertThat(sync(t.addIfGreater(TimeSeriesAddArgs.entry(2, "1")))).isTrue();
        assertThat(sync(t.addAndSum(TimeSeriesAddArgs.entry(2, "2")))).isFalse();
        assertThat(sync(t.get(2))).isEqualTo("3");

        assertThat(sync(t.addIfLess(TimeSeriesAddArgs.entry(1, "1")))).isTrue();
        assertThat(sync(t.addIfGreater(TimeSeriesAddArgs.entry(1, "0")))).isFalse();
        assertThat(sync(t.addAndSum(TimeSeriesAddArgs.entry(3, "7")))).isTrue();
        assertThat(sync(t.range(0, 10))).containsExactly("1", "3", "7");
    }

    @Test
    public void testComparingReportsACodecItCannotUseThroughTheResult() {
        RTimeSeriesReactive<String, String> t = redisson.getTimeSeries("binary");
        // building the publisher must not throw; the failure belongs to the subscriber
        reactor.core.publisher.Mono<Boolean> publisher = t.addIfLess(TimeSeriesAddArgs.entry(1, "1"));
        // the proxy invokes the async method reflectively, so a validation failure arrives
        // wrapped, exactly as it does for every other validating method in Redisson
        assertThatThrownBy(() -> sync(publisher))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("addIfLess() on 'binary'");
    }

    @Test
    public void testAddAndGet() {
        RTimeSeriesReactive<String, String> t = redisson.getTimeSeries("incr", StringCodec.INSTANCE);
        assertThat(sync(t.addAndGet(TimeSeriesAddArgs.entry(1, "5")))).isEqualTo(5.0);
        assertThat(sync(t.addAndGet(TimeSeriesAddArgs.entry(2, "3")))).isEqualTo(8.0);
        assertThat(sync(t.addAndGet(TimeSeriesAddArgs.entry(3, "1")))).isEqualTo(9.0);
        assertThat(sync(t.addAndGet(TimeSeriesAddArgs.entry(4, "1")))).isEqualTo(10.0);
        assertThat(sync(t.range(0, 10))).containsExactly("5", "8", "9", "10");
    }

    @Test
    public void testAddArgs() {
        RTimeSeriesReactive<String, String> t = redisson.getTimeSeries("args");
        assertThat(sync(t.add(TimeSeriesAddArgs.entry(1, "a")))).isTrue();
        assertThat(sync(t.add(TimeSeriesAddArgs.entry(1, "b")))).isTrue();
        assertThat(sync(t.getAll(1))).containsExactly("a", "b");
        // the window is anchored on the highest timestamp held, so this one is behind it
        assertThat(sync(t.add(TimeSeriesAddArgs.entry(-10, "behind")
                .retention(Duration.ofMillis(1))))).isFalse();
    }

    /**
     * The overloads the arguments object replaces are deprecated but still shipped, so this is
     * where they keep their coverage; everywhere else the tests use the arguments object.
     */
    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedAddOverloads() {
        RTimeSeriesReactive<String, String> t = redisson.getTimeSeries("deprecated");
        sync(t.add(1, "a"));
        sync(t.add(2, "b", "cpu"));
        sync(t.add(3, "c", 30, TimeUnit.SECONDS));
        sync(t.add(4, "d", Duration.ofSeconds(30)));
        sync(t.add(5, "e", "mem", Duration.ofSeconds(30)));
        sync(t.addAll(Collections.singletonMap(6L, "f"), 30, TimeUnit.SECONDS));

        assertThat(sync(t.entryRange(0, 10))).containsExactly(
                new TimeSeriesEntry<>(1, "a"),
                new TimeSeriesEntry<>(2, "b", "cpu"),
                new TimeSeriesEntry<>(3, "c"),
                new TimeSeriesEntry<>(4, "d"),
                new TimeSeriesEntry<>(5, "e", "mem"),
                new TimeSeriesEntry<>(6, "f"));
    }

}
