package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.api.listener.ScoredSortedSetAddListener;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RankedEntry;
import org.redisson.client.protocol.ScoredEntry;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RedissonScoredSortedSetTest extends RedisDockerTest {

    @Test
    public void testEntries() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("test");
        set.add(1.1, "v1");
        set.add(1.2, "v2");
        set.add(1.3, "v3");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("test2");

        ScoredEntry<String> s3 = set2.firstEntry();
        assertThat(s3).isNull();
        ScoredEntry<String> s = set.firstEntry();
        assertThat(s).isEqualTo(new ScoredEntry<>(1.1, "v1"));
        ScoredEntry<String> s2 = set.lastEntry();
        assertThat(s2).isEqualTo(new ScoredEntry<>(1.3, "v3"));
    }

    @Test
    public void testPollEntryDuration() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("test");
        set.add(1.1, "v1");
        set.add(1.2, "v2");
        set.add(1.3, "v3");
        set.add(1.4, "v4");
        set.add(1.5, "v5");

        List<ScoredEntry<String>> v1 = set.pollFirstEntries(Duration.ofSeconds(1), 2);
        assertThat(v1).containsOnly(new ScoredEntry<>(1.1, "v1"), new ScoredEntry<>(1.2, "v2"));

        List<ScoredEntry<String>> v2 = set.pollLastEntries(Duration.ofSeconds(1), 2);
        assertThat(v2).containsOnly(new ScoredEntry<>(1.4, "v4"), new ScoredEntry<>(1.5, "v5"));

        assertThat(set.size()).isEqualTo(1);
    }
    @Test
    public void testPollEntry() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("test");
        set.add(1.1, "v1");
        set.add(1.2, "v2");
        set.add(1.3, "v3");

        ScoredEntry<String> e = set.pollFirstEntry();
        assertThat(e).isEqualTo(new ScoredEntry<>(1.1, "v1"));

        ScoredEntry<String> e2 = set.pollLastEntry();
        assertThat(e2).isEqualTo(new ScoredEntry<>(1.3, "v3"));

        assertThat(set.size()).isEqualTo(1);
    }

    @Test
    public void testEntryScanIterator() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("test");
        set.add(1.1, "v1");
        set.add(1.2, "v2");
        set.add(1.3, "v3");

        Iterator<ScoredEntry<String>> entries = set.entryIterator();
        assertThat(entries).toIterable().containsExactly(new ScoredEntry<>(1.1, "v1"),
                                                new ScoredEntry<>(1.2, "v2"), new ScoredEntry<>(1.3, "v3"));
    }

    @Test
    public void testRankEntry() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("test");
        set.add(1.1, "v1");
        set.add(1.2, "v2");
        set.add(1.3, "v3");

        RankedEntry<String> v1 = set.rankEntry("v1");
        assertThat(v1.getRank()).isEqualTo(0);
        assertThat(v1.getScore()).isEqualTo(1.1);

        RankedEntry<String> v3 = set.rankEntry("v3");
        assertThat(v3.getRank()).isEqualTo(2);
        assertThat(v3.getScore()).isEqualTo(1.3);

        RankedEntry<String> v4 = set.rankEntry("v4");
        assertThat(v4).isNull();
    }

    @Test
    public void testReplace() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("test");
        set.add(1, 10);
        set.add(2, 20);
        set.add(3, 30);

        assertThat(set.replace(10, 60)).isTrue();
        assertThat(set.getScore(60)).isEqualTo(1);
        assertThat(set.size()).isEqualTo(3);

        assertThat(set.replace(10, 80)).isFalse();
        assertThat(set.getScore(60)).isEqualTo(1);
        assertThat(set.getScore(80)).isNull();
        assertThat(set.size()).isEqualTo(3);
    }

    @Test
    public void testRandom() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("test");
        set.add(1, 10);
        set.add(2, 20);
        set.add(3, 30);

        assertThat(set.random()).isIn(10, 20, 30);
        assertThat(set.random(2)).containsAnyOf(10, 20, 30).hasSize(2);

        Map<Integer, Double> map = set.randomEntries(2);
        assertThat(map).containsAnyOf(entry(10, 1D), entry(20, 2D), entry(30, 3D)).hasSize(2);
    }

    @Test
    public void testTakeFirst() {
        final RScoredSortedSet<Integer> queue1 = redisson.getScoredSortedSet("queue:pollany");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RScoredSortedSet<Integer> queue2 = redisson.getScoredSortedSet("queue:pollany1");
            RScoredSortedSet<Integer> queue3 = redisson.getScoredSortedSet("queue:pollany2");
            queue1.add(0.1, 1);
        }, 3, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = queue1.takeFirst();
        Assertions.assertEquals(1, l);
        Assertions.assertTrue(System.currentTimeMillis() - s > 2000);
    }
    
    @Test
    public void testPollFirstFromAny() {
        final RScoredSortedSet<Integer> queue1 = redisson.getScoredSortedSet("queue:pollany");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RScoredSortedSet<Integer> queue2 = redisson.getScoredSortedSet("queue:pollany1");
            RScoredSortedSet<Integer> queue3 = redisson.getScoredSortedSet("queue:pollany2");
            queue3.add(0.1, 2);
            queue1.add(0.1, 1);
            queue2.add(0.1, 3);
        }, 3, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = queue1.pollFirstFromAny(4, TimeUnit.SECONDS, "queue:pollany1", "queue:pollany2");

        Assertions.assertEquals(2, l);
        Assertions.assertTrue(System.currentTimeMillis() - s > 2000);
    }

    @Test
    public void testPollFirstFromAnyCount() {
        RScoredSortedSet<Integer> queue1 = redisson.getScoredSortedSet("queue:pollany");
        RScoredSortedSet<Integer> queue2 = redisson.getScoredSortedSet("queue:pollany1");
        RScoredSortedSet<Integer> queue3 = redisson.getScoredSortedSet("queue:pollany2");
        queue1.add(0.1, 1);
        queue1.add(0.2, 2);
        queue1.add(0.3, 3);
        queue2.add(0.4, 4);
        queue2.add(0.5, 5);
        queue2.add(0.6, 6);
        queue3.add(0.7, 7);
        queue3.add(0.8, 8);
        queue3.add(0.9, 9);

        List<Integer> elements = queue1.pollFirstFromAny(Duration.ofSeconds(4), 2, "queue:pollany1", "queue:pollany2");
        assertThat(elements).containsExactly(1, 2);
        assertThat(queue1.size()).isEqualTo(1);

        List<Integer> elements2 = queue1.pollFirstFromAny(2, "queue:pollany1", "queue:pollany2");
        assertThat(elements2).containsExactly(3);
        assertThat(elements2.size()).isEqualTo(1);
    }

    @Test
    public void testPollFirstEntriesFromAnyCount() {
        RScoredSortedSet<Integer> queue1 = redisson.getScoredSortedSet("queue:pollany");
        RScoredSortedSet<Integer> queue2 = redisson.getScoredSortedSet("queue:pollany1");
        RScoredSortedSet<Integer> queue3 = redisson.getScoredSortedSet("queue:pollany2");
        queue1.add(0.1, 1);
        queue1.add(0.2, 2);
        queue1.add(0.3, 3);
        queue2.add(0.4, 4);
        queue2.add(0.5, 5);
        queue2.add(0.6, 6);
        queue3.add(0.7, 7);
        queue3.add(0.8, 8);
        queue3.add(0.9, 9);

        Map<String, Map<Integer, Double>> elements = queue1.pollFirstEntriesFromAny(2, "queue:pollany1", "queue:pollany2");
        assertThat(elements).hasSize(1);
        assertThat(elements.get("queue:pollany")).containsEntry(1, 0.1).containsEntry(2, 0.2).hasSize(2);
        assertThat(queue1.size()).isEqualTo(1);

        Map<String, Map<Integer, Double>> elements2 = queue1.pollFirstEntriesFromAny(2, "queue:pollany1", "queue:pollany2");
        assertThat(elements2).hasSize(1);
        assertThat(elements2.get("queue:pollany")).containsEntry(3, 0.3).hasSize(1);
        assertThat(elements2.size()).isEqualTo(1);
    }

    @Test
    public void testPollLastEntriesFromAnyCount() {
        RScoredSortedSet<Integer> queue1 = redisson.getScoredSortedSet("queue:pollany");
        RScoredSortedSet<Integer> queue2 = redisson.getScoredSortedSet("queue:pollany1");
        RScoredSortedSet<Integer> queue3 = redisson.getScoredSortedSet("queue:pollany2");
        queue1.add(0.1, 1);
        queue1.add(0.2, 2);
        queue1.add(0.3, 3);
        queue2.add(0.4, 4);
        queue2.add(0.5, 5);
        queue2.add(0.6, 6);
        queue3.add(0.7, 7);
        queue3.add(0.8, 8);
        queue3.add(0.9, 9);

        Map<String, Map<Integer, Double>> elements = queue1.pollLastEntriesFromAny(2, "queue:pollany1", "queue:pollany2");
        assertThat(elements).hasSize(1);
        assertThat(elements.get("queue:pollany")).containsEntry(3, 0.3).containsEntry(2, 0.2).hasSize(2);
        assertThat(queue1.size()).isEqualTo(1);

        Map<String, Map<Integer, Double>> elements2 = queue1.pollLastEntriesFromAny(2, "queue:pollany1", "queue:pollany2");
        assertThat(elements2).hasSize(1);
        assertThat(elements2.get("queue:pollany")).containsEntry(1, 0.1).hasSize(1);
        assertThat(elements2.size()).isEqualTo(1);
    }

    @Test
    public void testPollFirstEntriesFromAnyTimeout() {
        RScoredSortedSet<Integer> queue1 = redisson.getScoredSortedSet("queue:pollany");
        RScoredSortedSet<Integer> queue2 = redisson.getScoredSortedSet("queue:pollany1");
        RScoredSortedSet<Integer> queue3 = redisson.getScoredSortedSet("queue:pollany2");
        queue1.add(0.1, 1);
        queue1.add(0.2, 2);
        queue1.add(0.3, 3);
        queue2.add(0.4, 4);
        queue2.add(0.5, 5);
        queue2.add(0.6, 6);
        queue3.add(0.7, 7);
        queue3.add(0.8, 8);
        queue3.add(0.9, 9);

        Map<String, Map<Integer, Double>> elements = queue1.pollFirstEntriesFromAny(Duration.ofSeconds(2), 2, "queue:pollany1", "queue:pollany2");
        assertThat(elements).hasSize(1);
        assertThat(elements.get("queue:pollany")).containsEntry(1, 0.1).containsEntry(2, 0.2).hasSize(2);
        assertThat(queue1.size()).isEqualTo(1);

        Map<String, Map<Integer, Double>> elements2 = queue1.pollFirstEntriesFromAny(Duration.ofSeconds(2),2, "queue:pollany1", "queue:pollany2");
        assertThat(elements2).hasSize(1);
        assertThat(elements2.get("queue:pollany")).containsEntry(3, 0.3).hasSize(1);
        assertThat(elements2.size()).isEqualTo(1);
    }

    @Test
    public void testPollLastEntriesFromAnyTimeout() {
        RScoredSortedSet<Integer> queue1 = redisson.getScoredSortedSet("queue:pollany");
        RScoredSortedSet<Integer> queue2 = redisson.getScoredSortedSet("queue:pollany1");
        RScoredSortedSet<Integer> queue3 = redisson.getScoredSortedSet("queue:pollany2");
        queue1.add(0.1, 1);
        queue1.add(0.2, 2);
        queue1.add(0.3, 3);
        queue2.add(0.4, 4);
        queue2.add(0.5, 5);
        queue2.add(0.6, 6);
        queue3.add(0.7, 7);
        queue3.add(0.8, 8);
        queue3.add(0.9, 9);

        Map<String, Map<Integer, Double>> elements = queue1.pollLastEntriesFromAny(Duration.ofSeconds(2), 2, "queue:pollany1", "queue:pollany2");
        assertThat(elements).hasSize(1);
        assertThat(elements.get("queue:pollany")).containsEntry(3, 0.3).containsEntry(2, 0.2).hasSize(2);
        assertThat(queue1.size()).isEqualTo(1);

        Map<String, Map<Integer, Double>> elements2 = queue1.pollLastEntriesFromAny(Duration.ofSeconds(2),2, "queue:pollany1", "queue:pollany2");
        assertThat(elements2).hasSize(1);
        assertThat(elements2.get("queue:pollany")).containsEntry(1, 0.1).hasSize(1);
        assertThat(elements2.size()).isEqualTo(1);
    }

    @Test
    public void testPollLastFromAnyCount() {
        RScoredSortedSet<Integer> queue1 = redisson.getScoredSortedSet("queue:pollany");
        RScoredSortedSet<Integer> queue2 = redisson.getScoredSortedSet("queue:pollany1");
        RScoredSortedSet<Integer> queue3 = redisson.getScoredSortedSet("queue:pollany2");
        queue1.add(0.1, 1);
        queue1.add(0.2, 2);
        queue1.add(0.3, 3);
        queue2.add(0.4, 4);
        queue2.add(0.5, 5);
        queue2.add(0.6, 6);
        queue3.add(0.7, 7);
        queue3.add(0.8, 8);
        queue3.add(0.9, 9);

        List<Integer> elements = queue1.pollLastFromAny(Duration.ofSeconds(4), 2, "queue:pollany1", "queue:pollany2");
        assertThat(elements).containsExactly(3, 2);
        assertThat(queue1.size()).isEqualTo(1);

        List<Integer> elements2 = queue1.pollLastFromAny(2, "queue:pollany1", "queue:pollany2");
        assertThat(elements2).containsExactly(1);
        assertThat(elements2.size()).isEqualTo(1);
    }

    @Test
    public void testPollLastFromAny() {
        final RScoredSortedSet<Integer> queue1 = redisson.getScoredSortedSet("queue:pollany");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RScoredSortedSet<Integer> queue2 = redisson.getScoredSortedSet("queue:pollany1");
            RScoredSortedSet<Integer> queue3 = redisson.getScoredSortedSet("queue:pollany2");
            queue3.add(0.1, 2);
            queue1.add(0.1, 1);
            queue2.add(0.1, 3);
        }, 3, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = queue1.pollLastFromAny(4, TimeUnit.SECONDS, "queue:pollany1", "queue:pollany2");

        Assertions.assertEquals(2, l);
        Assertions.assertTrue(System.currentTimeMillis() - s > 2000);
    }
    
    @Test
    public void testSortOrder() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("list", IntegerCodec.INSTANCE);
        set.add(10, 1);
        set.add(9, 2);
        set.add(8, 3);
        
        Set<Integer> descSort = set.readSort(SortOrder.DESC);
        assertThat(descSort).containsExactly(3, 2, 1);

        Set<Integer> ascSort = set.readSort(SortOrder.ASC);
        assertThat(ascSort).containsExactly(1, 2, 3);
    }
    
    @Test
    public void testSortOrderLimit() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("list", IntegerCodec.INSTANCE);
        set.add(10, 1);
        set.add(9, 2);
        set.add(8, 3);
        
        Set<Integer> descSort = set.readSort(SortOrder.DESC, 1, 2);
        assertThat(descSort).containsExactly(2, 1);

        Set<Integer> ascSort = set.readSort(SortOrder.ASC, 1, 2);
        assertThat(ascSort).containsExactly(2, 3);
    }

    @Test
    public void testSortOrderByPattern() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("list", IntegerCodec.INSTANCE);
        set.add(10, 1);
        set.add(9, 2);
        set.add(8, 3);
        
        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(2);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);
        
        Set<Integer> descSort = set.readSort("test*", SortOrder.DESC);
        assertThat(descSort).containsExactly(1, 2, 3);

        Set<Integer> ascSort = set.readSort("test*", SortOrder.ASC);
        assertThat(ascSort).containsExactly(3, 2, 1);
    }
    
    @Test
    public void testSortOrderByPatternLimit() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("list", IntegerCodec.INSTANCE);
        set.add(10, 1);
        set.add(9, 2);
        set.add(8, 3);
        
        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(2);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);
        
        Set<Integer> descSort = set.readSort("test*", SortOrder.DESC, 1, 2);
        assertThat(descSort).containsExactly(2, 3);

        Set<Integer> ascSort = set.readSort("test*", SortOrder.ASC, 1, 2);
        assertThat(ascSort).containsExactly(2, 1);
    }

    @Test
    public void testSortOrderByPatternGet() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("list", StringCodec.INSTANCE);
        set.add(10, 1);
        set.add(9, 2);
        set.add(8, 3);
        
        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(1);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(2);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(3);
        
        redisson.getBucket("tester1", StringCodec.INSTANCE).set("obj1");
        redisson.getBucket("tester2", StringCodec.INSTANCE).set("obj2");
        redisson.getBucket("tester3", StringCodec.INSTANCE).set("obj3");
        
        Collection<String> descSort = set.readSort("test*", Arrays.asList("tester*"), SortOrder.DESC);
        assertThat(descSort).containsExactly("obj3", "obj2", "obj1");

        Collection<String> ascSort = set.readSort("test*", Arrays.asList("tester*"), SortOrder.ASC);
        assertThat(ascSort).containsExactly("obj1", "obj2", "obj3");
    }
    
    @Test
    public void testSortOrderByPatternGetLimit() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("list", StringCodec.INSTANCE);
        set.add(10, 1);
        set.add(9, 2);
        set.add(8, 3);
        
        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(1);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(2);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(3);
        
        redisson.getBucket("tester1", StringCodec.INSTANCE).set("obj1");
        redisson.getBucket("tester2", StringCodec.INSTANCE).set("obj2");
        redisson.getBucket("tester3", StringCodec.INSTANCE).set("obj3");
        
        Collection<String> descSort = set.readSort("test*", Arrays.asList("tester*"), SortOrder.DESC, 1, 2);
        assertThat(descSort).containsExactly("obj2", "obj1");

        Collection<String> ascSort = set.readSort("test*", Arrays.asList("tester*"), SortOrder.ASC, 1, 2);
        assertThat(ascSort).containsExactly("obj2", "obj3");
    }

    @Test
    public void testSortOrderAlpha(){
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("list", StringCodec.INSTANCE);
        set.add(10,"1");
        set.add(9,"3");
        set.add(8,"12");

        assertThat(set.readSortAlpha(SortOrder.ASC))
                .containsExactly("1", "12", "3");
        assertThat(set.readSortAlpha(SortOrder.DESC))
                .containsExactly("3", "12", "1");
    }

    @Test
    public void testSortOrderLimitAlpha(){
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("list", StringCodec.INSTANCE);
        set.add(10,"1");
        set.add(9,"3");
        set.add(8,"12");

        assertThat(set.readSortAlpha(SortOrder.DESC, 0, 2))
                .containsExactly("3", "12");
        assertThat(set.readSortAlpha(SortOrder.DESC, 1, 2))
                .containsExactly("12", "1");
    }

    @Test
    public void testSortOrderByPatternAlpha(){
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("list", IntegerCodec.INSTANCE);
        set.add(10,1);
        set.add(9,2);
        set.add(8,3);

        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(12);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);

        Collection<Integer> descSort = set
                .readSortAlpha("test*", SortOrder.DESC);
        assertThat(descSort).containsExactly(2, 1, 3);

        Collection<Integer> ascSort = set
                .readSortAlpha("test*", SortOrder.ASC);
        assertThat(ascSort).containsExactly(3, 1, 2);
    }

    @Test
    public void testSortOrderByPatternAlphaLimit(){
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("list", IntegerCodec.INSTANCE);
        set.add(10,1);
        set.add(9, 2);
        set.add(8, 3);

        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(12);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);

        Collection<Integer> descSort = set
                .readSortAlpha("test*", SortOrder.DESC,1,2);
        assertThat(descSort).containsExactly(1, 3);

        Collection<Integer> ascSort = set
                .readSortAlpha("test*", SortOrder.ASC,1,2);
        assertThat(ascSort).containsExactly(1, 2);
    }

    @Test
    public void testSortOrderByPatternGetAlpha() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("list", StringCodec.INSTANCE);
        set.add(10,1);
        set.add(9, 2);
        set.add(8, 3);

        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(12);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);

        redisson.getBucket("tester1", StringCodec.INSTANCE).set("obj1");
        redisson.getBucket("tester2", StringCodec.INSTANCE).set("obj2");
        redisson.getBucket("tester3", StringCodec.INSTANCE).set("obj3");

        Collection<String> descSort = set
                .readSortAlpha("test*", Arrays.asList("tester*"), SortOrder.DESC);
        assertThat(descSort).containsExactly("obj2", "obj1", "obj3");

        Collection<String> ascSort = set
                .readSortAlpha("test*", Arrays.asList("tester*"), SortOrder.ASC);
        assertThat(ascSort).containsExactly("obj3", "obj1", "obj2");
    }

    @Test
    public void testSortOrderByPatternGetAlphaLimit() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("list", StringCodec.INSTANCE);
        set.add(10,1);
        set.add(9, 2);
        set.add(8, 3);

        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(12);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);

        redisson.getBucket("tester1", StringCodec.INSTANCE).set("obj1");
        redisson.getBucket("tester2", StringCodec.INSTANCE).set("obj2");
        redisson.getBucket("tester3", StringCodec.INSTANCE).set("obj3");

        Collection<String> descSort = set
                .readSortAlpha("test*", Arrays.asList("tester*"), SortOrder.DESC,1,2);
        assertThat(descSort).containsExactly("obj1", "obj3");

        Collection<String> ascSort = set
                .readSortAlpha("test*", Arrays.asList("tester*"), SortOrder.ASC,1, 2);
        assertThat(ascSort).containsExactly("obj1", "obj2");
    }

    @Test
    public void testSortTo() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("list", IntegerCodec.INSTANCE);
        set.add(10, 1);
        set.add(9, 2);
        set.add(8, 3);
        
        assertThat(set.sortTo("test3", SortOrder.DESC)).isEqualTo(3);
        RList<String> list2 = redisson.getList("test3", StringCodec.INSTANCE);
        assertThat(list2).containsExactly("3", "2", "1");
        
        assertThat(set.sortTo("test4", SortOrder.ASC)).isEqualTo(3);
        RList<String> list3 = redisson.getList("test4", StringCodec.INSTANCE);
        assertThat(list3).containsExactly("1", "2", "3");

    }

    @Test
    public void testSortToLimit() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("list", IntegerCodec.INSTANCE);
        set.add(10, 1);
        set.add(9, 2);
        set.add(8, 3);
        
        assertThat(set.sortTo("test3", SortOrder.DESC, 1, 2)).isEqualTo(2);
        RList<String> list2 = redisson.getList("test3", StringCodec.INSTANCE);
        assertThat(list2).containsExactly("2", "1");
        
        assertThat(set.sortTo("test4", SortOrder.ASC, 1, 2)).isEqualTo(2);
        RList<String> list3 = redisson.getList("test4", StringCodec.INSTANCE);
        assertThat(list3).containsExactly("2", "3");
    }

    @Test
    public void testSortToByPattern() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("list", IntegerCodec.INSTANCE);
        set.add(10, 1);
        set.add(9, 2);
        set.add(8, 3);
        
        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(2);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);
        
        assertThat(set.sortTo("tester3", "test*", SortOrder.DESC, 1, 2)).isEqualTo(2);
        RList<String> list2 = redisson.getList("tester3", StringCodec.INSTANCE);
        assertThat(list2).containsExactly("2", "3");
        
        assertThat(set.sortTo("tester4", "test*", SortOrder.ASC, 1, 2)).isEqualTo(2);
        RList<String> list3 = redisson.getList("tester4", StringCodec.INSTANCE);
        assertThat(list3).containsExactly("2", "1");
    }

    
    @Test
    public void testCount() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(0, "1");
        set.add(1, "4");
        set.add(2, "2");
        set.add(3, "5");
        set.add(4, "3");
        
        assertThat(set.count(0, true, 3, false)).isEqualTo(3);
    }
    
    @Test
    public void testReadAll() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(0, "1");
        set.add(1, "4");
        set.add(2, "2");
        set.add(3, "5");
        set.add(4, "3");

        assertThat(set.readAll()).containsOnly("1", "2", "4", "5", "3");
    }
    
    @Test
    public void testAddAll() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        Map<String, Double> objects = new HashMap<>();
        objects.put("1", 0.1);
        objects.put("2", 0.2);
        objects.put("3", 0.3);
        assertThat(set.addAll(objects)).isEqualTo(3);
        assertThat(set.entryRange(0, -1)).containsOnly(
                new ScoredEntry<>(0.1, "1"), new ScoredEntry<>(0.2, "2"), new ScoredEntry<>(0.3, "3"));
    }

    @Test
    public void testAddAllIfAbsent() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(10, "1981");
        set.add(11, "1984");

        Map<String, Double> map = new HashMap<>();
        map.put("1981", 111D);
        map.put("1982", 112D);
        map.put("1983", 113D);
        map.put("1984", 114D);

        assertThat(set.addAllIfAbsent(map)).isEqualTo(2);
        assertThat(set.getScore("1981")).isEqualTo(10);
        assertThat(set.getScore("1984")).isEqualTo(11);
        assertThat(set).contains("1981", "1982", "1983", "1984");
    }

    @Test
    public void testAddAllIfExist() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(10, "1981");
        set.add(11, "1984");

        Map<String, Double> map = new HashMap<>();
        map.put("1981", 111D);
        map.put("1982", 112D);
        map.put("1983", 113D);
        map.put("1984", 114D);

        assertThat(set.addAllIfExist(map)).isEqualTo(2);
        assertThat(set.getScore("1981")).isEqualTo(111D);
        assertThat(set.getScore("1984")).isEqualTo(114D);
    }

    @Test
    public void testAddAllIfGreater() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(10, "1981");
        set.add(11, "1984");
        set.add(13, "1985");

        Map<String, Double> map = new HashMap<>();
        map.put("1981", 111D);
        map.put("1982", 112D);
        map.put("1983", 113D);
        map.put("1984", 8D);
        map.put("1985", 3D);

        assertThat(set.addAllIfGreater(map)).isEqualTo(3);
        assertThat(set.size()).isEqualTo(5);
        assertThat(set.getScore("1981")).isEqualTo(111D);
        assertThat(set.getScore("1982")).isEqualTo(112D);
        assertThat(set.getScore("1983")).isEqualTo(113D);
        assertThat(set.getScore("1984")).isEqualTo(11D);
        assertThat(set.getScore("1985")).isEqualTo(13D);
    }

    @Test
    public void testAddAllIfLess() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(10D, "1981");
        set.add(11D, "1984");
        set.add(13D, "1985");

        Map<String, Double> map = new HashMap<>();
        map.put("1981", 111D);
        map.put("1982", 112D);
        map.put("1983", 113D);
        map.put("1984", 8D);
        map.put("1985", 3D);

        assertThat(set.addAllIfLess(map)).isEqualTo(4);
        assertThat(set.size()).isEqualTo(5);
        assertThat(set.getScore("1981")).isEqualTo(10D);
        assertThat(set.getScore("1982")).isEqualTo(112D);
        assertThat(set.getScore("1983")).isEqualTo(113D);
        assertThat(set.getScore("1984")).isEqualTo(8D);
        assertThat(set.getScore("1985")).isEqualTo(3D);
    }

    @Test
    public void testAddIfGreater() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(123, "1980");
        assertThat(set.addIfGreater(120, "1980")).isFalse();
        assertThat(set.getScore("1980")).isEqualTo(123);
        assertThat(set.addIfGreater(125, "1980")).isTrue();
        assertThat(set.getScore("1980")).isEqualTo(125);
    }

    @Test
    public void testAddIfLess() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(123, "1980");
        assertThat(set.addIfLess(120, "1980")).isTrue();
        assertThat(set.getScore("1980")).isEqualTo(120);
        assertThat(set.addIfLess(125, "1980")).isFalse();
        assertThat(set.getScore("1980")).isEqualTo(120);
    }

    @Test
    public void testAddIfExists() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        assertThat(set.addIfExists(123.81, "1980")).isFalse();
        assertThat(set.getScore("1980")).isNull();
        set.add(111, "1980");
        assertThat(set.addIfExists(32, "1980")).isTrue();
        assertThat(set.getScore("1980")).isEqualTo(32);
    }

    @Test
    public void testTryAdd() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        assertThat(set.tryAdd(123.81, "1980")).isTrue();
        assertThat(set.tryAdd(99, "1980")).isFalse();
        assertThat(set.getScore("1980")).isEqualTo(123.81);
    }

    @Test
    public void testPollLast() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        Assertions.assertNull(set.pollLast());

        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");

        Assertions.assertEquals("c", set.pollLast());
        assertThat(set).containsExactly("a", "b");
    }
    
    @Test
    public void testPollLastAmount() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        assertThat(set.pollLast(2)).isEmpty();

        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");

        assertThat(set.pollLast(2)).containsExactly("b", "c");
        assertThat(set).containsExactly("a");
    }
    
    @Test
    public void testPollLastTimeout() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        assertThat(set.pollLast(1, TimeUnit.SECONDS)).isNull();

        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");

        assertThat(set.pollLast(1, TimeUnit.SECONDS)).isEqualTo("c");
        assertThat(set).containsExactly("a", "b");
    }

    @Test
    public void testPollFirstTimeout() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        assertThat(set.pollFirst(1, TimeUnit.SECONDS)).isNull();

        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");

        assertThat(set.pollFirst(1, TimeUnit.SECONDS)).isEqualTo("a");
        assertThat(set).containsExactly("b", "c");
    }

    @Test
    public void testPollFirstTimeoutCount() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        assertThat(set.pollFirst(1, TimeUnit.SECONDS)).isNull();

        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");
        set.add(0.4, "d");
        set.add(0.5, "e");
        set.add(0.6, "f");

        assertThat(set.pollFirst(Duration.ofSeconds(2), 2)).containsExactly("a", "b");
        assertThat(set).containsExactly("c", "d", "e", "f");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        assertThat(set2.pollFirst(Duration.ofSeconds(1), 2)).isEmpty();
    }

    @Test
    public void testPollLastTimeoutCount() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        assertThat(set.pollFirst(1, TimeUnit.SECONDS)).isNull();

        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");
        set.add(0.4, "d");
        set.add(0.5, "e");
        set.add(0.6, "f");

        assertThat(set.pollLast(Duration.ofSeconds(2), 2)).containsExactly("f", "e");
        assertThat(set).containsExactly("a", "b", "c", "d");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        assertThat(set2.pollLast(Duration.ofSeconds(1), 2)).isEmpty();
    }

    @Test
    public void testPollFistAmount() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        assertThat(set.pollFirst(2)).isEmpty();

        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");

        assertThat(set.pollFirst(2)).containsExactly("a", "b");
        assertThat(set).containsExactly("c");
    }
    
    @Test
    public void testPollFirst() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        Assertions.assertNull(set.pollFirst());

        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");

        Assertions.assertEquals("a", set.pollFirst());
        assertThat(set).containsExactly("b", "c");
    }

    @Test
    public void testPollFirstEntries() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        Assertions.assertNull(set.pollFirst());

        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");

        List<ScoredEntry<String>> r = set.pollFirstEntries(2);
        assertThat(r).containsExactlyInAnyOrder(new ScoredEntry<>(0.1, "a"), new ScoredEntry<>(0.2, "b"));
    }

    @Test
    public void testFirstLast() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");
        set.add(0.4, "d");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        assertThat(set2.first()).isNull();
        assertThat(set2.last()).isNull();
        Assertions.assertEquals("a", set.first());
        Assertions.assertEquals("d", set.last());
    }
    
    @Test
    public void testFirstLastScore() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");
        set.add(0.4, "d");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        assertThat(set2.firstScore()).isNull();
        assertThat(set2.lastScore()).isNull();
        assertThat(set.firstScore()).isEqualTo(0.1);
        assertThat(set.lastScore()).isEqualTo(0.4);
    }

    @Test
    public void testRemoveRangeByScore() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");
        set.add(0.4, "d");
        set.add(0.5, "e");
        set.add(0.6, "f");
        set.add(0.7, "g");

        Assertions.assertEquals(2, set.removeRangeByScore(0.1, false, 0.3, true));
        assertThat(set).containsExactly("a", "d", "e", "f", "g");
    }

    @Test
    public void testRemoveRangeByScoreNegativeInf() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");
        set.add(0.4, "d");
        set.add(0.5, "e");
        set.add(0.6, "f");
        set.add(0.7, "g");

        Assertions.assertEquals(3, set.removeRangeByScore(Double.NEGATIVE_INFINITY, false, 0.3, true));
        assertThat(set).containsExactly("d", "e", "f", "g");
    }
    
    @Test
    public void testRemoveRangeByScorePositiveInf() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");
        set.add(0.4, "d");
        set.add(0.5, "e");
        set.add(0.6, "f");
        set.add(0.7, "g");

        Assertions.assertEquals(3, set.removeRangeByScore(0.4, false, Double.POSITIVE_INFINITY, true));
        assertThat(set).containsExactly("a", "b", "c", "d");
    }

    @Test
    public void testRemoveRangeByRank() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");
        set.add(0.4, "d");
        set.add(0.5, "e");
        set.add(0.6, "f");
        set.add(0.7, "g");

        Assertions.assertEquals(2, set.removeRangeByRank(0, 1));
        assertThat(set).containsExactly("c", "d", "e", "f", "g");
    }

    @Test
    public void testRank() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");
        set.add(0.4, "d");
        set.add(0.5, "e");
        set.add(0.6, "f");
        set.add(0.7, "g");

        assertThat(set.revRank("d")).isEqualTo(3);
        assertThat(set.revRank(Arrays.asList("d", "a", "g", "abc", "f"))).isEqualTo(Arrays.asList(3, 6, 0, null, 1));
        assertThat(set.rank("abc")).isNull();
    }
    
    @Test
    public void testRevRank() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");
        set.add(0.4, "d");
        set.add(0.5, "e");
        set.add(0.6, "f");
        set.add(0.7, "g");

        assertThat(set.revRank("f")).isEqualTo(1);
        assertThat(set.revRank("abc")).isNull();
    }


    @Test
    public void testAddAsync() throws InterruptedException, ExecutionException {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        RFuture<Boolean> future = set.addAsync(0.323, 2);
        Assertions.assertTrue(future.get());
        RFuture<Boolean> future2 = set.addAsync(0.323, 2);
        Assertions.assertFalse(future2.get());

        Assertions.assertTrue(set.contains(2));
    }

    @Test
    public void testAddAndGetRank() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        Integer res = set.addAndGetRank(0.3, 1);
        assertThat(res).isEqualTo(0);
        Integer res2 = set.addAndGetRank(0.4, 2);
        assertThat(res2).isEqualTo(1);
        Integer res3 = set.addAndGetRank(0.2, 3);
        assertThat(res3).isEqualTo(0);

        Assertions.assertTrue(set.contains(3));
    }

    @Test
    public void testAddAndGetRevRank() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        Integer res = set.addAndGetRevRank(0.3, 1);
        assertThat(res).isEqualTo(0);
        Integer res2 = set.addAndGetRevRank(0.4, 2);
        assertThat(res2).isEqualTo(0);
        Integer res3  = set.addAndGetRevRank(0.2, 3);
        assertThat(res3).isEqualTo(2);

        Assertions.assertTrue(set.contains(3));
    }

    @Test
    public void testRemoveAsync() throws InterruptedException, ExecutionException {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        set.add(0.11, 1);
        set.add(0.22, 3);
        set.add(0.33, 7);

        Assertions.assertTrue(set.removeAsync(1).get());
        Assertions.assertFalse(set.contains(1));
        assertThat(set).containsExactly(3, 7);

        Assertions.assertFalse(set.removeAsync(1).get());
        assertThat(set).containsExactly(3, 7);

        set.removeAsync(3).get();
        Assertions.assertFalse(set.contains(3));
        assertThat(set).containsExactly(7);
    }

    @Test
    public void testIteratorNextNext() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(1, "1");
        set.add(2, "4");

        Iterator<String> iter = set.iterator();
        Assertions.assertEquals("1", iter.next());
        Assertions.assertEquals("4", iter.next());
        Assertions.assertFalse(iter.hasNext());
    }

    @Test
    public void testIteratorRemove() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(1, "1");
        set.add(2, "4");
        set.add(3, "2");
        set.add(4, "5");
        set.add(5, "3");

        for (Iterator<String> iterator = set.iterator(); iterator.hasNext();) {
            String value = iterator.next();
            if (value.equals("2")) {
                iterator.remove();
            }
        }

        assertThat(set).containsExactly("1", "4", "5", "3");

        int iteration = 0;
        for (Iterator<String> iterator = set.iterator(); iterator.hasNext();) {
            iterator.next();
            iterator.remove();
            iteration++;
        }

        Assertions.assertEquals(4, iteration);

        Assertions.assertEquals(0, set.size());
        Assertions.assertTrue(set.isEmpty());
    }

    @Test
    public void testIteratorSequence() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        for (int i = 0; i < 1000; i++) {
            set.add(i, Integer.valueOf(i));
        }

        Set<Integer> setCopy = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            setCopy.add(Integer.valueOf(i));
        }

        checkIterator(set, setCopy);
    }

    private void checkIterator(RScoredSortedSet<Integer> set, Set<Integer> setCopy) {
        for (Integer value : set) {
            if (!setCopy.remove(value)) {
                Assertions.fail();
            }
        }

        assertThat(setCopy.size()).isEqualTo(0);
    }

    @Test
    public void testRetainAll() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        for (int i = 0; i < 20000; i++) {
            set.add(i*10, i);
        }

        assertThat(set.retainAll(Arrays.asList(1, 2))).isTrue();
        assertThat(set).containsExactly(1, 2);
        assertThat(set.size()).isEqualTo(2);
        assertThat(set.getScore(1)).isEqualTo(10);
        assertThat(set.getScore(2)).isEqualTo(20);
    }

    @Test
    public void testRemoveAll() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        set.add(0.1, 1);
        set.add(0.2, 2);
        set.add(0.3, 3);

        Assertions.assertTrue(set.removeAll(Arrays.asList(1, 2)));
        assertThat(set).containsOnly(3);
        Assertions.assertEquals(1, set.size());
    }


//    @Test(expected = IllegalArgumentException.class)
    public void testTailSet() {
        RSortedSet<Integer> set = redisson.getSortedSet("set");

        set.add(1);
        set.add(2);
        set.add(3);
        set.add(4);
        set.add(5);

        SortedSet<Integer> hs = set.tailSet(3);
        hs.add(10);

        assertThat(hs).containsExactly(3, 4, 5, 10);

        set.remove(4);

        assertThat(hs).containsExactly(3, 5, 10);

        set.remove(3);

        assertThat(hs).containsExactly(5, 10);

        hs.add(-1);
    }


//    @Test(expected = IllegalArgumentException.class)
    public void testHeadSet() {
        RSortedSet<Integer> set = redisson.getSortedSet("set");

        set.add(1);
        set.add(2);
        set.add(3);
        set.add(4);
        set.add(5);

        SortedSet<Integer> hs = set.headSet(3);
        hs.add(0);

        assertThat(hs).containsExactly(0, 1, 2);

        set.remove(2);

        assertThat(hs).containsExactly(0, 1);

        set.remove(3);

        assertThat(hs).containsExactly(0, 1);

        hs.add(7);
    }

    @Test
    public void testTailSetTreeSet() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            TreeSet<Integer> set = new TreeSet<>();

            set.add(1);
            set.add(2);
            set.add(3);
            set.add(4);
            set.add(5);

            SortedSet<Integer> hs = set.tailSet(3);
            hs.add(10);

            assertThat(hs).containsExactly(3, 4, 5, 10);

            set.remove(4);

            assertThat(hs).containsExactly(3, 5, 10);

            set.remove(3);

            assertThat(hs).containsExactly(5, 10);

            hs.add(-1);
        });
    }

    @Test
    public void testHeadSetTreeSet() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            TreeSet<Integer> set = new TreeSet<>();

            set.add(1);
            set.add(2);
            set.add(3);
            set.add(4);
            set.add(5);

            SortedSet<Integer> hs = set.headSet(3);
            hs.add(0);

            assertThat(hs).containsExactly(0, 1, 2);

            set.remove(2);

            assertThat(hs).containsExactly(0, 1);

            set.remove(3);

            assertThat(hs).containsExactly(0, 1);

            hs.add(7);
        });
    }

    @Test
    public void testSort() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        Assertions.assertTrue(set.add(4, 2));
        Assertions.assertTrue(set.add(5, 3));
        Assertions.assertTrue(set.add(3, 1));
        Assertions.assertTrue(set.add(6, 4));
        Assertions.assertTrue(set.add(1000, 10));
        Assertions.assertTrue(set.add(1, -1));
        Assertions.assertTrue(set.add(2, 0));

        assertThat(set).containsExactly(-1, 0, 1, 2, 3, 4, 10);
    }

    @Test
    public void testRemove() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        set.add(4, 5);
        set.add(2, 3);
        set.add(0, 1);
        set.add(1, 2);
        set.add(3, 4);

        Assertions.assertFalse(set.remove(0));
        Assertions.assertTrue(set.remove(3));

        assertThat(set).containsExactly(1, 2, 4, 5);
    }

    @Test
    public void testContainsAll() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        for (int i = 0; i < 200; i++) {
            set.add(i, i);
        }

        Assertions.assertTrue(set.containsAll(Arrays.asList(30, 11)));
        Assertions.assertFalse(set.containsAll(Arrays.asList(30, 711, 11)));
    }

    @Test
    public void testToArray() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(0, "1");
        set.add(1, "4");
        set.add(2, "2");
        set.add(3, "5");
        set.add(4, "3");

        assertThat(Arrays.asList(set.toArray())).containsExactly("1", "4", "2", "5", "3");

        String[] strs = set.toArray(new String[0]);
        assertThat(Arrays.asList(strs)).containsExactly("1", "4", "2", "5", "3");
    }

    @Test
    public void testContains() {
        RScoredSortedSet<TestObject> set = redisson.getScoredSortedSet("simple");

        set.add(0, new TestObject("1", "2"));
        set.add(1, new TestObject("1", "2"));
        set.add(2, new TestObject("2", "3"));
        set.add(3, new TestObject("3", "4"));
        set.add(4, new TestObject("5", "6"));

        Assertions.assertTrue(set.contains(new TestObject("2", "3")));
        Assertions.assertTrue(set.contains(new TestObject("1", "2")));
        Assertions.assertFalse(set.contains(new TestObject("1", "9")));
    }

    @Test
    public void testDuplicates() {
        RScoredSortedSet<TestObject> set = redisson.getScoredSortedSet("simple");

        Assertions.assertTrue(set.add(0, new TestObject("1", "2")));
        Assertions.assertFalse(set.add(0, new TestObject("1", "2")));
        Assertions.assertTrue(set.add(2, new TestObject("2", "3")));
        Assertions.assertTrue(set.add(3, new TestObject("3", "4")));
        Assertions.assertTrue(set.add(4, new TestObject("5", "6")));

        Assertions.assertEquals(4, set.size());
    }

    @Test
    public void testSize() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        set.add(0, 1);
        set.add(1, 2);
        set.add(2, 3);
        set.add(2, 3);
        set.add(3, 4);
        set.add(4, 5);
        set.add(4, 5);

        Assertions.assertEquals(5, set.size());
    }

    @Test
    public void testValueRange() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        set.add(0, 1);
        set.add(1, 2);
        set.add(2, 3);
        set.add(3, 4);
        set.add(4, 5);
        set.add(4, 5);

        Collection<Integer> vals = set.valueRange(0, -1);
        assertThat(vals).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    public void testValueRangeReversedInfinity() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        set.add(1, 1);
        set.add(2, 2);
        set.valueRangeReversed(Double.POSITIVE_INFINITY, true, Double.NEGATIVE_INFINITY, true, 0, 1);
    }

    @Test
    public void testValueRangeReversed() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        set.add(0, 1);
        set.add(1, 2);
        set.add(2, 3);
        set.add(3, 4);
        set.add(4, 5);
        set.add(4, 5);

        Collection<Integer> vals = set.valueRangeReversed(0, -1);
        assertThat(vals).containsExactly(5, 4, 3, 2, 1);
    }


    @Test
    public void testEntryRange() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        set.add(10, 1);
        set.add(20, 2);
        set.add(30, 3);
        set.add(40, 4);
        set.add(50, 5);

        Collection<ScoredEntry<Integer>> vals = set.entryRange(0, -1);
        assertThat(vals).containsExactly(new ScoredEntry<>(10D, 1),
                new ScoredEntry<>(20D, 2),
                new ScoredEntry<>(30D, 3),
                new ScoredEntry<>(40D, 4),
                new ScoredEntry<>(50D, 5));
    }
    
    @Test
    public void testEntryRangeReversed() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        set.add(10, 1);
        set.add(20, 2);
        set.add(30, 3);
        set.add(40, 4);
        set.add(50, 5);

        Collection<ScoredEntry<Integer>> vals = set.entryRangeReversed(0, -1);
        assertThat(vals).containsExactly(
                new ScoredEntry<>(50D, 5),
                new ScoredEntry<>(40D, 4),
                new ScoredEntry<>(30D, 3),
                new ScoredEntry<>(20D, 2),
                new ScoredEntry<>(10D, 1)
                );
    }


    @Test
    public void testLexSortedSet() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");

        set.add("a");
        set.add("b");
        set.add("c");
        set.add("d");
        set.add("e");

        Collection<String> r = set.range("b", true, "e", false, 1, 2);
        String[] a = r.toArray(new String[0]);
        Assertions.assertArrayEquals(new String[]{"c", "d"}, a);
    }

    @Test
    public void testValueRangeLimit() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<String> r = set.valueRange(1, true, 4, false, 1, 2);
        assertThat(r).containsExactly("c", "d");
    }

    @Test
    public void testValueRangeAlpha() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<String> r = set.valueRange(1, true, 4, false);
        assertThat(r).containsExactly("b", "c", "d");
    }

    @Test
    public void testValueRangeReversedLimit() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<String> r = set.valueRangeReversed(1, true, 4, false, 1, 2);
        assertThat(r).containsExactly("c", "b");
    }

    @Test
    public void testValueRangeReversedAlpha() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<String> r = set.valueRangeReversed(1, true, 4, false);
        assertThat(r).containsExactly("d", "c", "b");
    }
    
    @Test
    public void testValueRangeNegativeInf() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<String> r = set.valueRange(Double.NEGATIVE_INFINITY, true, 4, false, 1, 2);
        String[] a = r.toArray(new String[0]);
        Assertions.assertArrayEquals(new String[]{"b", "c"}, a);
    }
    
    @Test
    public void testValueRangePositiveInf() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<String> r = set.valueRange(1, true, Double.POSITIVE_INFINITY, false, 1, 2);
        String[] a = r.toArray(new String[0]);
        Assertions.assertArrayEquals(new String[]{"c", "d"}, a);
    }

    @Test
    public void testEntryRangeAlpha() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<ScoredEntry<String>> r = set.entryRange(1, true, 4, false, 1, 2);
        Assertions.assertEquals(2, r.size());
        ScoredEntry<String>[] a = r.toArray(new ScoredEntry[0]);
        Assertions.assertEquals(2d, a[0].getScore(), 0);
        Assertions.assertEquals(3d, a[1].getScore(), 0);
        Assertions.assertEquals("c", a[0].getValue());
        Assertions.assertEquals("d", a[1].getValue());
    }

    @Test
    public void testEntryRangeReversedAlpha() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<ScoredEntry<String>> r = set.entryRangeReversed(1, true, 4, false, 1, 2);
        Assertions.assertEquals(2, r.size());
        ScoredEntry<String>[] a = r.toArray(new ScoredEntry[0]);
        Assertions.assertEquals(2d, a[0].getScore(), 0);
        Assertions.assertEquals(1d, a[1].getScore(), 0);
        Assertions.assertEquals("c", a[0].getValue());
        Assertions.assertEquals("b", a[1].getValue());
    }
    
    @Test
    public void testEntryRangeNegativeInf() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<ScoredEntry<String>> r = set.entryRange(Double.NEGATIVE_INFINITY, true, 4, false, 1, 2);
        ScoredEntry<String>[] a = r.toArray(new ScoredEntry[0]);
        Assertions.assertEquals(1d, a[0].getScore(), 0);
        Assertions.assertEquals(2d, a[1].getScore(), 0);
        Assertions.assertEquals("b", a[0].getValue());
        Assertions.assertEquals("c", a[1].getValue());
    }
    
    @Test
    public void testEntryRangePositiveInf() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<ScoredEntry<String>> r = set.entryRange(1, true, Double.POSITIVE_INFINITY, false, 1, 2);
        ScoredEntry<String>[] a = r.toArray(new ScoredEntry[0]);
        Assertions.assertEquals(2d, a[0].getScore(), 0);
        Assertions.assertEquals(3d, a[1].getScore(), 0);
        Assertions.assertEquals("c", a[0].getValue());
        Assertions.assertEquals("d", a[1].getValue());
    }
    
    @Test
    public void testAddAndGet() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(1, "100");

        Double res = set.addScore("100", 11);
        Assertions.assertEquals(12, res, 0);
        Double score = set.getScore("100");
        Assertions.assertEquals(12, score, 0);

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple");
        set2.add(100.2, "1");

        Double res2 = set2.addScore("1", 12.1);
        assertThat(res2).isEqualTo(112.3);
        res2 = set2.getScore("1");
        assertThat(res2).isEqualTo(112.3);
    }

    @Test
    public void testAddAndGetAll() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(100.2, "1");

        Double res2 = set.addScore("1", 12.1);
        assertThat(res2).isEqualTo(112.3);
        res2 = set.getScore("1");
        assertThat(res2).isEqualTo(112.3);

        Collection<Double> res = set.getScore(Arrays.asList("1", "42", "100"));
        Assertions.assertArrayEquals(new Double[] {112.3d, null, null},
                res.toArray());
    }
    
    @Test
    public void testAddScoreAndGetRank() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        Integer res1 = set.addScoreAndGetRank("12", 12);
        assertThat(res1).isEqualTo(0);
        Integer res2 = set.addScoreAndGetRank("15", 10);
        assertThat(res2).isEqualTo(0);
        
        assertThat(set.rank("12")).isEqualTo(1);
        assertThat(set.rank("15")).isEqualTo(0);
        
        Integer res3 = set.addScoreAndGetRank("12", 2);
        assertThat(res3).isEqualTo(1);
        Double score = set.getScore("12");
        assertThat(score).isEqualTo(14);
    }
    
    @Test
    public void testAddScoreAndGetRevRank() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        Integer res1 = set.addScoreAndGetRevRank("12", 12);
        assertThat(res1).isEqualTo(0);
        Integer res2 = set.addScoreAndGetRevRank("15", 10);
        assertThat(res2).isEqualTo(1);
        
        assertThat(set.revRank("12")).isEqualTo(0);
        assertThat(set.revRank("15")).isEqualTo(1);
        
        Integer res3 = set.addScoreAndGetRevRank("12", 2);
        assertThat(res3).isEqualTo(0);
        Integer res4 = set.addScoreAndGetRevRank("15", -1);
        assertThat(res4).isEqualTo(1);
        Double score = set.getScore("12");
        assertThat(score).isEqualTo(14);
    }

    @Test
    public void testAddAndGetRevRankCollection() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple", StringCodec.INSTANCE);
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("one", 1d);
        map.put("three", 3d);
        map.put("two", 2d);
        Collection<Integer> res = set.addAndGetRevRank(map);
        Assertions.assertArrayEquals(new Integer[]{2, 0, 1}, res.toArray());

        assertThat(set.revRank("one")).isEqualTo(2);
        assertThat(set.revRank("two")).isEqualTo(1);
        assertThat(set.revRank("three")).isEqualTo(0);
    }

    @Test
    public void testReadIntersection() {
        RScoredSortedSet<String> set1 = redisson.getScoredSortedSet("simple1");
        set1.add(1, "one");
        set1.add(2, "two");
        set1.add(2, "four");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        set2.add(1, "one");
        set2.add(2, "two");
        set2.add(3, "three");

        RScoredSortedSet<String> out = redisson.getScoredSortedSet("simple1");
        assertThat(out.readIntersection(set1.getName(), set2.getName())).containsOnly("one", "two");
    }

    @Test
    public void testReadIntersection2() {
        RScoredSortedSet<String> set1 = redisson.getScoredSortedSet("simple1");
        set1.add(1, "one");
        set1.add(2, "two");
        set1.add(2, "four");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        set2.add(1, "one");
        set2.add(2, "two");
        set2.add(3, "three");

        RScoredSortedSet<String> out = redisson.getScoredSortedSet("simple1");
        assertThat(out.readIntersection(SetIntersectionArgs.names(set2.getName()))).containsOnly("one", "two");
    }
    
    @Test
    public void testReadIntersectionEntries() {
        RScoredSortedSet<String> set1 = redisson.getScoredSortedSet("simple1");
        set1.add(1, "one");
        set1.add(2, "two");
        set1.add(2, "four");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        set2.add(1, "one");
        set2.add(3, "two");
        set2.add(3, "three");

        RScoredSortedSet<String> out = redisson.getScoredSortedSet("simple1");
        assertThat(out.readIntersectionEntries(SetIntersectionArgs.names(set2.getName())))
                .containsOnly(new ScoredEntry<>(2D, "one"), new ScoredEntry<>(5D, "two"));

        assertThat(out.readIntersectionEntries((SetIntersectionArgs) SetIntersectionArgs.names(set2.getName()).aggregate(RScoredSortedSet.Aggregate.MAX)))
                .containsOnly(new ScoredEntry<>(1D, "one"), new ScoredEntry<>(3D, "two"));

        assertThat(out.readIntersectionEntries((SetIntersectionArgs) SetIntersectionArgs.names(set2.getName()).weights(2D, 3D)))
                .containsOnly(new ScoredEntry<>(5D, "one"), new ScoredEntry<>(13D, "two"));

    }

    @Test
    public void testIntersection() {
        RScoredSortedSet<String> set1 = redisson.getScoredSortedSet("simple1");
        set1.add(1, "one");
        set1.add(2, "two");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        set2.add(1, "one");
        set2.add(2, "two");
        set2.add(3, "three");
        
        RScoredSortedSet<String> out = redisson.getScoredSortedSet("out");
        assertThat(out.intersection(set1.getName(), set2.getName())).isEqualTo(2);

        assertThat(out.readAll()).containsOnly("one", "two");
        assertThat(out.getScore("one")).isEqualTo(2);
        assertThat(out.getScore("two")).isEqualTo(4);
    }
    
    @Test
    public void testIntersection2() {
        RScoredSortedSet<String> set1 = redisson.getScoredSortedSet("simple1");
        set1.add(1, "one");
        set1.add(2, "two");
        
        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        set2.add(3, "three");
        
        RScoredSortedSet<String> out = redisson.getScoredSortedSet("out");
        assertThat(out.intersection(SetIntersectionArgs.names(set1.getName(), set2.getName()))).isEqualTo(0);
        
        assertThat(out.readAll()).isEmpty();
        
        set2.add(1, "one");
        set2.add(2, "two");
        
        assertThat(out.intersection(SetIntersectionArgs.names(set1.getName(), set2.getName()))).isEqualTo(2);
        
        assertThat(out.readAll()).containsOnly("one", "two");
        assertThat(out.getScore("one")).isEqualTo(2);
        assertThat(out.getScore("two")).isEqualTo(4);
        
        RScoredSortedSet<String> out2 = redisson.getScoredSortedSet("out2");
        assertThat(out2.intersection((SetIntersectionArgs) SetIntersectionArgs.names(set1.getName(), set2.getName())
                .weights(5D, 3D).aggregate(RScoredSortedSet.Aggregate.MIN))).isEqualTo(2);
        
        assertThat(out2.readAll()).containsOnly("one", "two");
        assertThat(out2.getScore("one")).isEqualTo(3);
        assertThat(out2.getScore("two")).isEqualTo(6);
    }
    
    @Test
    public void testIntersectionEmpty() {
        RScoredSortedSet<String> set1 = redisson.getScoredSortedSet("simple1");
        set1.add(1, "one");
        set1.add(2, "two");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        set2.add(3, "three");
        set2.add(4, "four");

        RScoredSortedSet<String> out = redisson.getScoredSortedSet("out");
        assertThat(out.intersection(set1.getName(), set2.getName())).isEqualTo(0);

        assertThat(out.readAll()).isEmpty();
    }

    @Test
    public void testAddListener() {
        testWithParams(redisson -> {
            RScoredSortedSet<Integer> ss = redisson.getScoredSortedSet("test");
            AtomicInteger latch = new AtomicInteger();
            int id = ss.addListener(new ScoredSortedSetAddListener() {
                @Override
                public void onAdd(String name) {
                    latch.incrementAndGet();
                }
            });
            ss.add(1, 1);

            Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
                assertThat(latch.get()).isEqualTo(1);
            });

            ss.removeListener(id);

            ss.add(1, 1);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            assertThat(latch.get()).isEqualTo(1);
        }, NOTIFY_KEYSPACE_EVENTS, "Ez");
    }


    @Test
    public void testIntersectionWithWeight() {
        RScoredSortedSet<String> set1 = redisson.getScoredSortedSet("simple1");
        set1.add(1, "one");
        set1.add(2, "two");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        set2.add(1, "one");
        set2.add(2, "two");
        set2.add(3, "three");
        
        RScoredSortedSet<String> out = redisson.getScoredSortedSet("out");
        Map<String, Double> nameWithWeight = new HashMap<>();
        nameWithWeight.put(set1.getName(), 2D);
        nameWithWeight.put(set2.getName(), 3D);
        assertThat(out.intersection(nameWithWeight)).isEqualTo(2);

        assertThat(out.readAll()).containsOnly("one", "two");
        assertThat(out.getScore("one")).isEqualTo(5);
        assertThat(out.getScore("two")).isEqualTo(10);
    }

    @Test
    public void testRangeTo() {
        RScoredSortedSet<Integer> set1 = redisson.getScoredSortedSet("simple1");
        for (int i = 0; i < 10; i++) {
            set1.add(i, i);

        }

        set1.rangeTo("simple2", 0, 3);
        RScoredSortedSet<Integer> set2 = redisson.getScoredSortedSet("simple2");
        assertThat(set2.readAll()).containsOnly(0, 1, 2, 3);
    }

    @Test
    public void testRevRange() {
        RScoredSortedSet<Integer> set1 = redisson.getScoredSortedSet("simple1");
        for (int i = 0; i < 10; i++) {
            set1.add(i, i);

        }

        set1.revRangeTo("simple2", 3, true, 0, false);
        RScoredSortedSet<Integer> set2 = redisson.getScoredSortedSet("simple2");
        assertThat(set2.readAll()).containsOnly(3, 2, 1);
    }

    @Test
    public void testRangeToScored() {
        RScoredSortedSet<Integer> set1 = redisson.getScoredSortedSet("simple1");
        for (int i = 0; i < 10; i++) {
            set1.add(i, i);

        }

        set1.rangeTo("simple2", 0, false, 3, true);
        RScoredSortedSet<Integer> set2 = redisson.getScoredSortedSet("simple2");
        assertThat(set2.readAll()).containsOnly(1, 2, 3);
    }

    @Test
    public void testReadUnion() {
        RScoredSortedSet<String> set1 = redisson.getScoredSortedSet("simple1");
        set1.add(1, "one");
        set1.add(2, "two");
        set1.add(4, "four");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        set2.add(1, "one");
        set2.add(2, "two");
        set2.add(3, "three");

        RScoredSortedSet<String> out = redisson.getScoredSortedSet("simple1");
        assertThat(out.readUnion(set1.getName(), set2.getName())).containsOnly("one", "two", "three", "four");
    }

    @Test
    public void testReadUnion2() {
        RScoredSortedSet<String> set1 = redisson.getScoredSortedSet("simple1");
        set1.add(1, "one");
        set1.add(2, "two");
        set1.add(4, "four");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        set2.add(1, "one");
        set2.add(2, "two");
        set2.add(3, "three");

        RScoredSortedSet<String> out = redisson.getScoredSortedSet("simple1");
        assertThat(out.readUnion(SetUnionArgs.names(set2.getName()))).containsOnly("one", "two", "three", "four");
    }

    @Test
    public void testReadUnionEntries() {
        RScoredSortedSet<String> set1 = redisson.getScoredSortedSet("simple1");
        set1.add(1, "one");
        set1.add(2, "two");
        set1.add(2, "four");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        set2.add(1, "one");
        set2.add(3, "two");
        set2.add(3, "three");

        RScoredSortedSet<String> out = redisson.getScoredSortedSet("simple1");
        assertThat(out.readUnionEntries(SetUnionArgs.names(set2.getName())))
                .containsOnly(new ScoredEntry<>(2D, "one"), new ScoredEntry<>(5D, "two"),
                        new ScoredEntry<>(3D, "three"), new ScoredEntry<>(2D, "four"));

        assertThat(out.readUnionEntries((SetUnionArgs) SetUnionArgs.names(set2.getName()).aggregate(RScoredSortedSet.Aggregate.MAX)))
                .containsOnly(new ScoredEntry<>(1D, "one"), new ScoredEntry<>(3D, "two"),
                        new ScoredEntry<>(3D, "three"), new ScoredEntry<>(2D, "four"));

        assertThat(out.readUnionEntries((SetUnionArgs) SetUnionArgs.names(set2.getName()).weights(2D, 3D)))
                .containsOnly(new ScoredEntry<>(5D, "one"), new ScoredEntry<>(13D, "two"),
                        new ScoredEntry<>(9D, "three"), new ScoredEntry<>(4D, "four"));

    }

    @Test
    public void testUnion() {
        RScoredSortedSet<String> set1 = redisson.getScoredSortedSet("simple1");
        set1.add(1, "one");
        set1.add(2, "two");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        set2.add(1, "one");
        set2.add(2, "two");
        set2.add(3, "three");
        
        RScoredSortedSet<String> out = redisson.getScoredSortedSet("out");
        assertThat(out.union(set1.getName(), set2.getName())).isEqualTo(3);

        assertThat(out.readAll()).containsOnly("one", "two", "three");
        assertThat(out.getScore("one")).isEqualTo(2);
        assertThat(out.getScore("two")).isEqualTo(4);
        assertThat(out.getScore("three")).isEqualTo(3);
    }
    
    @Test
    public void testUnion2() {
        RScoredSortedSet<String> set1 = redisson.getScoredSortedSet("simple1");
        set1.add(1, "one");
        set1.add(2, "two");
        
        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        set2.add(1, "one");
        set2.add(2, "two");
        set2.add(3, "three");
        
        RScoredSortedSet<String> out = redisson.getScoredSortedSet("out");
        assertThat(out.union(SetUnionArgs.names(set1.getName(), set2.getName()))).isEqualTo(3);
        
        assertThat(out.readAll()).containsOnly("one", "two", "three");
        assertThat(out.getScore("one")).isEqualTo(2);
        assertThat(out.getScore("two")).isEqualTo(4);
        assertThat(out.getScore("three")).isEqualTo(3);
        
        RScoredSortedSet<String> out2 = redisson.getScoredSortedSet("out2");
        assertThat(out2.union((SetUnionArgs) SetUnionArgs.names(set1.getName(), set2.getName())
                .weights(2D, 3D).aggregate(RScoredSortedSet.Aggregate.MAX))).isEqualTo(3);
        
        assertThat(out2.readAll()).containsOnly("one", "two", "three");
        assertThat(out2.getScore("one")).isEqualTo(3);
        assertThat(out2.getScore("two")).isEqualTo(6);
        assertThat(out2.getScore("three")).isEqualTo(9);
    }
    
    @Test
    public void testUnionWithWeight() {
        RScoredSortedSet<String> set1 = redisson.getScoredSortedSet("simple1");
        set1.add(1, "one");
        set1.add(2, "two");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        set2.add(1, "one");
        set2.add(2, "two");
        set2.add(3, "three");
        
        RScoredSortedSet<String> out = redisson.getScoredSortedSet("out");
        Map<String, Double> nameWithWeight = new HashMap<>();
        nameWithWeight.put(set1.getName(), 2D);
        nameWithWeight.put(set2.getName(), 3D);
        assertThat(out.union(nameWithWeight)).isEqualTo(3);

        assertThat(out.readAll()).containsOnly("one", "two", "three");
        assertThat(out.getScore("one")).isEqualTo(5);
        assertThat(out.getScore("two")).isEqualTo(10);
        assertThat(out.getScore("three")).isEqualTo(9);
    }

    @Test
    public void testDistributedIterator() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("set", StringCodec.INSTANCE);

        // populate set with elements
        Map<String, Double> stringsOne = IntStream.range(0, 128).boxed()
                .collect(Collectors.toMap(i -> "one-" + i, Integer::doubleValue));
        Map<String, Double> stringsTwo = IntStream.range(0, 128).boxed()
                .collect(Collectors.toMap(i -> "two-" + i, Integer::doubleValue));;
        set.addAll(stringsOne);
        set.addAll(stringsTwo);

        Iterator<String> stringIterator = set.distributedIterator("iterator_{set}", "one*", 10);

        // read some elements using iterator
        List<String> strings = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            if (stringIterator.hasNext()) {
                strings.add(stringIterator.next());
            }
        }

        // create another iterator instance using the same name
        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("set", StringCodec.INSTANCE);
        Iterator<String> stringIterator2 = set2.distributedIterator("iterator_{set}", "one*", 10);

        assertTrue(stringIterator2.hasNext());

        // read all remaining elements
        stringIterator2.forEachRemaining(strings::add);
        stringIterator.forEachRemaining(strings::add);

        assertThat(strings).containsAll(stringsOne.keySet());
        assertThat(strings).hasSize(stringsOne.size());
    }

    @Test
    public void testReadDiffEntries() {
        RScoredSortedSet<String> set1 = redisson.getScoredSortedSet("simple1");
        set1.add(1, "one");
        set1.add(2, "two");

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple2");
        set2.add(1, "one");
        set2.add(2, "two");
        set2.add(3, "three");

        Collection<ScoredEntry<String>> r = set2.readDiffEntries("simple1");

        assertThat(r).containsExactly(new ScoredEntry<>(3D, "three"));

        Collection<ScoredEntry<String>> r2 = set1.readDiffEntries("simple2");
        assertThat(r2).isEmpty();

        set1.add(3, "three");
        Collection<ScoredEntry<String>> r3 = set2.readDiffEntries("simple1");
        assertThat(r3).isEmpty();

    }
}
