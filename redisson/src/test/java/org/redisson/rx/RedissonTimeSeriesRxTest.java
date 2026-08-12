package org.redisson.rx;

import org.junit.jupiter.api.Test;
import org.redisson.api.RTimeSeriesRx;
import org.redisson.api.TimeSeriesEntry;

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

}
