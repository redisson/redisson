package org.redisson;

import org.junit.Test;
import org.redisson.api.RTimeSeriesReactive;
import org.redisson.api.RTimeSeriesRx;
import org.redisson.api.TimeSeriesEntry;
import org.redisson.rx.BaseRxTest;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTimeSeriesReactiveTest extends BaseReactiveTest {

    @Test
    public void testOrder() {
        RTimeSeriesReactive<String> t = redisson.getTimeSeries("test");
        sync(t.add(4, "40"));
        sync(t.add(2, "20"));
        sync(t.add(1, "10", 1, TimeUnit.SECONDS));

        Collection<TimeSeriesEntry<String>> r11 = sync(t.entryRange(1, 5));
        assertThat(r11).containsExactly(new TimeSeriesEntry<>(1,"10"),
                                        new TimeSeriesEntry<>(2, "20"),
                                        new TimeSeriesEntry<>(4, "40"));
    }
    
}
