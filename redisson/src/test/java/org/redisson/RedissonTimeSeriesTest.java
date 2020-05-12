package org.redisson;

import org.junit.Test;
import org.redisson.api.RTimeSeries;
import org.redisson.api.TimeSeriesEntry;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTimeSeriesTest extends BaseTest {

    @Test
    public void testOrder() {
        RTimeSeries<String> t = redisson.getTimeSeries("test");
        t.add(4, "40");
        t.add(2, "20");
        t.add(1, "10", 1, TimeUnit.SECONDS);

        Collection<TimeSeriesEntry<String>> r11 = t.entryRange(1, 5);
        assertThat(r11).containsExactly(new TimeSeriesEntry<>(1,"10"),
                                        new TimeSeriesEntry<>(2, "20"),
                                        new TimeSeriesEntry<>(4, "40"));
    }

    @Test
    public void testCleanup() throws InterruptedException {
        RTimeSeries<String> t = redisson.getTimeSeries("test");
        t.add(1, "10", 1, TimeUnit.SECONDS);

        Thread.sleep(5000);

        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testIterator() {
        RTimeSeries<String> t = redisson.getTimeSeries("test");
        t.add(1, "10");
        t.add(3, "30");

        Iterator<String> iter = t.iterator(2);
        assertThat(iter.next()).isEqualTo("10");
        assertThat(iter.next()).isEqualTo("30");
        assertThat(iter.hasNext()).isFalse();
    }

    @Test
    public void testRemove() {
        RTimeSeries<String> t = redisson.getTimeSeries("test");
        t.add(1, "10");
        t.add(2, "10");
        t.add(3, "30");
        t.add(4, "40");

        assertThat(t.removeRange(2, 3)).isEqualTo(2);
        assertThat(t.size()).isEqualTo(2);
        assertThat(t.range(1, 4)).containsExactly("10", "40");
        assertThat(t.rangeReversed(1, 4)).containsExactly("40", "10");

        assertThat(t.remove(4)).isTrue();
        assertThat(t.remove(5)).isFalse();
        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void test() {
        RTimeSeries<String> t = redisson.getTimeSeries("test");
        t.add(1, "10");
        t.add(2, "10");
        t.add(3, "30");
        t.add(4, "40");
        assertThat(t.size()).isEqualTo(4);
        assertThat(t.get(3)).isEqualTo("30");

        assertThat(t.first()).isEqualTo("10");
        assertThat(t.first(2)).containsExactly("10", "10");
        assertThat(t.last()).isEqualTo("40");
        assertThat(t.last(2)).containsExactly("30", "40");

        Collection<String> r = t.range(1, 3);
        assertThat(r).containsExactly("10", "10", "30");

        Collection<TimeSeriesEntry<String>> r11 = t.entryRange(1, 3);
        assertThat(r11).containsExactly(new TimeSeriesEntry<>(1,"10"),
                                        new TimeSeriesEntry<>(2, "10"),
                                        new TimeSeriesEntry<>(3, "30"));

        Collection<TimeSeriesEntry<String>> r12 = t.entryRangeReversed(1, 3);
        assertThat(r12).containsExactly(new TimeSeriesEntry<>(3, "30"),
                                        new TimeSeriesEntry<>(2, "10"),
                                        new TimeSeriesEntry<>(1,"10"));

        Collection<String> r1 = t.range(1, 3);
        assertThat(r1).containsExactly("10", "10", "30");

        Collection<String> r2 = t.rangeReversed(1, 3);
        assertThat(r2).containsExactly("30", "10", "10");

        Collection<String> r3 = t.rangeReversed(2, 10);
        assertThat(r3).containsExactly("40", "30", "10");
    }

    @Test
    public void testTTLLast() throws InterruptedException {
        RTimeSeries<String> t = redisson.getTimeSeries("test");
        t.add(1, "10");
        t.add(2, "10");
        t.add(3, "30");
        t.add(4, "40", 1, TimeUnit.SECONDS);
        assertThat(t.size()).isEqualTo(4);
        assertThat(t.get(3)).isEqualTo("30");

        Thread.sleep(1100);

        assertThat(t.size()).isEqualTo(3);
        assertThat(t.get(4)).isNull();

        assertThat(t.firstTimestamp()).isEqualTo(1);
        assertThat(t.first()).isEqualTo("10");
        assertThat(t.first(2)).containsExactly("10", "10");
        assertThat(t.lastTimestamp()).isEqualTo(3);
        assertThat(t.last()).isEqualTo("30");
        assertThat(t.last(2)).containsExactly("10", "30");

        Collection<String> r = t.range(1, 3);
        assertThat(r).containsExactly("10", "10", "30");

        Collection<String> r2 = t.rangeReversed(1, 3);
        assertThat(r2).containsExactly("30", "10", "10");
    }

    @Test
    public void testTTLFirst() throws InterruptedException {
        RTimeSeries<String> t = redisson.getTimeSeries("test");
        t.add(1, "10", 1, TimeUnit.SECONDS);
        t.add(2, "10");
        t.add(3, "30");
        t.add(4, "40");
        assertThat(t.size()).isEqualTo(4);
        assertThat(t.get(3)).isEqualTo("30");

        Thread.sleep(1000);

        assertThat(t.size()).isEqualTo(3);
        assertThat(t.get(1)).isNull();

        assertThat(t.firstTimestamp()).isEqualTo(2);
        assertThat(t.first()).isEqualTo("10");
        assertThat(t.first(2)).containsExactly("10", "30");
        assertThat(t.lastTimestamp()).isEqualTo(4);
        assertThat(t.last()).isEqualTo("40");
        assertThat(t.last(2)).containsExactly("30", "40");

        Collection<String> r = t.range(1, 3);
        assertThat(r).containsExactly("10", "30");

        Collection<String> r2 = t.rangeReversed(1, 3);
        assertThat(r2).containsExactly("30", "10");
    }

    @Test
    public void testPoll() {
        RTimeSeries<String> t = redisson.getTimeSeries("test");
        t.add(1, "10");
        t.add(2, "20");
        t.add(3, "30");

        assertThat(t.pollFirst()).isEqualTo("10");
        assertThat(t.size()).isEqualTo(2);
        assertThat(t.pollLast()).isEqualTo("30");
        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void testPollList() {
        RTimeSeries<String> t = redisson.getTimeSeries("test");
        t.add(1, "10");
        t.add(2, "20");
        t.add(3, "30");
        t.add(4, "40");
        t.add(5, "50");
        t.add(6, "60");

        assertThat(t.pollFirst(2)).containsExactly("10", "20");
        assertThat(t.size()).isEqualTo(4);
        assertThat(t.pollLast(2)).containsExactly("50", "60");
        assertThat(t.size()).isEqualTo(2);
    }

}
