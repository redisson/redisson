package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RTimeSeries;
import org.redisson.api.ts.TimeSeriesAddArgs;
import org.redisson.api.TimeSeriesEntry;

import java.time.Duration;
import java.util.Arrays;
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

    @Test
    public void testBackfilledOrder() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        // inserted out of timestamp order
        t.add(100, "A");
        t.add(50, "B");
        t.add(200, "C");

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
        t.add(5, "e");
        t.add(4, "d");
        t.add(3, "c");
        t.add(2, "b");
        t.add(1, "a");

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
        t.add(5, "e");
        t.add(4, "d");
        t.add(3, "c");
        t.add(2, "b");
        t.add(1, "a");

        assertThat(t.pollFirst()).isEqualTo("a");
        assertThat(t.pollLast()).isEqualTo("e");
        assertThat(t.pollFirst(2)).containsExactly("b", "c");
        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    public void testTTLBackfilledOrder() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(300, "keep-late");
        t.add(100, "expires", Duration.ofSeconds(1));
        t.add(200, "keep-mid");

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
            t.add(i, "expired" + i, Duration.ofMillis(300));
        }
        Thread.sleep(500);
        for (int i = 0; i < 20; i++) {
            t.add(7000, "dup" + i);
        }
        for (int i = 0; i < 20; i++) {
            t.add(8000 + i, "tail" + i);
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

        t.add(1, "a", Duration.ofMillis(800));
        t.add(2, "b", Duration.ofMillis(800));
        Thread.sleep(1000);

        assertThat(t.first()).isNull();
        assertThat(t.firstTimestamp()).isNull();
        assertThat(t.first(5)).isEmpty();
        assertThat(t.pollFirst()).isNull();
    }

    @Test
    public void testZeroAndNegativeCount() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(1, "a");
        t.add(2, "b");
        t.add(3, "c");

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
            t.add(i, "old" + i, Duration.ofMillis(500));
        }
        Thread.sleep(700);
        t.add(100, "a");
        t.add(101, "b");

        assertThat(t.size()).isEqualTo(2);
        assertThat(t.pollFirst()).isEqualTo("a");
        assertThat(t.pollFirst(10)).containsExactly("b");
        assertThat(t.size()).isEqualTo(0);
    }

    @Test
    public void testLargeExpiredBacklog() throws InterruptedException {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        for (int i = 0; i < 200; i++) {
            t.add(i, "expired" + i, Duration.ofMillis(300));
        }
        Thread.sleep(500);
        t.add(9000, "z");
        t.add(8000, "y");
        t.add(8500, "x");

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
        t.add(5, "x");
        t.add(5, "y");
        t.add(5, "z");
        assertThat(t.size()).isEqualTo(3);

        assertThat(t.addOrReplace(TimeSeriesAddArgs.entry(5, "last"))).isFalse();
        assertThat(t.size()).isEqualTo(1);
        assertThat(t.get(5)).isEqualTo("last");
    }

    @Test
    public void testAddAllIfAbsentAndAddAllOrReplace() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        assertThat(t.addAllIfAbsent(Arrays.asList(TimeSeriesAddArgs.entry(1, "a"),
                                                  TimeSeriesAddArgs.entry(2, "b"),
                                                  TimeSeriesAddArgs.entry(3, "c")))).isEqualTo(3);

        assertThat(t.addAllIfAbsent(Arrays.asList(TimeSeriesAddArgs.entry(2, "ignored"),
                                                  TimeSeriesAddArgs.entry(4, "d")))).isEqualTo(1);
        assertThat(t.get(2)).isEqualTo("b");

        assertThat(t.addAllOrReplace(Arrays.asList(TimeSeriesAddArgs.entry(2, "B"),
                                                   TimeSeriesAddArgs.entry(5, "e")))).isEqualTo(1);
        assertThat(t.get(2)).isEqualTo("B");
        assertThat(t.size()).isEqualTo(5);
    }

    @Test
    public void testAddArgsLabelAndTimeToLive() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.addAllOrReplace(Arrays.asList(
                TimeSeriesAddArgs.entry(1, "a", "lab1"),
                TimeSeriesAddArgs.entry(2, "b"),
                TimeSeriesAddArgs.entry(3, "c", "lab3").timeToLive(Duration.ofMillis(400))));

        assertThat(t.firstEntries(3)).containsExactly(new TimeSeriesEntry<>(1, "a", "lab1"),
                                                      new TimeSeriesEntry<>(2, "b"),
                                                      new TimeSeriesEntry<>(3, "c", "lab3"));

        Thread.sleep(600);
        assertThat(t.size()).isEqualTo(2);
    }

    @Test
    public void testExpiredDuplicateDoesNotMaskLiveSample() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(5, "old", Duration.ofMillis(300));
        t.add(5, "new");
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
        t.add(5, "a");
        t.add(5, "b");
        t.add(5, "c");

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
        t.add(1, "temp42");
        t.add(2, "temp42");
        t.add(3, "temp42");

        assertThat(t.size()).isEqualTo(3);
        assertThat(t.entryRange(1, 3)).containsExactly(new TimeSeriesEntry<>(1, "temp42"),
                                                       new TimeSeriesEntry<>(2, "temp42"),
                                                       new TimeSeriesEntry<>(3, "temp42"));
    }

    @Test
    public void testDuplicateOrderAcrossAddApis() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.addIfAbsent(TimeSeriesAddArgs.entry(9, "a"));
        t.add(9, "b");
        t.add(9, "c");

        assertThat(t.range(9, 9)).containsExactly("a", "b", "c");
        assertThat(t.get(9)).isEqualTo("a");
    }

    @Test
    public void testMixedLabelsAtSameTimestamp() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(5, "v", "label");
        t.add(5, "v");
        t.add(5, "v");

        assertThat(t.size()).isEqualTo(3);
        assertThat(t.range(5, 5)).containsExactly("v", "v", "v");
    }

    @Test
    public void testMixedLabelsKeepInsertionOrder() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(5, "a", "label");
        t.add(5, "b");
        t.add(5, "c");

        assertThat(t.range(5, 5)).containsExactly("a", "b", "c");
        assertThat(t.get(5)).isEqualTo("a");
        assertThat(t.entryRange(5, 5)).containsExactly(new TimeSeriesEntry<>(5, "a", "label"),
                                                       new TimeSeriesEntry<>(5, "b"),
                                                       new TimeSeriesEntry<>(5, "c"));
    }

    @Test
    public void testEmptyLabelDistinctFromNoLabel() {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(1, "v1", "lab");
        t.add(2, "v2");
        t.add(3, "v3", "");

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
    public void testMixedLabelsDoNotClobberTimeToLive() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(5, "keep");
        t.add(5, "keep", "label");
        t.add(5, "keep", Duration.ofMillis(400));

        assertThat(t.size()).isEqualTo(3);
        Thread.sleep(600);
        assertThat(t.size()).isEqualTo(2);
    }

    @Test
    public void testNegativeAndZeroTimestamps() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(-5, "same");
        t.add(-5, "same");
        t.add(-5, "same");
        t.add(0, "same");
        t.add(0, "same");

        assertThat(t.size()).isEqualTo(5);
        assertThat(t.range(-5, -5)).containsExactly("same", "same", "same");
        assertThat(t.range(0, 0)).containsExactly("same", "same");
        assertThat(t.firstTimestamp()).isEqualTo(-5);
        assertThat(t.lastTimestamp()).isEqualTo(0);
    }

    @Test
    public void testLargeTimestamps() {
        RTimeSeries<String, Object> t = redisson.getTimeSeries("test");
        t.add(100_000_000_000_000_000L, "w");
        t.add(1_000_000_000_000_000_000L, "v");

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
        t.add(1, "expired", Duration.ofMillis(300));
        for (int i = 0; i < 5; i++) {
            t.add(2, "dup" + i);
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
        t.add(5, "a", "L1");
        t.add(5, "b");
        t.add(5, "c", "L3");
        t.add(6, "other");

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
        t.add(5, "a", "L1");
        t.add(5, "b");

        assertThat(t.getAndRemoveAllEntries(5))
                .containsExactly(new TimeSeriesEntry<>(5, "a", "L1"),
                                 new TimeSeriesEntry<>(5, "b"));
        assertThat(t.size()).isZero();

        t.add(7, "x");
        t.add(7, "y");
        assertThat(t.getAndRemoveAll(7)).containsExactly("x", "y");
        assertThat(t.getAll(7)).isEmpty();
        assertThat(t.getAndRemoveAll(99)).isEmpty();
    }

    @Test
    public void testPluralAccessorsSkipExpired() throws InterruptedException {
        RTimeSeries<String, String> t = redisson.getTimeSeries("test");
        t.add(5, "gone1", Duration.ofMillis(300));
        t.add(5, "keep1");
        t.add(5, "gone2", Duration.ofMillis(300));
        t.add(5, "keep2");
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
        t.add(5, "a");
        t.add(5, "b");
        t.add(5, "c");

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
        t.add(5, "a");
        t.add(5, "b");
        t.add(5, "c");
        assertThat(t.removeAll(5)).isEqualTo(3);

        t.add(5, "d");
        t.add(5, "e");
        assertThat(t.range(5, 5)).containsExactly("d", "e");
        assertThat(t.get(5)).isEqualTo("d");
    }

}
