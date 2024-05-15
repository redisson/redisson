package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RTimeSeries;
import org.redisson.api.TimeSeriesEntry;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTimeSeriesTest extends RedisDockerTest {

    @Test
    public void testMultipleValues() {
        RTimeSeries<String, Object> ts = redisson.getTimeSeries("test");
        for (int i=0;i < 10000;i++){
            ts.add(System.currentTimeMillis(), "my-value",60,TimeUnit.DAYS);
        }
        assertThat(ts.size()).isEqualTo(10000);
    }

    @Test
    public void testPutAll() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        Map<Long, String> map = new HashMap<>();
        map.put(1L, "1");
        map.put(2L, "2");
        map.put(3L, "3");
        map.put(4L, "4");
        t.addAll(map);
        assertThat(t.size()).isEqualTo(4);
    }

    @Test
    public void testOrder() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(4, "40");
        t.add(2, "20", "label2");
        t.add(1, "10", 1, TimeUnit.SECONDS);

        Collection<TimeSeriesEntry<String, Object>> r11 = t.entryRange(1, 5);
        assertThat(r11).containsExactly(new TimeSeriesEntry<>(1,"10"),
                                        new TimeSeriesEntry<>(2, "20", "label2"),
                                        new TimeSeriesEntry<>(4, "40"));
    }

    @Test
    public void testCleanup() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(1, "10", 1, TimeUnit.SECONDS);

        Thread.sleep(6000);

        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testIterator() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        for (int i = 0; i < 19; i++) {
            t.add(i, "" + i*10);
        }

        Iterator<String> iter = t.iterator(3);
        for (int i = 0; i < 19; i++) {
            assertThat(iter.hasNext()).isTrue();
            assertThat(iter.next()).isEqualTo("" + i*10);
        }
        assertThat(iter.hasNext()).isFalse();
    }

    @Test
    public void testRangeReversed() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(1, "10");
        t.add(2, "20");
        t.add(3, "30");
        t.add(4, "40");

        assertThat(t.rangeReversed(1, 4, 2)).containsExactly("40", "30");
        assertThat(t.rangeReversed(1, 4, 0)).containsExactly("40", "30", "20", "10");

        RTimeSeries<String, Object> t2 = redisson.getTimeSeries("test2");
        t2.add(1, "10");
        t2.add(2, "20");
        t2.add(3, "30", 1, TimeUnit.SECONDS);
        t2.add(4, "40");

        Thread.sleep(1200);

        assertThat(t2.rangeReversed(1, 4, 2)).containsExactly("40", "20");
    }

    @Test
    public void testRange() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(1, "10");
        t.add(2, "10");
        t.add(3, "30");
        t.add(4, "40");

        assertThat(t.range(1, 4, 2)).containsExactly("10", "10");
        assertThat(t.range(1, 4, 0)).containsExactly("10", "10", "30", "40");

        RTimeSeries<String, Object> t2 = redisson.getTimeSeries("test2");
        t2.add(1, "10");
        t2.add(2, "10", 1, TimeUnit.SECONDS);
        t2.add(3, "30");
        t2.add(4, "40");

        Thread.sleep(1200);

        assertThat(t2.range(1, 4, 2)).containsExactly("10", "30");
    }

    @Test
    public void testRemove() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
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
    public void testGetEntry() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(1, "10");
        t.add(2, "10");
        t.add(3, "30");
        t.add(4, "40");
        assertThat(t.size()).isEqualTo(4);
        assertThat(t.get(3)).isEqualTo("30");
        assertThat(t.getEntry(3).getValue()).isEqualTo("30");

    }

    @Test
    public void testLabel() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(1, "10");
        t.add(2, "20", "label2");
        t.add(3, "30", "label3");

        TimeSeriesEntry<String, Object> ee = t.getEntry(2);
        assertThat(ee.getTimestamp()).isEqualTo(2);
        assertThat(ee.getValue()).isEqualTo("20");
        assertThat(ee.getLabel()).isEqualTo("label2");
    }

    @Test
    public void testGetAndRemoveEntry() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(1, "10", "100");
        t.add(2, "20");
        t.add(3, "30", "300", Duration.ofSeconds(2));
        t.add(4, "40");

        TimeSeriesEntry<String, String> e1 = t.getAndRemoveEntry(1);
        assertThat(e1.getValue()).isEqualTo("10");
        assertThat(e1.getTimestamp()).isEqualTo(1);
        assertThat(e1.getLabel()).isEqualTo("100");

        TimeSeriesEntry<String, String> e2 = t.getAndRemoveEntry(2);
        assertThat(e2.getValue()).isEqualTo("20");
        assertThat(e2.getTimestamp()).isEqualTo(2);
        assertThat(e2.getLabel()).isNull();

        TimeSeriesEntry<String, String> e3 = t.getAndRemoveEntry(3);
        assertThat(e3.getValue()).isEqualTo("30");
        assertThat(e3.getTimestamp()).isEqualTo(3);
        assertThat(e3.getLabel()).isEqualTo("300");

        TimeSeriesEntry<String, String> e4 = t.getAndRemoveEntry(4);
        assertThat(e4.getValue()).isEqualTo("40");
        assertThat(e4.getTimestamp()).isEqualTo(4);
        assertThat(e4.getLabel()).isNull();
    }


    @Test
    public void testGetAndRemove() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(1, "10", "100");
        t.add(2, "20");
        t.add(3, "30", "300", Duration.ofSeconds(2));
        t.add(4, "40");

        String s1 = t.getAndRemove(1);
        assertThat(s1).isEqualTo("10");
        String s2 = t.getAndRemove(2);
        assertThat(s2).isEqualTo("20");
        String s3 = t.getAndRemove(3);
        assertThat(s3).isEqualTo("30");
        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void test() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(1, "10");
        t.add(2, "10");
        t.add(3, "30");
        t.add(4, "40");
        assertThat(t.size()).isEqualTo(4);
        assertThat(t.get(3)).isEqualTo("30");
        TimeSeriesEntry<String, Object> ee = t.getEntry(2);
        assertThat(ee.getTimestamp()).isEqualTo(2);
        assertThat(ee.getValue()).isEqualTo("10");
        assertThat(ee.getLabel()).isNull();

        assertThat(t.first()).isEqualTo("10");
        assertThat(t.first(2)).containsExactly("10", "10");
        assertThat(t.last()).isEqualTo("40");
        assertThat(t.last(2)).containsExactly("30", "40");

        Collection<String> r = t.range(1, 3);
        assertThat(r).containsExactly("10", "10", "30");

        Collection<TimeSeriesEntry<String, Object>> r11 = t.entryRange(1, 3);
        assertThat(r11).containsExactly(new TimeSeriesEntry<>(1,"10"),
                                        new TimeSeriesEntry<>(2, "10"),
                                        new TimeSeriesEntry<>(3, "30"));

        Collection<TimeSeriesEntry<String, Object>> r12 = t.entryRangeReversed(1, 3);
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
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
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
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
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
    public void testPollLastEntries() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(1, "10");
        t.add(2, "20", "200");
        t.add(3, "30");

        Collection<TimeSeriesEntry<String, String>> s = t.pollLastEntries(2);
        assertThat(s).containsExactly(new TimeSeriesEntry<>(2, "20", "200"),
                new TimeSeriesEntry<>(3, "30"));

        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void testPollFirstEntries() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(1, "10", "100");
        t.add(2, "20");
        t.add(3, "30");

        Collection<TimeSeriesEntry<String, String>> s = t.pollFirstEntries(2);
        assertThat(s).containsExactly(new TimeSeriesEntry<>(1, "10", "100"),
                                        new TimeSeriesEntry<>(2, "20"));

        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void testPoll() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
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
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
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

    @Test
    public void testPollFirstEntry() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(1, "10", "100");
        t.add(2, "20");
        t.add(3, "30");

        TimeSeriesEntry<String, String> e = t.pollFirstEntry();
        assertThat(e).isEqualTo(new TimeSeriesEntry<>(1, "10", "100"));

        assertThat(t.size()).isEqualTo(2);

        TimeSeriesEntry<String, String> ee = t.firstEntry();
        assertThat(ee).isEqualTo(new TimeSeriesEntry<>(2, "20"));
    }

    @Test
    public void testPollLastEntry() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(1, "10", "100");
        t.add(2, "20");
        t.add(3, "30");

        TimeSeriesEntry<String, String> e = t.pollLastEntry();
        assertThat(e).isEqualTo(new TimeSeriesEntry<>(3, "30"));

        assertThat(t.size()).isEqualTo(2);

        TimeSeriesEntry<String, String> ee = t.lastEntry();
        assertThat(ee).isEqualTo(new TimeSeriesEntry<>(2, "20"));
    }

    @Test
    public void testLastEntries() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(1, "10");
        t.add(2, "20", "200");
        t.add(3, "30");

        Collection<TimeSeriesEntry<String, String>> s = t.lastEntries(2);
        assertThat(s).containsExactly(new TimeSeriesEntry<>(2, "20", "200"),
                new TimeSeriesEntry<>(3, "30"));

        assertThat(t.size()).isEqualTo(3);
    }

    @Test
    public void testFirstEntries() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(1, "10", "100");
        t.add(2, "20");
        t.add(3, "30");

        Collection<TimeSeriesEntry<String, String>> s = t.firstEntries(2);
        assertThat(s).containsExactly(new TimeSeriesEntry<>(1, "10", "100"),
                new TimeSeriesEntry<>(2, "20"));

        assertThat(t.size()).isEqualTo(3);
    }

}
