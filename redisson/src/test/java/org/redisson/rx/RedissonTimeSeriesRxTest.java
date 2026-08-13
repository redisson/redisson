package org.redisson.rx;

import org.junit.jupiter.api.Test;
import org.redisson.api.RTimeSeriesRx;
import org.redisson.api.TimeSeriesEntry;
import org.redisson.api.ts.TimeSeriesReadArgs;
import org.redisson.api.ts.TimeSeriesInfo;
import java.time.Duration;
import org.redisson.client.codec.StringCodec;
import org.redisson.api.ts.TimeSeriesBucket;
import org.redisson.api.ts.TimeSeriesAggregationArgs;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTimeSeriesRxTest extends BaseRxTest {

    @Test
    public void testOrder() {
        RTimeSeriesRx<String, Object> t = redisson.getTimeSeries("test");
        sync(t.add(4, "40"));
        sync(t.add(2, "20"));
        sync(t.add(1, "10", 1, TimeUnit.SECONDS));

        Collection<TimeSeriesEntry<String, Object>> r11 = sync(t.entryRange(1, 5));
        assertThat(r11).containsExactly(new TimeSeriesEntry<>(1,"10"),
                                        new TimeSeriesEntry<>(2, "20"),
                                        new TimeSeriesEntry<>(4, "40"));
    }

    @Test
    public void testLabels() {
        RTimeSeriesRx<String, String> t = redisson.getTimeSeries("test");
        sync(t.add(1, "a1", "cpu"));
        sync(t.add(2, "b1", "mem"));
        sync(t.add(3, "plain"));

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
        RTimeSeriesRx<String, String> t = redisson.getTimeSeries("agg", StringCodec.INSTANCE);
        sync(t.add(1, "10"));
        sync(t.add(2, "20"));
        sync(t.add(500, "30"));

        Collection<TimeSeriesBucket> buckets = sync(t.aggregate(
                TimeSeriesAggregationArgs.<String>between(0, 1000)
                        .bucket(Duration.ofMillis(100))
                        .avg().count()));

        assertThat(buckets).hasSize(2);
        assertThat(buckets.iterator().next().getAvg()).isEqualTo(15.0);
    }

    @Test
    public void testReadTailAndInfo() {
        RTimeSeriesRx<String, String> t = redisson.getTimeSeries("tail");
        sync(t.add(10, "a"));
        sync(t.add(20, "b"));

        assertThat(sync(t.readTail(TimeSeriesReadArgs.after(10))))
                .containsExactly(new TimeSeriesEntry<>(20, "b"));
        assertThat(sync(t.readTail(TimeSeriesReadArgs.after(20)))).isEmpty();

        TimeSeriesInfo info = sync(t.info());
        assertThat(info.getSize()).isEqualTo(2);
        assertThat(info.getFirstTimestamp()).isEqualTo(10);
        assertThat(info.getLastTimestamp()).isEqualTo(20);
    }

}
