package org.redisson;

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.api.RScript;
import org.junit.jupiter.api.Test;
import org.redisson.api.RTimeSeries;
import org.redisson.api.ts.TimeSeriesAddArgs;
import org.redisson.api.ts.TimeSeriesReadArgs;
import org.redisson.api.ts.TimeSeriesInfo;
import org.assertj.core.data.Offset;
import java.util.List;
import org.redisson.api.ts.TimeSeriesBucket;
import org.redisson.api.ts.TimeSeriesAggregationArgs;
import org.redisson.api.ts.TimeSeriesAggregation;
import org.redisson.api.TimeSeriesEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            ts.add(TimeSeriesAddArgs.entry(System.currentTimeMillis(), "my-value")
                                    .timeToLive(Duration.ofDays(60)));
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
        t.add(TimeSeriesAddArgs.entry(4, "40"));
        t.add(TimeSeriesAddArgs.entry(2, "20", "label2"));
        t.add(TimeSeriesAddArgs.entry(1, "10").timeToLive(Duration.ofSeconds(1)));

        Collection<TimeSeriesEntry<String, Object>> r11 = t.entryRange(1, 5);
        assertThat(r11).containsExactly(new TimeSeriesEntry<>(1,"10"),
                                        new TimeSeriesEntry<>(2, "20", "label2"),
                                        new TimeSeriesEntry<>(4, "40"));
    }

    @Test
    public void testCleanup() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "10").timeToLive(Duration.ofSeconds(1)));

        Thread.sleep(6000);

        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testIterator() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        for (int i = 0; i < 19; i++) {
            t.add(TimeSeriesAddArgs.entry(i, "" + i*10));
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
        t.add(TimeSeriesAddArgs.entry(1, "10"));
        t.add(TimeSeriesAddArgs.entry(2, "20"));
        t.add(TimeSeriesAddArgs.entry(3, "30"));
        t.add(TimeSeriesAddArgs.entry(4, "40"));

        assertThat(t.rangeReversed(1, 4, 2)).containsExactly("40", "30");
        assertThat(t.rangeReversed(1, 4, 0)).containsExactly("40", "30", "20", "10");

        RTimeSeries<String, Object> t2 = redisson.getTimeSeries("test2");
        t2.add(TimeSeriesAddArgs.entry(1, "10"));
        t2.add(TimeSeriesAddArgs.entry(2, "20"));
        t2.add(TimeSeriesAddArgs.entry(3, "30").timeToLive(Duration.ofSeconds(1)));
        t2.add(TimeSeriesAddArgs.entry(4, "40"));

        Thread.sleep(1200);

        assertThat(t2.rangeReversed(1, 4, 2)).containsExactly("40", "20");
    }

    @Test
    public void testRange() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "10"));
        t.add(TimeSeriesAddArgs.entry(2, "10"));
        t.add(TimeSeriesAddArgs.entry(3, "30"));
        t.add(TimeSeriesAddArgs.entry(4, "40"));

        assertThat(t.range(1, 4, 2)).containsExactly("10", "10");
        assertThat(t.range(1, 4, 0)).containsExactly("10", "10", "30", "40");

        RTimeSeries<String, Object> t2 = redisson.getTimeSeries("test2");
        t2.add(TimeSeriesAddArgs.entry(1, "10"));
        t2.add(TimeSeriesAddArgs.entry(2, "10").timeToLive(Duration.ofSeconds(1)));
        t2.add(TimeSeriesAddArgs.entry(3, "30"));
        t2.add(TimeSeriesAddArgs.entry(4, "40"));

        Thread.sleep(1200);

        assertThat(t2.range(1, 4, 2)).containsExactly("10", "30");
    }

    @Test
    public void testRemove() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "10"));
        t.add(TimeSeriesAddArgs.entry(2, "10"));
        t.add(TimeSeriesAddArgs.entry(3, "30"));
        t.add(TimeSeriesAddArgs.entry(4, "40"));

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
        t.add(TimeSeriesAddArgs.entry(1, "10"));
        t.add(TimeSeriesAddArgs.entry(2, "10"));
        t.add(TimeSeriesAddArgs.entry(3, "30"));
        t.add(TimeSeriesAddArgs.entry(4, "40"));
        assertThat(t.size()).isEqualTo(4);
        assertThat(t.get(3)).isEqualTo("30");
        assertThat(t.getEntry(3).getValue()).isEqualTo("30");

    }

    @Test
    public void testLabel() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "10"));
        t.add(TimeSeriesAddArgs.entry(2, "20", "label2"));
        t.add(TimeSeriesAddArgs.entry(3, "30", "label3"));

        TimeSeriesEntry<String, Object> ee = t.getEntry(2);
        assertThat(ee.getTimestamp()).isEqualTo(2);
        assertThat(ee.getValue()).isEqualTo("20");
        assertThat(ee.getLabel()).isEqualTo("label2");
    }

    @Test
    public void testGetAndRemoveEntry() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "10", "100"));
        t.add(TimeSeriesAddArgs.entry(2, "20"));
        t.add(TimeSeriesAddArgs.entry(3, "30", "300").timeToLive(Duration.ofSeconds(2)));
        t.add(TimeSeriesAddArgs.entry(4, "40"));

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
        t.add(TimeSeriesAddArgs.entry(1, "10", "100"));
        t.add(TimeSeriesAddArgs.entry(2, "20"));
        t.add(TimeSeriesAddArgs.entry(3, "30", "300").timeToLive(Duration.ofSeconds(2)));
        t.add(TimeSeriesAddArgs.entry(4, "40"));

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
        t.add(TimeSeriesAddArgs.entry(1, "10"));
        t.add(TimeSeriesAddArgs.entry(2, "10"));
        t.add(TimeSeriesAddArgs.entry(3, "30"));
        t.add(TimeSeriesAddArgs.entry(4, "40"));
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
        t.add(TimeSeriesAddArgs.entry(1, "10"));
        t.add(TimeSeriesAddArgs.entry(2, "10"));
        t.add(TimeSeriesAddArgs.entry(3, "30"));
        t.add(TimeSeriesAddArgs.entry(4, "40").timeToLive(Duration.ofSeconds(1)));
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
        t.add(TimeSeriesAddArgs.entry(1, "10").timeToLive(Duration.ofSeconds(1)));
        t.add(TimeSeriesAddArgs.entry(2, "10"));
        t.add(TimeSeriesAddArgs.entry(3, "30"));
        t.add(TimeSeriesAddArgs.entry(4, "40"));
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
        t.add(TimeSeriesAddArgs.entry(1, "10"));
        t.add(TimeSeriesAddArgs.entry(2, "20", "200"));
        t.add(TimeSeriesAddArgs.entry(3, "30"));

        Collection<TimeSeriesEntry<String, String>> s = t.pollLastEntries(2);
        assertThat(s).containsExactly(new TimeSeriesEntry<>(2, "20", "200"),
                new TimeSeriesEntry<>(3, "30"));

        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void testPollFirstEntries() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "10", "100"));
        t.add(TimeSeriesAddArgs.entry(2, "20"));
        t.add(TimeSeriesAddArgs.entry(3, "30"));

        Collection<TimeSeriesEntry<String, String>> s = t.pollFirstEntries(2);
        assertThat(s).containsExactly(new TimeSeriesEntry<>(1, "10", "100"),
                                        new TimeSeriesEntry<>(2, "20"));

        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void testPoll() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "10"));
        t.add(TimeSeriesAddArgs.entry(2, "20"));
        t.add(TimeSeriesAddArgs.entry(3, "30"));

        assertThat(t.pollFirst()).isEqualTo("10");
        assertThat(t.size()).isEqualTo(2);
        assertThat(t.pollLast()).isEqualTo("30");
        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void testPollList() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "10"));
        t.add(TimeSeriesAddArgs.entry(2, "20"));
        t.add(TimeSeriesAddArgs.entry(3, "30"));
        t.add(TimeSeriesAddArgs.entry(4, "40"));
        t.add(TimeSeriesAddArgs.entry(5, "50"));
        t.add(TimeSeriesAddArgs.entry(6, "60"));

        assertThat(t.pollFirst(2)).containsExactly("10", "20");
        assertThat(t.size()).isEqualTo(4);
        assertThat(t.pollLast(2)).containsExactly("50", "60");
        assertThat(t.size()).isEqualTo(2);
    }

    @Test
    public void testPollFirstEntry() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "10", "100"));
        t.add(TimeSeriesAddArgs.entry(2, "20"));
        t.add(TimeSeriesAddArgs.entry(3, "30"));

        TimeSeriesEntry<String, String> e = t.pollFirstEntry();
        assertThat(e).isEqualTo(new TimeSeriesEntry<>(1, "10", "100"));

        assertThat(t.size()).isEqualTo(2);

        TimeSeriesEntry<String, String> ee = t.firstEntry();
        assertThat(ee).isEqualTo(new TimeSeriesEntry<>(2, "20"));
    }

    @Test
    public void testPollLastEntry() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "10", "100"));
        t.add(TimeSeriesAddArgs.entry(2, "20"));
        t.add(TimeSeriesAddArgs.entry(3, "30"));

        TimeSeriesEntry<String, String> e = t.pollLastEntry();
        assertThat(e).isEqualTo(new TimeSeriesEntry<>(3, "30"));

        assertThat(t.size()).isEqualTo(2);

        TimeSeriesEntry<String, String> ee = t.lastEntry();
        assertThat(ee).isEqualTo(new TimeSeriesEntry<>(2, "20"));
    }

    @Test
    public void testLastEntries() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "10"));
        t.add(TimeSeriesAddArgs.entry(2, "20", "200"));
        t.add(TimeSeriesAddArgs.entry(3, "30"));

        Collection<TimeSeriesEntry<String, String>> s = t.lastEntries(2);
        assertThat(s).containsExactly(new TimeSeriesEntry<>(2, "20", "200"),
                new TimeSeriesEntry<>(3, "30"));

        assertThat(t.size()).isEqualTo(3);
    }

    @Test
    public void testFirstEntries() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "10", "100"));
        t.add(TimeSeriesAddArgs.entry(2, "20"));
        t.add(TimeSeriesAddArgs.entry(3, "30"));

        Collection<TimeSeriesEntry<String, String>> s = t.firstEntries(2);
        assertThat(s).containsExactly(new TimeSeriesEntry<>(1, "10", "100"),
                new TimeSeriesEntry<>(2, "20"));

        assertThat(t.size()).isEqualTo(3);
    }

    @Test
    public void testBackfilledOrder() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        // inserted out of timestamp order
        t.add(TimeSeriesAddArgs.entry(100, "A"));
        t.add(TimeSeriesAddArgs.entry(50, "B"));
        t.add(TimeSeriesAddArgs.entry(200, "C"));

        assertThat(t.firstTimestamp()).isEqualTo(50);
        assertThat(t.lastTimestamp()).isEqualTo(200);
        assertThat(t.first()).isEqualTo("B");
        assertThat(t.last()).isEqualTo("C");
        assertThat(t.first(2)).containsExactly("B", "A");
        assertThat(t.last(2)).containsExactly("A", "C");
        assertThat(t.firstEntry()).isEqualTo(new TimeSeriesEntry<>(50, "B"));
        assertThat(t.lastEntry()).isEqualTo(new TimeSeriesEntry<>(200, "C"));
    }

    @Test
    public void testReversedInsertionOrder() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(5, "e"));
        t.add(TimeSeriesAddArgs.entry(4, "d"));
        t.add(TimeSeriesAddArgs.entry(3, "c"));
        t.add(TimeSeriesAddArgs.entry(2, "b"));
        t.add(TimeSeriesAddArgs.entry(1, "a"));

        assertThat(t.firstTimestamp()).isEqualTo(1);
        assertThat(t.lastTimestamp()).isEqualTo(5);
        assertThat(t.first(3)).containsExactly("a", "b", "c");
        assertThat(t.last(3)).containsExactly("c", "d", "e");
        assertThat(t.firstEntries(2)).containsExactly(new TimeSeriesEntry<>(1, "a"),
                                                      new TimeSeriesEntry<>(2, "b"));
        assertThat(t.lastEntries(2)).containsExactly(new TimeSeriesEntry<>(4, "d"),
                                                     new TimeSeriesEntry<>(5, "e"));
    }

    @Test
    public void testPollReversedInsertionOrder() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(5, "e"));
        t.add(TimeSeriesAddArgs.entry(4, "d"));
        t.add(TimeSeriesAddArgs.entry(3, "c"));
        t.add(TimeSeriesAddArgs.entry(2, "b"));
        t.add(TimeSeriesAddArgs.entry(1, "a"));

        assertThat(t.pollFirst()).isEqualTo("a");
        assertThat(t.pollLast()).isEqualTo("e");
        assertThat(t.pollFirst(2)).containsExactly("b", "c");
        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void testTTLBackfilledOrder() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(300, "keep-late"));
        t.add(TimeSeriesAddArgs.entry(100, "expires").timeToLive(Duration.ofSeconds(1)));
        t.add(TimeSeriesAddArgs.entry(200, "keep-mid"));

        assertThat(t.firstTimestamp()).isEqualTo(100);

        Thread.sleep(1100);

        assertThat(t.size()).isEqualTo(2);
        assertThat(t.firstTimestamp()).isEqualTo(200);
        assertThat(t.first()).isEqualTo("keep-mid");
        assertThat(t.lastTimestamp()).isEqualTo(300);
        assertThat(t.last()).isEqualTo("keep-late");
    }

    @Test
    public void testDuplicateTimestampsAtBatchBoundary() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        // expired entries at the head force the index paging branch rather than the
        // single ranged read, and keeping live entries in the majority keeps it there
        for (int i = 0; i < 15; i++) {
            t.add(TimeSeriesAddArgs.entry(i, "expired" + i).timeToLive(Duration.ofMillis(300)));
        }
        Thread.sleep(500);
        for (int i = 0; i < 20; i++) {
            t.add(TimeSeriesAddArgs.entry(7000, "dup" + i));
        }
        for (int i = 0; i < 20; i++) {
            t.add(TimeSeriesAddArgs.entry(8000 + i, "tail" + i));
        }

        assertThat(t.size()).isEqualTo(40);
        assertThat(t.firstTimestamp()).isEqualTo(7000);
        assertThat(t.lastTimestamp()).isEqualTo(8019);
        // all 20 same-timestamp entries survive a batch boundary falling inside the group
        assertThat(t.first(20)).hasSize(20)
                .allMatch(v -> v.startsWith("dup"));
    }

    @Test
    public void testHeadTailOnEmptyAndFullyExpired() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        assertThat(t.first()).isNull();
        assertThat(t.last()).isNull();
        assertThat(t.firstTimestamp()).isNull();
        assertThat(t.firstEntry()).isNull();
        assertThat(t.pollFirst()).isNull();

        t.add(TimeSeriesAddArgs.entry(1, "a").timeToLive(Duration.ofMillis(800)));
        t.add(TimeSeriesAddArgs.entry(2, "b").timeToLive(Duration.ofMillis(800)));
        Thread.sleep(1000);

        assertThat(t.first()).isNull();
        assertThat(t.firstTimestamp()).isNull();
        assertThat(t.first(5)).isEmpty();
        assertThat(t.pollFirst()).isNull();
    }

    @Test
    public void testZeroAndNegativeCount() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "a"));
        t.add(TimeSeriesAddArgs.entry(2, "b"));
        t.add(TimeSeriesAddArgs.entry(3, "c"));

        assertThat(t.first(0)).isEmpty();
        assertThat(t.last(0)).isEmpty();
        assertThat(t.firstEntries(0)).isEmpty();
        assertThat(t.lastEntries(0)).isEmpty();
        assertThat(t.pollFirst(0)).isEmpty();
        assertThat(t.pollLast(0)).isEmpty();
        assertThat(t.size()).isEqualTo(3);

        assertThat(t.first(10)).containsExactly("a", "b", "c");
        assertThat(t.last(10)).containsExactly("a", "b", "c");

        assertThat(t.first(-1)).containsExactly("a", "b", "c");
        assertThat(t.last(-1)).containsExactly("a", "b", "c");
        assertThat(t.size()).isEqualTo(3);
    }

    @Test
    public void testPollSkipsExpiredWithoutConsumingThem() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        for (int i = 0; i < 5; i++) {
            t.add(TimeSeriesAddArgs.entry(i, "old" + i).timeToLive(Duration.ofMillis(500)));
        }
        Thread.sleep(700);
        t.add(TimeSeriesAddArgs.entry(100, "a"));
        t.add(TimeSeriesAddArgs.entry(101, "b"));

        assertThat(t.size()).isEqualTo(2);
        assertThat(t.pollFirst()).isEqualTo("a");
        assertThat(t.pollFirst(10)).containsExactly("b");
        assertThat(t.size()).isEqualTo(0);
    }

    @Test
    public void testLargeExpiredBacklog() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        for (int i = 0; i < 200; i++) {
            t.add(TimeSeriesAddArgs.entry(i, "expired" + i).timeToLive(Duration.ofMillis(300)));
        }
        Thread.sleep(500);
        t.add(TimeSeriesAddArgs.entry(9000, "z"));
        t.add(TimeSeriesAddArgs.entry(8000, "y"));
        t.add(TimeSeriesAddArgs.entry(8500, "x"));

        assertThat(t.size()).isEqualTo(3);
        assertThat(t.first()).isEqualTo("y");
        assertThat(t.last()).isEqualTo("z");
        assertThat(t.first(3)).containsExactly("y", "x", "z");
        assertThat(t.last(2)).containsExactly("x", "z");
        assertThat(t.firstTimestamp()).isEqualTo(8000);
        assertThat(t.lastTimestamp()).isEqualTo(9000);
        assertThat(t.firstEntries(3)).containsExactly(new TimeSeriesEntry<>(8000, "y"),
                                                      new TimeSeriesEntry<>(8500, "x"),
                                                      new TimeSeriesEntry<>(9000, "z"));
    }

    @Test
    public void testAddIfAbsent() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        assertThat(t.addIfAbsent(TimeSeriesAddArgs.entry(5, "a"))).isTrue();
        assertThat(t.addIfAbsent(TimeSeriesAddArgs.entry(5, "b"))).isFalse();
        assertThat(t.get(5)).isEqualTo("a");
        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void testAddIfAbsentTreatsExpiredAsAbsent() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        assertThat(t.addIfAbsent(TimeSeriesAddArgs.entry(5, "old")
                                                  .timeToLive(Duration.ofMillis(300)))).isTrue();
        Thread.sleep(500);

        assertThat(t.get(5)).isNull();
        assertThat(t.addIfAbsent(TimeSeriesAddArgs.entry(5, "new"))).isTrue();
        assertThat(t.get(5)).isEqualTo("new");
        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void testAddOrReplace() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        assertThat(t.addOrReplace(TimeSeriesAddArgs.entry(5, "a"))).isTrue();
        assertThat(t.addOrReplace(TimeSeriesAddArgs.entry(5, "b"))).isFalse();
        assertThat(t.get(5)).isEqualTo("b");
        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void testAddOrReplaceCollapsesDuplicates() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(5, "x"));
        t.add(TimeSeriesAddArgs.entry(5, "y"));
        t.add(TimeSeriesAddArgs.entry(5, "z"));
        assertThat(t.size()).isEqualTo(3);

        assertThat(t.addOrReplace(TimeSeriesAddArgs.entry(5, "last"))).isFalse();
        assertThat(t.size()).isEqualTo(1);
        assertThat(t.get(5)).isEqualTo("last");
    }

    @Test
    public void testAddIfAbsentAndAddOrReplaceInSequence() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        assertThat(t.addIfAbsent(TimeSeriesAddArgs.entry(1, "a"))).isTrue();
        assertThat(t.addIfAbsent(TimeSeriesAddArgs.entry(2, "b"))).isTrue();
        assertThat(t.addIfAbsent(TimeSeriesAddArgs.entry(3, "c"))).isTrue();

        assertThat(t.addIfAbsent(TimeSeriesAddArgs.entry(2, "ignored"))).isFalse();
        assertThat(t.addIfAbsent(TimeSeriesAddArgs.entry(4, "d"))).isTrue();
        assertThat(t.get(2)).isEqualTo("b");

        assertThat(t.addOrReplace(TimeSeriesAddArgs.entry(2, "replaced"))).isFalse();
        assertThat(t.addOrReplace(TimeSeriesAddArgs.entry(5, "new"))).isTrue();
        assertThat(t.get(2)).isEqualTo("replaced");
        assertThat(t.size()).isEqualTo(5);
    }

    @Test
    public void testAddArgsLabelAndTimeToLive() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.addOrReplace(TimeSeriesAddArgs.entry(1, "a", "lab1"));
        t.addOrReplace(TimeSeriesAddArgs.entry(2, "b"));
        t.addOrReplace(TimeSeriesAddArgs.<String, String>entry(3, "c", "lab3")
                .timeToLive(Duration.ofMillis(400)));

        assertThat(t.firstEntries(3)).containsExactly(new TimeSeriesEntry<>(1, "a", "lab1"),
                                                      new TimeSeriesEntry<>(2, "b"),
                                                      new TimeSeriesEntry<>(3, "c", "lab3"));

        Thread.sleep(600);
        assertThat(t.size()).isEqualTo(2);
    }

    @Test
    public void testExpiredDuplicateDoesNotMaskLiveSample() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(5, "old").timeToLive(Duration.ofMillis(300)));
        t.add(TimeSeriesAddArgs.entry(5, "new"));
        Thread.sleep(500);

        assertThat(t.size()).isEqualTo(1);
        assertThat(t.get(5)).isEqualTo("new");
        assertThat(t.getEntry(5)).isEqualTo(new TimeSeriesEntry<>(5, "new"));
        assertThat(t.getAndRemove(5)).isEqualTo("new");
        assertThat(t.get(5)).isNull();
    }

    @Test
    public void testDuplicatesKeepInsertionOrder() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(5, "a"));
        t.add(TimeSeriesAddArgs.entry(5, "b"));
        t.add(TimeSeriesAddArgs.entry(5, "c"));

        assertThat(t.size()).isEqualTo(3);
        assertThat(t.range(5, 5)).containsExactly("a", "b", "c");
        assertThat(t.get(5)).isEqualTo("a");

        assertThat(t.remove(5)).isTrue();
        assertThat(t.get(5)).isEqualTo("b");
        assertThat(t.range(5, 5)).containsExactly("b", "c");
    }

    @Test
    public void testSameValueAtDifferentTimestamps() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "temp42"));
        t.add(TimeSeriesAddArgs.entry(2, "temp42"));
        t.add(TimeSeriesAddArgs.entry(3, "temp42"));

        assertThat(t.size()).isEqualTo(3);
        assertThat(t.entryRange(1, 3)).containsExactly(new TimeSeriesEntry<>(1, "temp42"),
                                                       new TimeSeriesEntry<>(2, "temp42"),
                                                       new TimeSeriesEntry<>(3, "temp42"));
    }

    @Test
    public void testDuplicateOrderAcrossAddApis() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.addIfAbsent(TimeSeriesAddArgs.entry(9, "a"));
        t.add(TimeSeriesAddArgs.entry(9, "b"));
        t.add(TimeSeriesAddArgs.entry(9, "c"));

        assertThat(t.range(9, 9)).containsExactly("a", "b", "c");
        assertThat(t.get(9)).isEqualTo("a");
    }

    @Test
    public void testMixedLabelsAtSameTimestamp() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(5, "v", "label"));
        t.add(TimeSeriesAddArgs.entry(5, "v"));
        t.add(TimeSeriesAddArgs.entry(5, "v"));

        assertThat(t.size()).isEqualTo(3);
        assertThat(t.range(5, 5)).containsExactly("v", "v", "v");
    }

    @Test
    public void testMixedLabelsKeepInsertionOrder() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(5, "a", "label"));
        t.add(TimeSeriesAddArgs.entry(5, "b"));
        t.add(TimeSeriesAddArgs.entry(5, "c"));

        assertThat(t.range(5, 5)).containsExactly("a", "b", "c");
        assertThat(t.get(5)).isEqualTo("a");
        assertThat(t.entryRange(5, 5)).containsExactly(new TimeSeriesEntry<>(5, "a", "label"),
                                                       new TimeSeriesEntry<>(5, "b"),
                                                       new TimeSeriesEntry<>(5, "c"));
    }

    @Test
    public void testEmptyLabelDistinctFromNoLabel() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "v1", "lab"));
        t.add(TimeSeriesAddArgs.entry(2, "v2"));
        t.add(TimeSeriesAddArgs.entry(3, "v3", ""));

        assertThat(t.getEntry(1).getLabel()).isEqualTo("lab");
        assertThat(t.getEntry(2).getLabel()).isNull();
        assertThat(t.getEntry(3).getLabel()).isEmpty();
    }

    @Test
    public void testZeroTimeToLiveMeansNoTimeToLive() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        assertThat(t.addOrReplace(TimeSeriesAddArgs.entry(5, "v")
                                                   .timeToLive(Duration.ZERO))).isTrue();
        assertThat(t.get(5)).isEqualTo("v");

        assertThat(t.addOrReplace(TimeSeriesAddArgs.entry(5, "replacement")
                                                   .timeToLive(Duration.ZERO))).isFalse();
        Thread.sleep(200);
        assertThat(t.get(5)).isEqualTo("replacement");
        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void testATimeToLiveThatCannotBeHonouredMeansNoTimeToLive() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        // a negative interval would put the expiration in the past, and one this wide cannot be
        // converted to milliseconds at all; neither says anything about when to expire the entry
        assertThat(t.add(TimeSeriesAddArgs.entry(1, "negative")
                                          .timeToLive(Duration.ofDays(-30000)))).isTrue();
        assertThat(t.add(TimeSeriesAddArgs.entry(2, "unconvertible")
                                          .timeToLive(Duration.ofSeconds(Long.MAX_VALUE)))).isTrue();

        assertThat(t.range(0, 10)).containsExactly("negative", "unconvertible");
        assertThat(t.size()).isEqualTo(2);
    }

    @Test
    public void testMixedLabelsDoNotClobberTimeToLive() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(5, "keep"));
        t.add(TimeSeriesAddArgs.entry(5, "keep", "label"));
        t.add(TimeSeriesAddArgs.entry(5, "keep").timeToLive(Duration.ofMillis(400)));

        assertThat(t.size()).isEqualTo(3);
        Thread.sleep(600);
        assertThat(t.size()).isEqualTo(2);
    }

    @Test
    public void testNegativeAndZeroTimestamps() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(-5, "same"));
        t.add(TimeSeriesAddArgs.entry(-5, "same"));
        t.add(TimeSeriesAddArgs.entry(-5, "same"));
        t.add(TimeSeriesAddArgs.entry(0, "same"));
        t.add(TimeSeriesAddArgs.entry(0, "same"));

        assertThat(t.size()).isEqualTo(5);
        assertThat(t.range(-5, -5)).containsExactly("same", "same", "same");
        assertThat(t.range(0, 0)).containsExactly("same", "same");
        assertThat(t.firstTimestamp()).isEqualTo(-5);
        assertThat(t.lastTimestamp()).isEqualTo(0);
    }

    @Test
    public void testLargeTimestamps() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(100_000_000_000_000_000L, "w"));
        t.add(TimeSeriesAddArgs.entry(1_000_000_000_000_000_000L, "v"));

        assertThat(t.firstTimestamp()).isEqualTo(100_000_000_000_000_000L);
        assertThat(t.lastTimestamp()).isEqualTo(1_000_000_000_000_000_000L);
        assertThat(t.firstEntries(2)).containsExactly(
                new TimeSeriesEntry<>(100_000_000_000_000_000L, "w"),
                new TimeSeriesEntry<>(1_000_000_000_000_000_000L, "v"));
        assertThat(t.entryRange(0, Long.MAX_VALUE)).containsExactly(
                new TimeSeriesEntry<>(100_000_000_000_000_000L, "w"),
                new TimeSeriesEntry<>(1_000_000_000_000_000_000L, "v"));
    }

    @Test
    public void testRangeLimitAcrossDuplicateTimestamps() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        // an expired entry ahead of the duplicates forces the range to fetch a second page
        t.add(TimeSeriesAddArgs.entry(1, "expired").timeToLive(Duration.ofMillis(300)));
        for (int i = 0; i < 5; i++) {
            t.add(TimeSeriesAddArgs.entry(2, "dup" + i));
        }
        Thread.sleep(500);

        assertThat(t.size()).isEqualTo(5);
        assertThat(t.range(1, 2, 1)).containsExactly("dup0");
        assertThat(t.range(1, 2, 3)).containsExactly("dup0", "dup1", "dup2");
        assertThat(t.range(1, 2, 5)).containsExactly("dup0", "dup1", "dup2", "dup3", "dup4");
        assertThat(t.range(1, 2, 9)).containsExactly("dup0", "dup1", "dup2", "dup3", "dup4");
        assertThat(t.rangeReversed(1, 2, 3)).containsExactly("dup4", "dup3", "dup2");
        assertThat(t.entryRange(1, 2, 3)).containsExactly(new TimeSeriesEntry<>(2, "dup0"),
                                                          new TimeSeriesEntry<>(2, "dup1"),
                                                          new TimeSeriesEntry<>(2, "dup2"));
    }

    @Test
    public void testGetAllAndRemoveAll() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(5, "a", "L1"));
        t.add(TimeSeriesAddArgs.entry(5, "b"));
        t.add(TimeSeriesAddArgs.entry(5, "c", "L3"));
        t.add(TimeSeriesAddArgs.entry(6, "other"));

        assertThat(t.getAll(5)).containsExactly("a", "b", "c");
        assertThat(t.get(5)).isEqualTo("a");
        assertThat(t.getAllEntries(5)).containsExactly(new TimeSeriesEntry<>(5, "a", "L1"),
                                                       new TimeSeriesEntry<>(5, "b"),
                                                       new TimeSeriesEntry<>(5, "c", "L3"));
        assertThat(t.getAll(99)).isEmpty();
        assertThat(t.getAllEntries(99)).isEmpty();

        assertThat(t.removeAll(5)).isEqualTo(3);
        assertThat(t.getAll(5)).isEmpty();
        assertThat(t.getAll(6)).containsExactly("other");
        assertThat(t.size()).isEqualTo(1);
        assertThat(t.removeAll(99)).isZero();
    }

    @Test
    public void testGetAndRemoveAll() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(5, "a", "L1"));
        t.add(TimeSeriesAddArgs.entry(5, "b"));

        assertThat(t.getAndRemoveAllEntries(5))
                .containsExactly(new TimeSeriesEntry<>(5, "a", "L1"),
                                 new TimeSeriesEntry<>(5, "b"));
        assertThat(t.size()).isZero();

        t.add(TimeSeriesAddArgs.entry(7, "x"));
        t.add(TimeSeriesAddArgs.entry(7, "y"));
        assertThat(t.getAndRemoveAll(7)).containsExactly("x", "y");
        assertThat(t.getAll(7)).isEmpty();
        assertThat(t.getAndRemoveAll(99)).isEmpty();
    }

    @Test
    public void testPluralAccessorsSkipExpired() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(5, "gone1").timeToLive(Duration.ofMillis(300)));
        t.add(TimeSeriesAddArgs.entry(5, "keep1"));
        t.add(TimeSeriesAddArgs.entry(5, "gone2").timeToLive(Duration.ofMillis(300)));
        t.add(TimeSeriesAddArgs.entry(5, "keep2"));
        Thread.sleep(500);

        assertThat(t.getAll(5)).containsExactly("keep1", "keep2");
        assertThat(t.getAllEntries(5)).containsExactly(new TimeSeriesEntry<>(5, "keep1"),
                                                       new TimeSeriesEntry<>(5, "keep2"));
        assertThat(t.removeAll(5)).isEqualTo(2);
        assertThat(t.size()).isZero();
    }

    @Test
    public void testSingularAndPluralTogether() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(5, "a"));
        t.add(TimeSeriesAddArgs.entry(5, "b"));
        t.add(TimeSeriesAddArgs.entry(5, "c"));

        assertThat(t.remove(5)).isTrue();
        assertThat(t.getAll(5)).containsExactly("b", "c");
        assertThat(t.getAndRemove(5)).isEqualTo("b");
        assertThat(t.getAll(5)).containsExactly("c");
        assertThat(t.removeAll(5)).isEqualTo(1);
        assertThat(t.size()).isZero();
    }

    @Test
    public void testDuplicateOrderSurvivesRemoval() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(5, "a"));
        t.add(TimeSeriesAddArgs.entry(5, "b"));
        t.add(TimeSeriesAddArgs.entry(5, "c"));
        assertThat(t.removeAll(5)).isEqualTo(3);

        t.add(TimeSeriesAddArgs.entry(5, "d"));
        t.add(TimeSeriesAddArgs.entry(5, "e"));
        assertThat(t.range(5, 5)).containsExactly("d", "e");
        assertThat(t.get(5)).isEqualTo("d");
    }

    @Test
    public void testRetention() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        for (int ts = 1000; ts <= 1150; ts += 50) {
            t.addOrReplace(TimeSeriesAddArgs.entry(ts, "v" + ts)
                                            .retention(Duration.ofMillis(100)));
        }

        assertThat(t.range(0, 10000)).containsExactly("v1050", "v1100", "v1150");
        assertThat(t.firstTimestamp()).isEqualTo(1050);
        assertThat(t.size()).isEqualTo(3);
    }

    @Test
    public void testRetentionMeasuredAgainstHighestTimestamp() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        Duration retention = Duration.ofMillis(100);
        assertThat(t.addOrReplace(TimeSeriesAddArgs.entry(5000, "newest").retention(retention))).isTrue();
        // arrives last, but its own timestamp is outside the window, so it is not added
        assertThat(t.addOrReplace(TimeSeriesAddArgs.entry(4000, "way-behind").retention(retention))).isFalse();
        assertThat(t.addOrReplace(TimeSeriesAddArgs.entry(4950, "just-inside").retention(retention))).isTrue();

        assertThat(t.range(0, 10000)).containsExactly("just-inside", "newest");
        assertThat(t.size()).isEqualTo(2);
    }

    @Test
    public void testRetentionIsIndependentOfTimeToLive() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        Duration retention = Duration.ofMinutes(1);
        t.addOrReplace(TimeSeriesAddArgs.entry(5000, "kept").retention(retention));
        t.addOrReplace(TimeSeriesAddArgs.entry(5050, "expires")
                                        .retention(retention)
                                        .timeToLive(Duration.ofMillis(300)));
        assertThat(t.size()).isEqualTo(2);

        Thread.sleep(500);
        assertThat(t.range(0, 10000)).containsExactly("kept");
    }

    @Test
    public void testNoRetentionKeepsEverything() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.addOrReplace(TimeSeriesAddArgs.entry(1, "old"));
        t.addOrReplace(TimeSeriesAddArgs.entry(1_000_000, "new"));

        assertThat(t.size()).isEqualTo(2);
        assertThat(t.range(0, 10_000_000)).containsExactly("old", "new");
    }

    @Test
    public void testRetentionRejectsInsteadOfReporting() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.addOrReplace(TimeSeriesAddArgs.entry(10000, "newest"));
        Duration retention = Duration.ofSeconds(1);

        assertThat(t.addIfAbsent(TimeSeriesAddArgs.entry(1000, "old").retention(retention))).isFalse();
        assertThat(t.get(1000)).isNull();
        assertThat(t.addOrReplace(TimeSeriesAddArgs.entry(1000, "old").retention(retention))).isFalse();
        assertThat(t.size()).isEqualTo(1);

        // and over a run of calls, only the entries that survive are counted
        int created = 0;
        for (long timestamp : new long[]{1000, 9000, 10500}) {
            if (t.addOrReplace(TimeSeriesAddArgs.<String, Object>entry(timestamp, "v" + timestamp)
                    .retention(Duration.ofSeconds(2)))) {
                created++;
            }
        }
        assertThat(created).isEqualTo(2);
        assertThat(t.size()).isEqualTo(3);
        assertThat(t.getAll(1000)).isEmpty();
    }

    @Test
    public void testRetentionAnchorsOnALiveEntry() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.addOrReplace(TimeSeriesAddArgs.entry(1000, "live-a"));
        t.addOrReplace(TimeSeriesAddArgs.entry(1100, "live-b"));
        // far ahead of everything else, and gone before the next write
        t.addOrReplace(TimeSeriesAddArgs.entry(9000, "doomed")
                                        .timeToLive(Duration.ofMillis(1)));
        Thread.sleep(50);

        t.addOrReplace(TimeSeriesAddArgs.entry(1150, "live-c").retention(Duration.ofMillis(100)));

        // the window is anchored on 1150, not on the expired entry at 9000
        assertThat(t.range(0, 100000)).containsExactly("live-b", "live-c");
    }

    @Test
    public void testRetentionOfAnUnrepresentableLength() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.addOrReplace(TimeSeriesAddArgs.entry(1, "old"));
        // Duration.toMillis() overflows for this one
        t.addOrReplace(TimeSeriesAddArgs.entry(1_000_000, "new")
                                        .retention(Duration.ofSeconds(Long.MAX_VALUE)));

        assertThat(t.range(0, 10_000_000)).containsExactly("old", "new");
    }

    @Test
    public void testEachCallAppliesItsOwnRetention() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.addOrReplace(TimeSeriesAddArgs.<String, Object>entry(1000, "a")
                .retention(Duration.ofMillis(300)));
        // shorter than a millisecond, so it rounds to nothing and trims nothing
        t.addOrReplace(TimeSeriesAddArgs.<String, Object>entry(1100, "b")
                .retention(Duration.ofNanos(999_999)));
        assertThat(t.range(0, 10000)).containsExactly("a", "b");

        // this one is anchored on its own timestamp, so it takes the oldest with it
        t.addOrReplace(TimeSeriesAddArgs.<String, Object>entry(1200, "c")
                .retention(Duration.ofMillis(100)));
        assertThat(t.range(0, 10000)).containsExactly("b", "c");

        t.addOrReplace(TimeSeriesAddArgs.entry(1500, "d").retention(Duration.ofMillis(300)));
        assertThat(t.range(0, 10000)).containsExactly("c", "d");
    }

    @Test
    public void testRangeByLabel() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "a1", "cpu"));
        t.add(TimeSeriesAddArgs.entry(2, "b1", "mem"));
        t.add(TimeSeriesAddArgs.entry(3, "a2", "cpu"));
        t.add(TimeSeriesAddArgs.entry(4, "plain"));
        t.add(TimeSeriesAddArgs.entry(5, "b2", "mem"));

        assertThat(t.rangeByLabel(0, 10, "cpu")).containsExactly("a1", "a2");
        assertThat(t.rangeByLabel(0, 10, "mem")).containsExactly("b1", "b2");
        assertThat(t.rangeByLabel(0, 10, "nope")).isEmpty();
        assertThat(t.rangeReversedByLabel(0, 10, "cpu")).containsExactly("a2", "a1");
        // the timestamp range still applies
        assertThat(t.rangeByLabel(3, 10, "cpu")).containsExactly("a2");
        // and nothing changed for the unfiltered form
        assertThat(t.range(0, 10)).containsExactly("a1", "b1", "a2", "plain", "b2");
    }

    @Test
    public void testRangeByLabelSelectsUnlabelledWithNull() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "labelled", "cpu"));
        t.add(TimeSeriesAddArgs.entry(2, "plain"));

        assertThat(t.rangeByLabel(0, 10, null)).containsExactly("plain");
        assertThat(t.entryRangeByLabel(0, 10, null))
                .containsExactly(new TimeSeriesEntry<>(2, "plain"));
    }

    @Test
    public void testEntryRangeByLabel() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "a1", "cpu"));
        t.add(TimeSeriesAddArgs.entry(2, "b1", "mem"));
        t.add(TimeSeriesAddArgs.entry(3, "a2", "cpu"));

        assertThat(t.entryRangeByLabel(0, 10, "cpu")).containsExactly(
                new TimeSeriesEntry<>(1, "a1", "cpu"),
                new TimeSeriesEntry<>(3, "a2", "cpu"));
        assertThat(t.entryRangeReversedByLabel(0, 10, "cpu")).containsExactly(
                new TimeSeriesEntry<>(3, "a2", "cpu"),
                new TimeSeriesEntry<>(1, "a1", "cpu"));
    }

    @Test
    public void testRangeByLabelWithLimit() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        // only every tenth element matches, so a page of the requested size is mostly dropped
        for (int i = 1; i <= 40; i++) {
            t.add(TimeSeriesAddArgs.entry(i, "v" + i, i % 10 == 0 ? "keep" : "drop"));
        }

        assertThat(t.rangeByLabel(0, 100, "keep", 10)).containsExactly("v10", "v20", "v30", "v40");
        assertThat(t.rangeByLabel(0, 100, "keep", 2)).containsExactly("v10", "v20");
        assertThat(t.rangeByLabel(0, 100, "keep", 1)).containsExactly("v10");
        assertThat(t.rangeReversedByLabel(0, 100, "keep", 2)).containsExactly("v40", "v30");
        assertThat(t.entryRangeByLabel(0, 100, "keep", 2)).containsExactly(
                new TimeSeriesEntry<>(10, "v10", "keep"),
                new TimeSeriesEntry<>(20, "v20", "keep"));
    }

    @Test
    public void testRemoveRangeByLabel() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "a1", "cpu"));
        t.add(TimeSeriesAddArgs.entry(2, "b1", "mem"));
        t.add(TimeSeriesAddArgs.entry(3, "a2", "cpu"));
        t.add(TimeSeriesAddArgs.entry(4, "plain"));

        assertThat(t.removeRangeByLabel(0, 10, "cpu")).isEqualTo(2);
        assertThat(t.range(0, 10)).containsExactly("b1", "plain");
        assertThat(t.removeRangeByLabel(0, 10, "nope")).isZero();
        assertThat(t.removeRangeByLabel(0, 10, null)).isEqualTo(1);
        assertThat(t.range(0, 10)).containsExactly("b1");
    }

    @Test
    public void testLabels() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "a1", "cpu"));
        t.add(TimeSeriesAddArgs.entry(2, "b1", "mem"));
        t.add(TimeSeriesAddArgs.entry(3, "a2", "cpu"));
        t.add(TimeSeriesAddArgs.entry(4, "plain"));

        assertThat(t.labels()).containsExactlyInAnyOrder("cpu", "mem");
        assertThat(t.labels(2, 2)).containsExactlyInAnyOrder("mem");
        assertThat(t.labels(4, 4)).isEmpty();

        RTimeSeries<String, String> empty = redisson.getTimeSeries("empty");
        assertThat(empty.labels()).isEmpty();
    }

    @Test
    public void testLabelsAcrossPages() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        // more than the 500 element page the scan reads at a time
        for (int i = 1; i <= 1300; i++) {
            t.add(TimeSeriesAddArgs.entry(i, "v" + i, "L" + (i % 7)));
        }

        assertThat(t.labels()).hasSize(7);
        assertThat(t.labels()).contains("L0", "L1", "L2", "L3", "L4", "L5", "L6");
    }

    @Test
    public void testLabelFilterIgnoresExpiredEntries() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "live", "cpu"));
        t.add(TimeSeriesAddArgs.entry(2, "dying", "gone").timeToLive(Duration.ofMillis(300)));
        assertThat(t.rangeByLabel(0, 10, "cpu")).containsExactly("live");
        assertThat(t.labels()).containsExactlyInAnyOrder("cpu", "gone");

        Thread.sleep(500);
        assertThat(t.rangeByLabel(0, 10, "gone")).isEmpty();
        assertThat(t.labels()).containsExactlyInAnyOrder("cpu");
    }


    @Test
    public void testRangeByLabelWithNegativeLimit() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        for (int i = 1; i <= 5; i++) {
            t.add(TimeSeriesAddArgs.entry(i, "v" + i, "cpu"));
        }

        assertThat(t.rangeByLabel(0, 100, "cpu", -1)).isEmpty();
        assertThat(t.range(0, 100, -1)).isEmpty();
        assertThat(t.entryRange(0, 100, -1)).isEmpty();
        assertThat(t.entryRangeReversedByLabel(0, 100, "cpu", -1)).isEmpty();
    }

    @Test
    public void testTimestampsAtTheEdgeOfTheLongRange() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(Long.MAX_VALUE, "max", "cpu"));
        t.add(TimeSeriesAddArgs.entry(Long.MIN_VALUE, "min", "cpu"));

        assertThat(t.firstTimestamp()).isEqualTo(Long.MIN_VALUE);
        assertThat(t.lastTimestamp()).isEqualTo(Long.MAX_VALUE);
        assertThat(t.entryRange(Long.MIN_VALUE, Long.MAX_VALUE)).containsExactly(
                new TimeSeriesEntry<>(Long.MIN_VALUE, "min", "cpu"),
                new TimeSeriesEntry<>(Long.MAX_VALUE, "max", "cpu"));
        assertThat(t.rangeByLabel(Long.MIN_VALUE, Long.MAX_VALUE, "cpu")).containsExactly("min", "max");
    }

    @Test
    public void testLabelFilterUnderAnotherCodec() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test", StringCodec.INSTANCE);
        t.add(TimeSeriesAddArgs.entry(1, "a1", "cpu"));
        t.add(TimeSeriesAddArgs.entry(2, "b1", "mem"));
        t.add(TimeSeriesAddArgs.entry(3, "plain"));
        // an empty label is a label, and is not the same as carrying none
        t.add(TimeSeriesAddArgs.entry(4, "e1", ""));

        assertThat(t.rangeByLabel(0, 10, "cpu")).containsExactly("a1");
        assertThat(t.rangeByLabel(0, 10, "")).containsExactly("e1");
        assertThat(t.rangeByLabel(0, 10, null)).containsExactly("plain");
        assertThat(t.labels()).containsExactlyInAnyOrder("cpu", "mem", "");
        assertThat(t.removeRangeByLabel(0, 10, "")).isEqualTo(1);
        assertThat(t.range(0, 10)).containsExactly("a1", "b1", "plain");
    }

    @Test
    public void testLabelThatLooksLikeTheAbsentMarker() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test", StringCodec.INSTANCE);
        t.add(TimeSeriesAddArgs.entry(1, "zero", "0"));
        t.add(TimeSeriesAddArgs.entry(2, "plain"));

        assertThat(t.rangeByLabel(0, 10, "0")).containsExactly("zero");
        assertThat(t.rangeByLabel(0, 10, null)).containsExactly("plain");
        assertThat(t.labels()).containsExactlyInAnyOrder("0");
    }

    @Test
    public void testEntryRangeReversedByLabelWithLimit() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        for (int i = 1; i <= 20; i++) {
            t.add(TimeSeriesAddArgs.entry(i, "v" + i, i % 5 == 0 ? "keep" : "drop"));
        }

        assertThat(t.entryRangeReversedByLabel(0, 100, "keep", 2)).containsExactly(
                new TimeSeriesEntry<>(20, "v20", "keep"),
                new TimeSeriesEntry<>(15, "v15", "keep"));
        assertThat(t.entryRangeReversedByLabel(0, 100, "keep")).hasSize(4);
    }

    @Test
    public void testRemoveRangeByLabelSkipsExpiredMatches() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "live", "cpu"));
        t.add(TimeSeriesAddArgs.entry(2, "dying", "cpu").timeToLive(Duration.ofMillis(300)));
        Thread.sleep(500);

        // the expired one is still in the index but must not be counted as removed
        assertThat(t.removeRangeByLabel(0, 10, "cpu")).isEqualTo(1);
        assertThat(t.range(0, 10)).isEmpty();
    }

    @Test
    public void testLabelFilterReadsEntriesWrittenByAnOlderVersion() {
        String name = "legacy";
        RTimeSeries<String, String> t = redisson.getTimeSeries(name, StringCodec.INSTANCE);
        t.add(TimeSeriesAddArgs.entry(1, "current", "cpu"));
        // pre-4.x members: marker 3 carries the label raw, marker 2 carries none
        redisson.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE,
                "local val = struct.pack('BBc0Lc0Lc0', ARGV[4], string.len(ARGV[2]), ARGV[2], "
                        + "string.len(ARGV[3]), ARGV[3], string.len(ARGV[5]), ARGV[5]); "
                + "redis.call('zadd', KEYS[1], ARGV[1], val); "
                + "redis.call('zadd', KEYS[2], '4102444800000', val); return 1;",
                RScript.ReturnType.LONG,
                Arrays.asList(name, "redisson__ts_ttl:{" + name + "}"),
                "2", "old-id-a", "legacy-labelled", "3", "cpu");
        redisson.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE,
                "local val = struct.pack('BBc0Lc0Lc0', ARGV[4], string.len(ARGV[2]), ARGV[2], "
                        + "string.len(ARGV[3]), ARGV[3], string.len(ARGV[5]), ARGV[5]); "
                + "redis.call('zadd', KEYS[1], ARGV[1], val); "
                + "redis.call('zadd', KEYS[2], '4102444800000', val); return 1;",
                RScript.ReturnType.LONG,
                Arrays.asList(name, "redisson__ts_ttl:{" + name + "}"),
                "3", "old-id-b", "legacy-plain", "2", "");

        assertThat(t.range(0, 10)).containsExactly("current", "legacy-labelled", "legacy-plain");
        assertThat(t.rangeByLabel(0, 10, "cpu")).containsExactly("current", "legacy-labelled");
        assertThat(t.rangeByLabel(0, 10, null)).containsExactly("legacy-plain");
        assertThat(t.labels()).containsExactlyInAnyOrder("cpu");
        assertThat(t.entryRangeByLabel(0, 10, "cpu")).containsExactly(
                new TimeSeriesEntry<>(1, "current", "cpu"),
                new TimeSeriesEntry<>(2, "legacy-labelled", "cpu"));
        assertThat(t.removeRangeByLabel(0, 10, "cpu")).isEqualTo(2);
    }

    @Test
    public void testLimitLargerThanTheWindow() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        for (int i = 1; i <= 2000; i++) {
            t.add(TimeSeriesAddArgs.entry(i, "v" + i, "cpu"));
        }

        // the page is a slice of ranks, so a limit far larger than the window must not
        // make one page read the whole collection
        assertThat(t.range(5, 6, Integer.MAX_VALUE)).containsExactly("v5", "v6");
        assertThat(t.rangeByLabel(5, 6, "cpu", Integer.MAX_VALUE)).containsExactly("v5", "v6");
        assertThat(t.entryRange(5, 6, Integer.MAX_VALUE)).hasSize(2);
        assertThat(t.rangeReversed(5, 6, Integer.MAX_VALUE)).containsExactly("v6", "v5");
    }

    @Test
    public void testExpireAtCoversEveryKey() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "a", "cpu"));

        // every expireAt form used to fail with "Unsupported option"
        assertThat(t.expire(Instant.now().plusSeconds(600))).isTrue();
        assertThat(redisson.getKeys().remainTimeToLive("test")).isGreaterThan(0);
        assertThat(redisson.getKeys().remainTimeToLive("redisson__ts_ttl:{test}")).isGreaterThan(0);
        assertThat(redisson.getKeys().remainTimeToLive("redisson__ts_seq:{test}")).isGreaterThan(0);

        assertThat(t.expireIfSet(Instant.now().plusSeconds(900))).isTrue();
        assertThat(t.clearExpire()).isTrue();
        assertThat(redisson.getKeys().remainTimeToLive("redisson__ts_seq:{test}")).isEqualTo(-1);
        assertThat(t.range(0, 10)).containsExactly("a");
    }

    @Test
    public void testCopyOntoANameWhoseCounterSurvived() {
        RTimeSeries<String, String> src = redisson.getTimeSeries("src");
        src.add(TimeSeriesAddArgs.entry(1000, "A"));
        src.add(TimeSeriesAddArgs.entry(1001, "B"));
        src.add(TimeSeriesAddArgs.entry(1002, "C"));

        // emptying a collection leaves its counter behind until the eviction task runs
        RTimeSeries<String, String> dst = redisson.getTimeSeries("dst");
        dst.add(TimeSeriesAddArgs.entry(1, "junk"));
        dst.removeAll(1);
        assertThat(redisson.getKeys().countExists("redisson__ts_seq:{dst}")).isEqualTo(1);

        // the destination is not clean, so the copy must not report success
        assertThat(src.copy("dst")).isFalse();
        assertThat(dst.size()).isZero();

        assertThat(src.copyAndReplace("dst")).isTrue();
        assertThat(dst.range(0, 10000)).containsExactly("A", "B", "C");
        // and the ids that came with the data are not handed out again
        dst.add(TimeSeriesAddArgs.entry(9999, "B"));
        assertThat(dst.size()).isEqualTo(4);
        assertThat(dst.get(1001)).isEqualTo("B");
        assertThat(dst.get(9999)).isEqualTo("B");
    }

    @Test
    public void testCopyLeavesTheDestinationAloneWhenItFails() {
        RTimeSeries<String, String> src = redisson.getTimeSeries("src");
        src.add(TimeSeriesAddArgs.entry(1, "a"));
        RTimeSeries<String, String> dst = redisson.getTimeSeries("dst");
        dst.add(TimeSeriesAddArgs.entry(5, "existing"));

        assertThat(src.copy("dst")).isFalse();
        assertThat(dst.range(0, 100)).containsExactly("existing");
        // a failed copy must not leave half of the source behind either
        assertThat(redisson.getKeys().countExists("redisson__ts_seq:{dst}")).isEqualTo(1);
        assertThat(dst.get(1)).isNull();
    }

    @Test
    public void testRenamenxOfACollectionWrittenByAnOlderVersion() {
        String name = "old";
        RTimeSeries<String, String> t = redisson.getTimeSeries(name, StringCodec.INSTANCE);
        // pre-4.x data has the two indexes but no counter key
        for (int i = 1; i <= 3; i++) {
            redisson.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE,
                    "local val = struct.pack('BBc0Lc0Lc0', 2, string.len(ARGV[2]), ARGV[2], "
                            + "string.len(ARGV[3]), ARGV[3], 0, ''); "
                    + "redis.call('zadd', KEYS[1], ARGV[1], val); "
                    + "redis.call('zadd', KEYS[2], '4102444800000', val); return 1;",
                    RScript.ReturnType.LONG,
                    Arrays.asList(name, "redisson__ts_ttl:{" + name + "}"),
                    String.valueOf(i), "id-" + i, "v" + i);
        }
        assertThat(redisson.getKeys().countExists("redisson__ts_seq:{old}")).isZero();

        assertThat(t.renamenx("fresh")).isTrue();
        assertThat(t.range(0, 100)).containsExactly("v1", "v2", "v3");
        assertThat(redisson.getKeys().countExists("old")).isZero();
        // and the renamed collection keeps working
        t.add(TimeSeriesAddArgs.entry(4, "v4", "cpu"));
        assertThat(t.rangeByLabel(0, 100, "cpu")).containsExactly("v4");
    }

    @Test
    public void testHeadAndTailAgreeWithTheOtherReadsWhenTheIndexesDisagree() {
        String name = "test";
        RTimeSeries<String, String> t = redisson.getTimeSeries(name, StringCodec.INSTANCE);
        for (int i = 1; i <= 10; i++) {
            t.add(TimeSeriesAddArgs.entry(i, "gone" + i).timeToLive(Duration.ofMillis(1)));
        }
        t.add(TimeSeriesAddArgs.entry(100, "live"));

        // drop the live entry's expiration row and add an orphan one, so the two indexes
        // have the same cardinality but different contents
        redisson.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE,
                "local m = redis.call('zrangebyscore', KEYS[1], 100, 100)[1]; "
                + "redis.call('zrem', KEYS[2], m); "
                + "redis.call('zadd', KEYS[2], '4102444800000', 'orphan'); return 1;",
                RScript.ReturnType.LONG,
                Arrays.asList(name, "redisson__ts_ttl:{" + name + "}"));

        assertThat(t.range(0, 1000)).containsExactly("live");
        assertThat(t.first()).isEqualTo("live");
        assertThat(t.last()).isEqualTo("live");
        assertThat(t.firstTimestamp()).isEqualTo(100);
        assertThat(t.lastTimestamp()).isEqualTo(100);
        assertThat(t.firstEntry()).isEqualTo(new TimeSeriesEntry<>(100, "live"));
        assertThat(t.first(5)).containsExactly("live");
    }

    private RTimeSeries<String, String> numeric(String name) {
        return redisson.getTimeSeries(name, StringCodec.INSTANCE);
    }

    @Test
    public void testAggregate() {
        RTimeSeries<String, String> t = numeric("test");
        t.add(TimeSeriesAddArgs.entry(0, "1"));
        t.add(TimeSeriesAddArgs.entry(10, "3"));
        t.add(TimeSeriesAddArgs.entry(20, "5"));
        t.add(TimeSeriesAddArgs.entry(100, "10"));
        t.add(TimeSeriesAddArgs.entry(150, "20"));

        List<TimeSeriesBucket> buckets = new ArrayList<>(t.aggregate(
                TimeSeriesAggregationArgs.<String>between(0, 200)
                        .bucket(Duration.ofMillis(100))
                        .count().sum().avg().min().max().valueRange().first().last()));

        assertThat(buckets).hasSize(2);

        TimeSeriesBucket first = buckets.get(0);
        assertThat(first.getTimestamp()).isZero();
        assertThat(first.getCount()).isEqualTo(3.0);
        assertThat(first.getSum()).isEqualTo(9.0);
        assertThat(first.getAvg()).isEqualTo(3.0);
        assertThat(first.getMin()).isEqualTo(1.0);
        assertThat(first.getMax()).isEqualTo(5.0);
        assertThat(first.getValueRange()).isEqualTo(4.0);
        assertThat(first.getFirst()).isEqualTo(1.0);
        assertThat(first.getLast()).isEqualTo(5.0);

        TimeSeriesBucket second = buckets.get(1);
        assertThat(second.getTimestamp()).isEqualTo(100);
        assertThat(second.getCount()).isEqualTo(2.0);
        assertThat(second.getAvg()).isEqualTo(15.0);
    }

    @Test
    public void testAggregateReportsOnlyWhatWasAskedFor() {
        RTimeSeries<String, String> t = numeric("test");
        t.add(TimeSeriesAddArgs.entry(1, "4"));

        TimeSeriesBucket bucket = t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 10)
                .bucket(Duration.ofMillis(10)).avg()).iterator().next();

        assertThat(bucket.getAvg()).isEqualTo(4.0);
        assertThat(bucket.getMax()).isNull();
        assertThat(bucket.get(TimeSeriesAggregation.SUM)).isNull();
        assertThat(bucket.getValues()).containsOnlyKeys(TimeSeriesAggregation.AVG);
    }

    @Test
    public void testAggregateSkipsEmptyBuckets() {
        RTimeSeries<String, String> t = numeric("test");
        t.add(TimeSeriesAddArgs.entry(0, "1"));
        t.add(TimeSeriesAddArgs.entry(5000, "2"));

        List<TimeSeriesBucket> buckets = new ArrayList<>(t.aggregate(
                TimeSeriesAggregationArgs.<String>between(0, 10000)
                        .bucket(Duration.ofMillis(100)).count()));

        assertThat(buckets).hasSize(2);
        assertThat(buckets.get(0).getTimestamp()).isZero();
        assertThat(buckets.get(1).getTimestamp()).isEqualTo(5000);
    }

    @Test
    public void testAggregateAlignment() {
        RTimeSeries<String, String> t = numeric("test");
        for (int ts = 0; ts < 100; ts += 10) {
            t.add(TimeSeriesAddArgs.entry(ts, Integer.toString(ts)));
        }

        assertThat(t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(50)).count()))
                .extracting(TimeSeriesBucket::getTimestamp)
                .containsExactly(0L, 50L);

        // shifting the alignment moves the boundaries with it
        assertThat(t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(50)).alignTo(25).count()))
                .extracting(TimeSeriesBucket::getTimestamp)
                .containsExactly(-25L, 25L, 75L);
    }

    @Test
    public void testAggregateNegativeTimestamps() {
        RTimeSeries<String, String> t = numeric("test");
        t.add(TimeSeriesAddArgs.entry(-30, "1"));
        t.add(TimeSeriesAddArgs.entry(-10, "2"));
        t.add(TimeSeriesAddArgs.entry(10, "3"));

        assertThat(t.aggregate(TimeSeriesAggregationArgs.<String>between(-100, 100)
                .bucket(Duration.ofMillis(20)).count()))
                .extracting(TimeSeriesBucket::getTimestamp)
                .containsExactly(-40L, -20L, 0L);
    }

    @Test
    public void testAggregateFilterByValue() {
        RTimeSeries<String, String> t = numeric("test");
        for (int i = 0; i < 10; i++) {
            t.add(TimeSeriesAddArgs.entry(i, Integer.toString(i)));
        }

        TimeSeriesBucket bucket = t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(100))
                .filterByValue(3, 6)
                .count().min().max()).iterator().next();

        assertThat(bucket.getCount()).isEqualTo(4.0);
        assertThat(bucket.getMin()).isEqualTo(3.0);
        assertThat(bucket.getMax()).isEqualTo(6.0);

        // a filter that matches nothing leaves no bucket behind
        assertThat(t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(100)).filterByValue(100, 200).count())).isEmpty();
    }

    @Test
    public void testAggregateFilterByLabel() {
        RTimeSeries<String, String> t = numeric("test");
        t.add(TimeSeriesAddArgs.entry(1, "10", "cpu"));
        t.add(TimeSeriesAddArgs.entry(2, "20", "mem"));
        t.add(TimeSeriesAddArgs.entry(3, "30", "cpu"));
        t.add(TimeSeriesAddArgs.entry(4, "40"));

        assertThat(t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(100)).label("cpu").sum().count())
                .iterator().next().getSum()).isEqualTo(40.0);

        assertThat(t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(100)).label(null).sum())
                .iterator().next().getSum()).isEqualTo(40.0);

        assertThat(t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(100)).label("nope").count())).isEmpty();
    }

    @Test
    public void testAggregateVariance() {
        RTimeSeries<String, String> t = numeric("test");
        for (String v : new String[]{"2", "4", "4", "4", "5", "5", "7", "9"}) {
            t.add(TimeSeriesAddArgs.entry(t.size(), v));
        }

        TimeSeriesBucket b = t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(100))
                .stdDevPopulation().stdDevSample().variancePopulation().varianceSample())
                .iterator().next();

        assertThat(b.getStdDevPopulation()).isEqualTo(2.0);
        assertThat(b.getVariancePopulation()).isEqualTo(4.0);
        assertThat(b.getVarianceSample()).isCloseTo(32.0 / 7.0, Offset.offset(1e-12));
        assertThat(b.getStdDevSample()).isCloseTo(Math.sqrt(32.0 / 7.0), Offset.offset(1e-12));
    }

    @Test
    public void testAggregateSampleVarianceOfOneValueIsUndefined() {
        RTimeSeries<String, String> t = numeric("test");
        t.add(TimeSeriesAddArgs.entry(1, "5"));

        TimeSeriesBucket b = t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(100))
                .stdDevPopulation().stdDevSample().variancePopulation().varianceSample())
                .iterator().next();

        assertThat(b.getStdDevPopulation()).isZero();
        assertThat(b.getVariancePopulation()).isZero();
        assertThat(b.getStdDevSample()).isNull();
        assertThat(b.getVarianceSample()).isNull();
    }

    @Test
    public void testAggregateKeepsPrecisionOnLargeCloseValues() {
        RTimeSeries<String, String> t = numeric("test");
        // a sum of squares would lose these entirely
        t.add(TimeSeriesAddArgs.entry(1, "1000000000.1"));
        t.add(TimeSeriesAddArgs.entry(2, "1000000000.2"));
        t.add(TimeSeriesAddArgs.entry(3, "1000000000.3"));

        TimeSeriesBucket b = t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(100)).avg().variancePopulation())
                .iterator().next();

        assertThat(b.getAvg()).isCloseTo(1000000000.2, Offset.offset(1e-6));
        assertThat(b.getVariancePopulation()).isCloseTo(0.0066666, Offset.offset(1e-6));
    }

    @Test
    public void testAggregateIgnoresExpiredEntries() throws InterruptedException {
        RTimeSeries<String, String> t = numeric("test");
        t.add(TimeSeriesAddArgs.entry(1, "10"));
        t.add(TimeSeriesAddArgs.entry(2, "20").timeToLive(Duration.ofMillis(300)));
        assertThat(t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(100)).count()).iterator().next().getCount())
                .isEqualTo(2.0);

        Thread.sleep(500);
        assertThat(t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(100)).count().sum()).iterator().next().getSum())
                .isEqualTo(10.0);
    }

    @Test
    public void testAggregateRejectsABinaryCodec() {
        // the default codec writes a number as binary, which the script cannot read
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "10"));

        assertThatThrownBy(() -> t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(100)).avg()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("codec")
                .hasMessageContaining("StringCodec");
    }

    @Test
    public void testAggregateRejectsAValueThatIsNotAFiniteNumber() {
        for (String bad : new String[]{"not a number", "inf", "-inf", "nan", "infinity"}) {
            RTimeSeries<String, String> t = numeric("test" + bad.hashCode());
            t.add(TimeSeriesAddArgs.entry(1, "10"));
            t.add(TimeSeriesAddArgs.entry(2, bad));

            // tonumber() reads inf and nan, which would quietly poison the whole bucket
            assertThatThrownBy(() -> t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                    .bucket(Duration.ofMillis(100)).avg()))
                    .hasMessageContaining("is not a finite number");
        }
    }

    @Test
    public void testAggregateReportsAnOverflowJavaCanRead() {
        RTimeSeries<String, String> t = numeric("test");
        t.add(TimeSeriesAddArgs.entry(1, "1e308"));
        t.add(TimeSeriesAddArgs.entry(2, "1e308"));

        TimeSeriesBucket b = t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(100)).sum().avg()).iterator().next();

        assertThat(b.getSum()).isEqualTo(Double.POSITIVE_INFINITY);
        // the sum overflowed but the running mean did not
        assertThat(b.getAvg()).isEqualTo(1e308);
    }

    @Test
    public void testAggregateAvgSurvivesASumThatCannotBeRepresented() {
        RTimeSeries<String, String> t = numeric("test");
        for (int i = 0; i < 20; i++) {
            t.add(TimeSeriesAddArgs.entry(i, "1e307"));
        }

        assertThat(t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(100)).avg()).iterator().next().getAvg())
                .isEqualTo(1e307);
    }

    @Test
    public void testAggregateAtTheBottomOfTheLongRange() {
        RTimeSeries<String, String> t = numeric("test");
        t.add(TimeSeriesAddArgs.entry(Long.MIN_VALUE, "1"));

        assertThat(t.aggregate(TimeSeriesAggregationArgs.<String>between(Long.MIN_VALUE, 0)
                .bucket(Duration.ofHours(1)).count()))
                .extracting(TimeSeriesBucket::getTimestamp)
                .containsExactly(Long.MIN_VALUE);
    }

    @Test
    public void testAggregateBucketMustSurviveConversionToMillis() {
        RTimeSeries<String, String> t = numeric("test");
        t.add(TimeSeriesAddArgs.entry(1, "1"));

        // neither zero nor negative, but it truncates to zero milliseconds
        assertThatThrownBy(() -> t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofNanos(999_999)).count()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("millisecond");

        // and one too large to convert is simply a very wide bucket
        assertThat(t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofSeconds(Long.MAX_VALUE)).count())).hasSize(1);
    }

    @Test
    public void testAggregateUnderEveryCodecTheMessageRecommends() {
        // the entries are written as text whatever the reading collection's codec is
        RTimeSeries<String, String> writer = redisson.getTimeSeries("codecs", StringCodec.INSTANCE);
        writer.add(TimeSeriesAddArgs.entry(0, "1.5"));
        writer.add(TimeSeriesAddArgs.entry(1, "2.5"));

        for (Codec codec : new Codec[]{StringCodec.INSTANCE, DoubleCodec.INSTANCE,
                                       LongCodec.INSTANCE, IntegerCodec.INSTANCE}) {
            RTimeSeries<Object, Object> t = redisson.getTimeSeries("codecs", codec);
            TimeSeriesBucket b = t.aggregate(TimeSeriesAggregationArgs.between(0, 10)
                    .bucket(Duration.ofSeconds(1)).count().avg()).iterator().next();
            assertThat(b.getCount()).as(codec.getClass().getSimpleName()).isEqualTo(2.0);
            assertThat(b.getAvg()).as(codec.getClass().getSimpleName()).isEqualTo(2.0);
        }
    }

    @Test
    public void testAggregateRejectsANullAggregation() {
        assertThatThrownBy(() -> TimeSeriesAggregationArgs.<String>between(0, 100)
                .aggregations(TimeSeriesAggregation.AVG, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testAggregateArgumentValidation() {
        RTimeSeries<String, String> t = numeric("test");
        t.add(TimeSeriesAddArgs.entry(1, "1"));

        assertThatThrownBy(() -> t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100).avg()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket");
        assertThatThrownBy(() -> t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ZERO).avg()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(10))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregation");
    }

    @Test
    public void testAggregateEmptyRangeAndEmptyCollection() {
        RTimeSeries<String, String> t = numeric("test");
        assertThat(t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(10)).avg())).isEmpty();

        t.add(TimeSeriesAddArgs.entry(500, "1"));
        assertThat(t.aggregate(TimeSeriesAggregationArgs.<String>between(0, 100)
                .bucket(Duration.ofMillis(10)).avg())).isEmpty();
    }

    @Test
    public void testReadTail() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(10, "a"));
        t.add(TimeSeriesAddArgs.entry(20, "b"));
        t.add(TimeSeriesAddArgs.entry(30, "c"));

        assertThat(t.readTail(TimeSeriesReadArgs.after(10))).containsExactly(
                new TimeSeriesEntry<>(20, "b"),
                new TimeSeriesEntry<>(30, "c"));
        // the boundary is exclusive
        assertThat(t.readTail(TimeSeriesReadArgs.after(30))).isEmpty();
        assertThat(t.readTail(TimeSeriesReadArgs.after(Long.MIN_VALUE))).hasSize(3);
    }

    @Test
    public void testReadTailAdvancingACursor() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        long cursor = Long.MIN_VALUE;
        List<String> seen = new ArrayList<>();
        for (int round = 0; round < 5; round++) {
            t.add(TimeSeriesAddArgs.entry(round * 10, "v" + round));
            for (TimeSeriesEntry<String, String> e : t.readTail(TimeSeriesReadArgs.after(cursor))) {
                seen.add(e.getValue());
                cursor = e.getTimestamp();
            }
            // nothing new the second time round
            assertThat(t.readTail(TimeSeriesReadArgs.after(cursor))).isEmpty();
        }
        assertThat(seen).containsExactly("v0", "v1", "v2", "v3", "v4");
    }

    @Test
    public void testReadTailCountAndLabel() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        for (int i = 1; i <= 10; i++) {
            t.add(TimeSeriesAddArgs.entry(i, "v" + i, i % 2 == 0 ? "even" : "odd"));
        }

        assertThat(t.readTail(TimeSeriesReadArgs.after(0).count(3)))
                .extracting(TimeSeriesEntry::getValue).containsExactly("v1", "v2", "v3");
        assertThat(t.readTail(TimeSeriesReadArgs.<String>after(0).label("even").count(2)))
                .extracting(TimeSeriesEntry::getValue).containsExactly("v2", "v4");
        assertThat(t.readTail(TimeSeriesReadArgs.<String>after(0).label(null))).isEmpty();
        // zero is no limit, and a negative count reports nothing, as elsewhere
        assertThat(t.readTail(TimeSeriesReadArgs.after(0).count(0))).hasSize(10);
        assertThat(t.readTail(TimeSeriesReadArgs.after(0).count(-1))).isEmpty();
    }

    @Test
    public void testReadTailAtTheEdgeOfTheLongRange() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(Long.MAX_VALUE, "max"));
        t.add(TimeSeriesAddArgs.entry(0, "zero"));

        assertThat(t.readTail(TimeSeriesReadArgs.after(Long.MAX_VALUE))).isEmpty();
        assertThat(t.readTail(TimeSeriesReadArgs.after(0)))
                .extracting(TimeSeriesEntry::getValue).containsExactly("max");
    }

    @Test
    public void testReadTailIgnoresExpiredEntries() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(10, "kept"));
        t.add(TimeSeriesAddArgs.entry(20, "dying").timeToLive(Duration.ofMillis(300)));
        assertThat(t.readTail(TimeSeriesReadArgs.after(0))).hasSize(2);

        Thread.sleep(500);
        assertThat(t.readTail(TimeSeriesReadArgs.after(0)))
                .extracting(TimeSeriesEntry::getValue).containsExactly("kept");
    }

    @Test
    public void testInfo() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        TimeSeriesInfo empty = t.info();
        assertThat(empty.getSize()).isZero();
        assertThat(empty.getTotalEntries()).isZero();
        assertThat(empty.getFirstTimestamp()).isNull();
        assertThat(empty.getLastTimestamp()).isNull();
        assertThat(empty.getEntriesIssued()).isZero();
        assertThat(empty.getTimeToLive()).isEqualTo(-2);

        t.add(TimeSeriesAddArgs.entry(30, "c"));
        t.add(TimeSeriesAddArgs.entry(10, "a"));
        t.add(TimeSeriesAddArgs.entry(20, "b"));

        TimeSeriesInfo info = t.info();
        assertThat(info.getSize()).isEqualTo(3);
        assertThat(info.getTotalEntries()).isEqualTo(3);
        assertThat(info.getFirstTimestamp()).isEqualTo(10);
        assertThat(info.getLastTimestamp()).isEqualTo(30);
        assertThat(info.getMemoryUsage()).isPositive();
        assertThat(info.getTimeToLive()).isEqualTo(-1);
        assertThat(info.getEntriesIssued()).isEqualTo(3);

        // and it agrees with the methods that report the same things one at a time
        assertThat(info.getSize()).isEqualTo(t.size());
        assertThat(info.getFirstTimestamp()).isEqualTo(t.firstTimestamp());
        assertThat(info.getLastTimestamp()).isEqualTo(t.lastTimestamp());
    }

    @Test
    public void testInfoCountsWhatEvictionHasNotReclaimed() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(10, "kept"));
        t.add(TimeSeriesAddArgs.entry(20, "dying").timeToLive(Duration.ofMillis(300)));
        t.add(TimeSeriesAddArgs.entry(30, "also dying").timeToLive(Duration.ofMillis(300)));
        Thread.sleep(500);

        TimeSeriesInfo info = t.info();
        assertThat(info.getSize()).isEqualTo(1);
        assertThat(info.getTotalEntries()).isEqualTo(3);
        // the expired ones are still held, but they are not the first or the last
        assertThat(info.getFirstTimestamp()).isEqualTo(10);
        assertThat(info.getLastTimestamp()).isEqualTo(10);
    }

    @Test
    public void testInfoReportsTheCollectionTimeToLive() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "a"));
        assertThat(t.info().getTimeToLive()).isEqualTo(-1);

        t.expire(Duration.ofMinutes(10));
        assertThat(t.info().getTimeToLive()).isGreaterThan(0);

        t.clearExpire();
        assertThat(t.info().getTimeToLive()).isEqualTo(-1);
    }

    @Test
    public void testInfoAtTheEdgeOfTheLongRange() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(Long.MIN_VALUE, "min"));
        t.add(TimeSeriesAddArgs.entry(Long.MAX_VALUE, "max"));

        TimeSeriesInfo info = t.info();
        assertThat(info.getFirstTimestamp()).isEqualTo(Long.MIN_VALUE);
        assertThat(info.getLastTimestamp()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    public void testInfoWithATimeToLivePastFourteenDigits() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "a"));
        t.expire(Duration.ofDays(365L * 4000));

        // Lua's tostring renders with %.14g, which would have come back as 1.26144e+14
        assertThat(t.info().getTimeToLive()).isGreaterThan(100_000_000_000_000L);
    }

    @Test
    public void testReadTailAboveTwoToTheFiftyThree() {
        // a long does not survive the conversion to a double up here, so the exclusive
        // bound cannot simply be the next double above the cursor
        for (long base : new long[]{1L << 53, (1L << 53) + 1000, 1L << 60}) {
            RTimeSeries<String, String> t = redisson.getTimeSeries("test" + base);
            List<Long> stored = new ArrayList<>();
            for (long d = 0; d <= 16; d += 2) {
                t.add(TimeSeriesAddArgs.entry(base + d, "v" + d));
            }
            for (TimeSeriesEntry<String, String> e : t.entryRange(Long.MIN_VALUE, Long.MAX_VALUE)) {
                stored.add(e.getTimestamp());
            }

            for (long cursor = base - 1; cursor <= base + 17; cursor++) {
                List<Long> expected = new ArrayList<>();
                for (long timestamp : stored) {
                    if (timestamp > cursor) {
                        expected.add(timestamp);
                    }
                }
                assertThat(t.readTail(TimeSeriesReadArgs.after(cursor)))
                        .as("base " + base + " cursor " + cursor)
                        .extracting(TimeSeriesEntry::getTimestamp)
                        .containsExactlyElementsOf(expected);
            }
        }
    }

    @Test
    public void testInfoOfACollectionThatHasEntirelyExpired() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        Map<Long, String> block = new HashMap<>();
        for (long i = 0; i < 50; i++) {
            block.put(i, "v" + i);
        }
        t.addAll(block, Duration.ofMillis(1));
        Thread.sleep(200);

        TimeSeriesInfo info = t.info();
        assertThat(info.getSize()).isZero();
        assertThat(info.getTotalEntries()).isEqualTo(50);
        assertThat(info.getFirstTimestamp()).isNull();
        assertThat(info.getLastTimestamp()).isNull();
        assertThat(info.getFirstTimestamp()).isEqualTo(t.firstTimestamp());
        assertThat(info.getLastTimestamp()).isEqualTo(t.lastTimestamp());
    }

    @Test
    public void testReadTailNeverEndsInsideATimestamp() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        for (int i = 0; i < 150; i++) {
            t.add(TimeSeriesAddArgs.entry(1000, "v" + i));
        }
        t.add(TimeSeriesAddArgs.entry(1001, "next"));

        // the count is reached inside the run at 1000, so the whole run comes back
        assertThat(t.readTail(TimeSeriesReadArgs.after(0).count(100))).hasSize(150);

        // and the loop the javadoc recommends therefore delivers everything
        long cursor = Long.MIN_VALUE;
        List<String> seen = new ArrayList<>();
        while (true) {
            Collection<TimeSeriesEntry<String, String>> batch =
                    t.readTail(TimeSeriesReadArgs.after(cursor).count(100));
            if (batch.isEmpty()) {
                break;
            }
            for (TimeSeriesEntry<String, String> e : batch) {
                seen.add(e.getValue());
                cursor = e.getTimestamp();
            }
        }
        assertThat(seen).hasSize(151);
        assertThat(seen).endsWith("next");
    }

    @Test
    public void testSizeCountsAnEntryWithANegativeExpirationAsExpired() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        // addAll() takes a negative interval literally, so the expiration lands in the past
        t.addAll(Collections.singletonMap(7L, "gone"), Duration.ofDays(-30000));
        t.add(TimeSeriesAddArgs.entry(8, "live"));

        assertThat(t.range(0, 100)).containsExactly("live");
        assertThat(t.size()).isEqualTo(1);

        TimeSeriesInfo info = t.info();
        assertThat(info.getSize()).isEqualTo(1);
        assertThat(info.getFirstTimestamp()).isEqualTo(8);
        assertThat(info.getLastTimestamp()).isEqualTo(8);
    }

    @Test
    public void testRenameLeavesTheDestinationMirroringTheSource() {
        RTimeSeries<String, String> src = redisson.getTimeSeries("src");
        src.add(TimeSeriesAddArgs.entry(1, "a"));
        src.removeAll(1);
        // emptied, but its counter key outlives the data

        RTimeSeries<String, String> dst = redisson.getTimeSeries("dst");
        for (int i = 1; i <= 5; i++) {
            dst.add(TimeSeriesAddArgs.entry(i, "d" + i));
        }

        src.rename("dst");
        RTimeSeries<String, String> renamed = redisson.getTimeSeries("dst");
        // the destination holds what the source held, which is nothing
        assertThat(renamed.size()).isZero();
        assertThat(renamed.range(0, 100)).isEmpty();
        assertThat(redisson.getKeys().countExists("dst")).isZero();
        assertThat(redisson.getKeys().countExists("redisson__ts_ttl:{dst}")).isZero();

        // and the ids the source's counter goes on issuing meet no data to collide with
        renamed.add(TimeSeriesAddArgs.entry(3, "fresh"));
        renamed.add(TimeSeriesAddArgs.entry(9, "later"));
        assertThat(renamed.range(0, 100)).containsExactly("fresh", "later");
        assertThat(renamed.size()).isEqualTo(2);
    }

    @Test
    public void testAddIfLess() {
        RTimeSeries<String, String> t = numeric("test");
        assertThat(t.addIfLess(TimeSeriesAddArgs.entry(5, "10"))).isTrue();
        assertThat(t.addIfLess(TimeSeriesAddArgs.entry(5, "4"))).isTrue();
        assertThat(t.get(5)).isEqualTo("4");
        assertThat(t.addIfLess(TimeSeriesAddArgs.entry(5, "9"))).isFalse();
        assertThat(t.addIfLess(TimeSeriesAddArgs.entry(5, "4"))).isFalse();
        assertThat(t.get(5)).isEqualTo("4");
        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void testAddIfGreater() {
        RTimeSeries<String, String> t = numeric("test");
        assertThat(t.addIfGreater(TimeSeriesAddArgs.entry(5, "10"))).isTrue();
        assertThat(t.addIfGreater(TimeSeriesAddArgs.entry(5, "12"))).isTrue();
        assertThat(t.get(5)).isEqualTo("12");
        assertThat(t.addIfGreater(TimeSeriesAddArgs.entry(5, "1"))).isFalse();
        assertThat(t.get(5)).isEqualTo("12");
        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void testAddAndSum() {
        RTimeSeries<String, String> t = numeric("test");
        // nothing there yet, so this one is created
        assertThat(t.addAndSum(TimeSeriesAddArgs.entry(5, "10"))).isTrue();
        assertThat(t.addAndSum(TimeSeriesAddArgs.entry(5, "5"))).isFalse();
        assertThat(t.get(5)).isEqualTo("15");
        assertThat(t.addAndSum(TimeSeriesAddArgs.entry(5, "-15"))).isFalse();
        assertThat(t.get(5)).isEqualTo("0");
        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void testAddPoliciesCollapseDuplicatesToOneEntry() {
        RTimeSeries<String, String> t = numeric("test");
        for (String v : new String[]{"9", "2", "7"}) {
            t.add(TimeSeriesAddArgs.entry(5, v));
        }

        // the smallest of them is what the incoming value is compared against
        assertThat(t.addIfLess(TimeSeriesAddArgs.entry(5, "3"))).isFalse();
        assertThat(t.range(0, 10)).containsExactly("9", "2", "7");
        assertThat(t.addIfLess(TimeSeriesAddArgs.entry(5, "1"))).isTrue();
        assertThat(t.range(0, 10)).containsExactly("1");

        RTimeSeries<String, String> g = numeric("greater");
        for (String v : new String[]{"9", "2", "7"}) {
            g.add(TimeSeriesAddArgs.entry(5, v));
        }
        assertThat(g.addIfGreater(TimeSeriesAddArgs.entry(5, "8"))).isFalse();
        assertThat(g.addIfGreater(TimeSeriesAddArgs.entry(5, "10"))).isTrue();
        assertThat(g.range(0, 10)).containsExactly("10");

        RTimeSeries<String, String> sum = numeric("sum");
        for (String v : new String[]{"9", "2", "7"}) {
            sum.add(TimeSeriesAddArgs.entry(5, v));
        }
        assertThat(sum.addAndSum(TimeSeriesAddArgs.entry(5, "1"))).isFalse();
        assertThat(sum.range(0, 10)).containsExactly("19");
    }

    @Test
    public void testAddPoliciesIgnoreExpiredEntries() throws InterruptedException {
        RTimeSeries<String, String> t = numeric("test");
        t.add(TimeSeriesAddArgs.entry(5, "1").timeToLive(Duration.ofMillis(200)));
        Thread.sleep(400);

        // the timestamp reads as free, and the stale entry is dropped
        assertThat(t.addIfLess(TimeSeriesAddArgs.entry(5, "100"))).isTrue();
        assertThat(t.range(0, 10)).containsExactly("100");
        assertThat(t.size()).isEqualTo(1);

        RTimeSeries<String, String> sum = numeric("sum");
        sum.add(TimeSeriesAddArgs.entry(5, "50").timeToLive(Duration.ofMillis(200)));
        sum.add(TimeSeriesAddArgs.entry(5, "3"));
        Thread.sleep(400);
        assertThat(sum.addAndSum(TimeSeriesAddArgs.entry(5, "1"))).isFalse();
        assertThat(sum.range(0, 10)).containsExactly("4");
    }

    @Test
    public void testTheComparingPoliciesInSequence() {
        RTimeSeries<String, String> t = numeric("test");
        t.add(TimeSeriesAddArgs.entry(1, "5"));
        t.add(TimeSeriesAddArgs.entry(2, "5"));

        assertThat(t.addIfLess(TimeSeriesAddArgs.entry(1, "9"))).isFalse();
        assertThat(t.addIfLess(TimeSeriesAddArgs.entry(2, "1"))).isTrue();
        assertThat(t.addIfLess(TimeSeriesAddArgs.entry(3, "0"))).isTrue();
        assertThat(t.range(0, 10)).containsExactly("5", "1", "0");

        assertThat(t.addIfGreater(TimeSeriesAddArgs.entry(1, "6"))).isTrue();
        assertThat(t.addIfGreater(TimeSeriesAddArgs.entry(2, "0"))).isFalse();
        assertThat(t.range(0, 10)).containsExactly("6", "1", "0");

        // only the first sum at a timestamp reports a creation
        assertThat(t.addAndSum(TimeSeriesAddArgs.entry(9, "1"))).isTrue();
        assertThat(t.addAndSum(TimeSeriesAddArgs.entry(9, "2"))).isFalse();
        assertThat(t.get(9)).isEqualTo("3");
    }

    @Test
    public void testAddPoliciesCarryLabelsAndTimeToLive() throws InterruptedException {
        RTimeSeries<String, String> t = numeric("test");
        t.addAndSum(TimeSeriesAddArgs.entry(5, "1", "cpu"));
        t.addAndSum(TimeSeriesAddArgs.entry(5, "2", "mem"));
        assertThat(t.entryRange(0, 10)).containsExactly(new TimeSeriesEntry<>(5, "3", "mem"));

        t.addAndSum(TimeSeriesAddArgs.entry(9, "1").timeToLive(Duration.ofMillis(200)));
        assertThat(t.size()).isEqualTo(2);
        Thread.sleep(400);
        assertThat(t.range(0, 10)).containsExactly("3");
    }

    @Test
    public void testAddPoliciesHonourRetention() {
        RTimeSeries<String, String> t = numeric("test");
        t.addAndSum(TimeSeriesAddArgs.entry(1000, "1"));
        t.addAndSum(TimeSeriesAddArgs.entry(1100, "2").retention(Duration.ofMillis(50)));
        assertThat(t.range(0, 10000)).containsExactly("2");

        // an entry below the cutoff is not stored, as with the other add forms
        assertThat(t.addAndSum(TimeSeriesAddArgs.entry(900, "5")
                .retention(Duration.ofMillis(50)))).isFalse();
        assertThat(t.range(0, 10000)).containsExactly("2");
    }

    @Test
    public void testAddPoliciesRequireANumericCodec() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "10"));

        assertThatThrownBy(() -> t.addIfLess(TimeSeriesAddArgs.entry(1, "5")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("addIfLess() on 'test'")
                .hasMessageContaining("StringCodec");
        assertThatThrownBy(() -> t.addAndSum(TimeSeriesAddArgs.entry(1, "5")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("addAndSum() on 'test'");
        assertThatThrownBy(() -> t.aggregate(TimeSeriesAggregationArgs.between(0, 10)
                .bucket(Duration.ofMillis(1)).sum()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Aggregation of 'test'");
    }

    @Test
    public void testAddPoliciesRejectValuesThatAreNotFiniteNumbers() {
        for (String bad : new String[]{"abc", "inf", "-inf", "nan"}) {
            RTimeSeries<String, String> t = numeric("test" + bad.hashCode());
            t.add(TimeSeriesAddArgs.entry(5, "1"));
            assertThatThrownBy(() -> t.addAndSum(TimeSeriesAddArgs.entry(5, bad)))
                    .hasMessageContaining("is not a finite number");
        }

        RTimeSeries<String, String> stored = numeric("stored");
        stored.add(TimeSeriesAddArgs.entry(5, "not a number"));
        assertThatThrownBy(() -> stored.addIfLess(TimeSeriesAddArgs.entry(5, "1")))
                .hasMessageContaining("is not a finite number");

        RTimeSeries<String, String> over = numeric("over");
        over.addAndSum(TimeSeriesAddArgs.entry(5, "1e308"));
        assertThatThrownBy(() -> over.addAndSum(TimeSeriesAddArgs.entry(5, "1e308")))
                .hasMessageContaining("sum at timestamp");
    }

    @Test
    public void testRemovingMoreMembersThanOneCallCanCarry() {
        // members are removed in runs, because unpack() is bounded by the Lua stack
        RTimeSeries<String, String> t = numeric("test");
        for (int i = 0; i < 1200; i++) {
            t.add(TimeSeriesAddArgs.entry(5, String.valueOf(i + 1)));
        }
        assertThat(t.getAll(5)).hasSize(1200);
        assertThat(t.addAndSum(TimeSeriesAddArgs.entry(5, "0"))).isFalse();
        assertThat(t.getAll(5)).containsExactly(String.valueOf(1200 * 1201 / 2));

        for (int i = 0; i < 1200; i++) {
            t.add(TimeSeriesAddArgs.entry(6, String.valueOf(i)));
        }
        assertThat(t.removeAll(6)).isEqualTo(1200);
        assertThat(t.getAll(6)).isEmpty();

        RTimeSeries<String, String> u = numeric("u");
        for (int i = 0; i < 1200; i++) {
            u.add(TimeSeriesAddArgs.entry(i, String.valueOf(i)));
        }
        assertThat(u.pollFirst(700)).hasSize(700);
        assertThat(u.removeRange(0, 2000)).isEqualTo(500);
        assertThat(u.size()).isZero();
    }

    @Test
    public void testAddPoliciesUnderEveryCodecTheMessageRecommends() {
        // a total is written as plain text, so every codec the message recommends has to be
        // able to read one back
        RTimeSeries<Long, String> l = redisson.getTimeSeries("l", LongCodec.INSTANCE);
        assertThat(l.addAndSum(TimeSeriesAddArgs.entry(1, 2L))).isTrue();
        assertThat(l.addAndSum(TimeSeriesAddArgs.entry(1, 3L))).isFalse();
        assertThat(l.get(1)).isEqualTo(5L);
        // and past 2^53 it is still a long, though the arithmetic is done in doubles
        l.addAndSum(TimeSeriesAddArgs.entry(2, 9007199254740993L));
        l.addAndSum(TimeSeriesAddArgs.entry(2, 9007199254740993L));
        assertThat(l.get(2)).isEqualTo(18014398509481984L);

        RTimeSeries<Integer, String> i = redisson.getTimeSeries("i", IntegerCodec.INSTANCE);
        i.addAndSum(TimeSeriesAddArgs.entry(1, 2));
        i.addIfGreater(TimeSeriesAddArgs.entry(1, 7));
        assertThat(i.get(1)).isEqualTo(7);

        RTimeSeries<Double, String> d = redisson.getTimeSeries("d", DoubleCodec.INSTANCE);
        d.addAndSum(TimeSeriesAddArgs.entry(1, 0.1));
        d.addAndSum(TimeSeriesAddArgs.entry(1, 0.2));
        assertThat(d.get(1)).isEqualTo(0.1 + 0.2);
        // a value that is only compared is stored as it arrived
        d.addIfLess(TimeSeriesAddArgs.entry(1, 0.05));
        assertThat(d.get(1)).isEqualTo(0.05);
    }

    @Test
    public void testASumIsRenderedAsShortAsItCanBe() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test", StringCodec.INSTANCE);
        t.addAndSum(TimeSeriesAddArgs.entry(1, "0.1"));
        t.addAndSum(TimeSeriesAddArgs.entry(1, "0"));
        assertThat(t.get(1)).isEqualTo("0.1");

        t.addAndSum(TimeSeriesAddArgs.entry(2, "0.1"));
        t.addAndSum(TimeSeriesAddArgs.entry(2, "0.2"));
        assertThat(t.get(2)).isEqualTo("0.30000000000000004");
        assertThat(Double.parseDouble(t.get(2))).isEqualTo(0.1 + 0.2);
    }

    @Test
    public void testAddAndGet() {
        RTimeSeries<String, String> t = numeric("test");

        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(1, "5"))).isEqualTo(5.0);
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(2, "3"))).isEqualTo(8.0);
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(3, "-4"))).isEqualTo(4.0);
        // what is stored is the total as of each timestamp
        assertThat(t.range(0, 10)).containsExactly("5", "8", "4");
        assertThat(t.size()).isEqualTo(3);

        // the same timestamp again replaces the entry there
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(3, "1"))).isEqualTo(5.0);
        assertThat(t.getAll(3)).containsExactly("5");
    }

    @Test
    public void testAddAndGetKeepsTheTotalOutsideTheEntries() throws InterruptedException {
        RTimeSeries<String, String> t = numeric("test");
        t.addAndGet(TimeSeriesAddArgs.<String, String>entry(1, "100")
                .timeToLive(Duration.ofMillis(300)));
        Thread.sleep(500);
        assertThat(t.range(0, 10)).isEmpty();

        // the entry holding the total is gone, and the total is not
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(2, "5"))).isEqualTo(105.0);
        assertThat(t.range(0, 10)).containsExactly("105");
    }

    @Test
    public void testAddAndGetIgnoresEntriesAddedByOtherMeans() {
        RTimeSeries<String, String> t = numeric("test");
        t.addAndGet(TimeSeriesAddArgs.entry(1, "10"));
        t.add(TimeSeriesAddArgs.entry(2, "999"));
        t.addOrReplace(TimeSeriesAddArgs.entry(3, "-777"));
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(4, "5"))).isEqualTo(15.0);
        assertThat(t.range(0, 10)).containsExactly("10", "999", "-777", "15");
    }

    @Test
    public void testAddAndGetRecordsALateIncrementAtTheLastTimestamp() {
        RTimeSeries<String, String> t = numeric("test");
        t.addAndGet(TimeSeriesAddArgs.entry(10, "1"));

        // it still counts, and it does not go behind what has already been recorded
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(9, "1"))).isEqualTo(2.0);
        assertThat(t.entryRange(0, 100)).containsExactly(new TimeSeriesEntry<>(10, "2"));
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(11, "1"))).isEqualTo(3.0);
        assertThat(t.entryRange(0, 100)).containsExactly(new TimeSeriesEntry<>(10, "2"),
                                                         new TimeSeriesEntry<>(11, "3"));
        // an entry added by another method may still go anywhere
        assertThat(t.addIfAbsent(TimeSeriesAddArgs.entry(1, "0"))).isTrue();
    }

    @Test
    public void testAddAndGetFromSeveralThreadsLosesNothing() throws InterruptedException {
        RTimeSeries<String, String> t = numeric("test");
        int threads = 8;
        int each = 100;
        AtomicInteger timestamp = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    go.await();
                    for (int k = 0; k < each; k++) {
                        t.addAndGet(TimeSeriesAddArgs.entry(timestamp.incrementAndGet(), "1"));
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(120, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).hasValue(0);
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(timestamp.incrementAndGet(), "0")))
                .isEqualTo(threads * each);

        // and what was recorded never goes backwards, whatever order the calls arrived in
        double previous = -1;
        for (String value : t.range(0, Long.MAX_VALUE)) {
            double current = Double.parseDouble(value);
            assertThat(current).isGreaterThanOrEqualTo(previous);
            previous = current;
        }
    }

    @Test
    public void testAddAndGetKeepsItsTotalThroughAnEvictionPass() throws InterruptedException {
        RTimeSeries<String, String> t = numeric("evicting");
        t.addAndGet(TimeSeriesAddArgs.<String, String>entry(1, "100")
                .timeToLive(Duration.ofMillis(300)));
        Thread.sleep(500);
        // the eviction task reclaims the counter key of an emptied collection, but not while
        // it is holding a total
        assertThat(t.size()).isZero();
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(2, "5"))).isEqualTo(105.0);
    }

    @Test
    public void testAddAndGetRefusesAnAmountTooSmallToRecord() {
        RTimeSeries<String, String> t = numeric("test");
        assertThatThrownBy(() -> t.addAndGet(TimeSeriesAddArgs.entry(1, "1e-300")))
                .hasMessageContaining("too small to change a total");
        assertThat(t.size()).isZero();
        // zero is not an increment and is allowed
        t.addAndGet(TimeSeriesAddArgs.entry(1, "1"));
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(2, "0"))).isEqualTo(1.0);
    }

    @Test
    public void testAddAndGetLeavesTheTotalWhereTheHistoryIsWhenItCannotFinish() {
        RTimeSeries<String, String> t = numeric("test");
        t.addAndGet(TimeSeriesAddArgs.entry(1, "5"));
        redisson.<String, String>getMap("redisson__ts_seq:{test}", StringCodec.INSTANCE)
                .put("id", "999999999999999999999");

        assertThatThrownBy(() -> t.addAndGet(TimeSeriesAddArgs.entry(2, "7")))
                .hasMessageContaining("sequence overflow");
        // the id is taken before the total moves, so the two cannot disagree
        assertThat(t.range(0, 10)).containsExactly("5");
    }

    @Test
    public void testAddAndGetInSequence() {
        RTimeSeries<String, String> t = numeric("test");
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(1, "1"))).isEqualTo(1.0);
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(2, "2"))).isEqualTo(3.0);
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(3, "3"))).isEqualTo(6.0);
        assertThat(t.range(0, 10)).containsExactly("1", "3", "6");

        // two at one timestamp leave the later total there
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(4, "1"))).isEqualTo(7.0);
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(4, "1"))).isEqualTo(8.0);
        assertThat(t.getAll(4)).containsExactly("8");
    }

    @Test
    public void testAddAndGetCarriesLabelsAndTimeToLive() {
        RTimeSeries<String, String> t = numeric("test");
        t.addAndGet(TimeSeriesAddArgs.<String, String>entry(1, "1").label("cpu"));
        t.addAndGet(TimeSeriesAddArgs.<String, String>entry(2, "2").label("cpu"));
        assertThat(t.entryRangeByLabel(0, 10, "cpu")).containsExactly(
                new TimeSeriesEntry<>(1, "1", "cpu"),
                new TimeSeriesEntry<>(2, "3", "cpu"));
        assertThat(t.labels()).containsExactly("cpu");
    }

    @Test
    public void testAddAndGetRefusesWhatItCannotAdd() {
        RTimeSeries<String, String> t = numeric("test");
        for (String bad : new String[]{"abc", "inf", "-inf", "nan"}) {
            assertThatThrownBy(() -> t.addAndGet(TimeSeriesAddArgs.entry(1, bad)))
                    .as(bad).hasMessageContaining("is not a finite number");
        }
        assertThat(t.size()).isZero();

        // the total is added in long double, which reaches well past a double
        t.addAndGet(TimeSeriesAddArgs.entry(1, "1e308"));
        assertThatThrownBy(() -> t.addAndGet(TimeSeriesAddArgs.entry(2, "1e308")))
                .hasMessageContaining("total at timestamp");

        RTimeSeries<String, String> binary = redisson.getTimeSeries("binary");
        assertThatThrownBy(() -> binary.addAndGet(TimeSeriesAddArgs.entry(1, "1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("addAndGet() on 'binary'");
        assertThatThrownBy(() -> binary.addAndGet(TimeSeriesAddArgs.entry(2, "1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("addAndGet() on 'binary'");
    }

    @Test
    public void testAddAndGetUnderTheOtherNumericCodecs() {
        RTimeSeries<Long, String> l = redisson.getTimeSeries("l", LongCodec.INSTANCE);
        assertThat(l.addAndGet(TimeSeriesAddArgs.entry(1, 5L))).isEqualTo(5.0);
        assertThat(l.addAndGet(TimeSeriesAddArgs.entry(2, 7L))).isEqualTo(12.0);
        assertThat(l.get(2)).isEqualTo(12L);

        RTimeSeries<Double, String> d = redisson.getTimeSeries("d", DoubleCodec.INSTANCE);
        // the total accumulates in long double, so it holds together better than a
        // running sum of doubles would
        assertThat(d.addAndGet(TimeSeriesAddArgs.entry(1, 0.1))).isEqualTo(0.1);
        assertThat(d.addAndGet(TimeSeriesAddArgs.entry(2, 0.2))).isEqualTo(0.3);
        assertThat(d.get(2)).isEqualTo(0.3);
    }

    @Test
    public void testAddAndGetSurvivesTheCollectionBeingRenamedAndCopied() {
        RTimeSeries<String, String> t = numeric("test");
        t.addAndGet(TimeSeriesAddArgs.entry(1, "7"));
        assertThat(t.copy("copy")).isTrue();

        RTimeSeries<String, String> copy = numeric("copy");
        assertThat(copy.addAndGet(TimeSeriesAddArgs.entry(2, "1"))).isEqualTo(8.0);
        assertThat(t.addAndGet(TimeSeriesAddArgs.entry(2, "2"))).isEqualTo(9.0);

        t.rename("moved");
        RTimeSeries<String, String> moved = numeric("moved");
        assertThat(moved.addAndGet(TimeSeriesAddArgs.entry(3, "1"))).isEqualTo(10.0);
    }

    @Test
    public void testAddArgs() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");

        assertThat(t.add(TimeSeriesAddArgs.entry(5, "a"))).isTrue();
        // a plain add leaves what is already there, so a timestamp can hold several
        assertThat(t.add(TimeSeriesAddArgs.entry(5, "b"))).isTrue();
        assertThat(t.getAll(5)).containsExactly("a", "b");
        assertThat(t.size()).isEqualTo(2);
    }

    @Test
    public void testAddArgsCarriesLabelAndTimeToLive() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(TimeSeriesAddArgs.entry(1, "a", "cpu"));
        t.add(TimeSeriesAddArgs.<String, String>entry(2, "b").timeToLive(Duration.ofMillis(300)));

        assertThat(t.entryRange(0, 10)).containsExactly(new TimeSeriesEntry<>(1, "a", "cpu"),
                                                        new TimeSeriesEntry<>(2, "b"));
        assertThat(t.rangeByLabel(0, 10, "cpu")).containsExactly("a");

        Thread.sleep(500);
        assertThat(t.range(0, 10)).containsExactly("a");
    }

    @Test
    public void testAddArgsIsRefusedOnlyByItsOwnRetention() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        assertThat(t.add(TimeSeriesAddArgs.<String, String>entry(1000, "old")
                .retention(Duration.ofMillis(100)))).isTrue();
        assertThat(t.add(TimeSeriesAddArgs.<String, String>entry(1200, "new")
                .retention(Duration.ofMillis(100)))).isTrue();
        assertThat(t.range(0, 10000)).containsExactly("new");

        // behind the cutoff, so it is not written rather than written and trimmed
        assertThat(t.add(TimeSeriesAddArgs.<String, String>entry(900, "way back")
                .retention(Duration.ofMillis(100)))).isFalse();
        assertThat(t.getAll(900)).isEmpty();
    }

    /**
     * The overloads the arguments object replaces are deprecated but still shipped, so this is
     * where they keep their coverage; everywhere else the tests use the arguments object.
     */
    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedAddOverloadsAgreeWithTheArgumentsObject() throws InterruptedException {
        RTimeSeries<String, String> args = redisson.getTimeSeries("args");
        RTimeSeries<String, String> plain = redisson.getTimeSeries("plain");

        args.add(TimeSeriesAddArgs.entry(1, "a"));
        args.add(TimeSeriesAddArgs.entry(2, "b", "cpu"));
        args.add(TimeSeriesAddArgs.entry(3, "c").timeToLive(Duration.ofMillis(300)));
        args.add(TimeSeriesAddArgs.entry(4, "d").timeToLive(Duration.ofMillis(300)));
        args.add(TimeSeriesAddArgs.entry(5, "e", "mem").timeToLive(Duration.ofMillis(300)));
        args.addAll(Collections.singletonMap(6L, "f"), Duration.ofMillis(300));

        plain.add(1, "a");
        plain.add(2, "b", "cpu");
        plain.add(3, "c", 300, TimeUnit.MILLISECONDS);
        plain.add(4, "d", Duration.ofMillis(300));
        plain.add(5, "e", "mem", Duration.ofMillis(300));
        plain.addAll(Collections.singletonMap(6L, "f"), 300, TimeUnit.MILLISECONDS);

        assertThat(args.entryRange(0, 10)).isEqualTo(plain.entryRange(0, 10));
        assertThat(args.labels()).containsExactlyInAnyOrderElementsOf(plain.labels());

        // and the interval each overload carries is still honoured
        Thread.sleep(500);
        assertThat(plain.range(0, 10)).containsExactly("a", "b");
        assertThat(args.range(0, 10)).isEqualTo(plain.range(0, 10));
    }

}
