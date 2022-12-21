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

}
