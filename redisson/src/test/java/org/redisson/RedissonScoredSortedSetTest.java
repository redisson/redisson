package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.redisson.api.RFuture;
import org.redisson.api.RLexSortedSet;
import org.redisson.api.RList;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSortedSet;
import org.redisson.api.SortOrder;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.ScoredEntry;

public class RedissonScoredSortedSetTest extends BaseTest {

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
        Assert.assertEquals(1, l);
        Assert.assertTrue(System.currentTimeMillis() - s > 2000);
    }
    
    @Test
    public void testPollFirstFromAny() throws InterruptedException {
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

        Assert.assertEquals(2, l);
        Assert.assertTrue(System.currentTimeMillis() - s > 2000);
    }

    @Test
    public void testPollLastFromAny() throws InterruptedException {
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

        Assert.assertEquals(2, l);
        Assert.assertTrue(System.currentTimeMillis() - s > 2000);
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

        Map<String, Double> objects = new HashMap<String, Double>();
        objects.put("1", 0.1);
        objects.put("2", 0.2);
        objects.put("3", 0.3);
        assertThat(set.addAll(objects)).isEqualTo(3);
        assertThat(set.entryRange(0, -1)).containsOnly(
                new ScoredEntry<String>(0.1, "1"), new ScoredEntry<String>(0.2, "2"), new ScoredEntry<String>(0.3, "3"));
    }

    @Test
    public void testTryAdd() {
        Assume.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("3.0.2") >= 0);
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        assertThat(set.tryAdd(123.81, "1980")).isTrue();
        assertThat(set.tryAdd(99, "1980")).isFalse();
        assertThat(set.getScore("1980")).isEqualTo(123.81);
    }

    @Test
    public void testPollLast() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        Assert.assertNull(set.pollLast());

        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");

        Assert.assertEquals("c", set.pollLast());
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
        Assert.assertNull(set.pollFirst());

        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");

        Assert.assertEquals("a", set.pollFirst());
        assertThat(set).containsExactly("b", "c");
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
        Assert.assertEquals("a", set.first());
        Assert.assertEquals("d", set.last());
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

        Assert.assertEquals(2, set.removeRangeByScore(0.1, false, 0.3, true));
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

        Assert.assertEquals(3, set.removeRangeByScore(Double.NEGATIVE_INFINITY, false, 0.3, true));
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

        Assert.assertEquals(3, set.removeRangeByScore(0.4, false, Double.POSITIVE_INFINITY, true));
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

        Assert.assertEquals(2, set.removeRangeByRank(0, 1));
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
        Assert.assertTrue(future.get());
        RFuture<Boolean> future2 = set.addAsync(0.323, 2);
        Assert.assertFalse(future2.get());

        Assert.assertTrue(set.contains(2));
    }

    @Test
    public void testAddAndGetRankAsync() throws InterruptedException, ExecutionException {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        RFuture<Integer> future = set.addAndGetRankAsync(0.3, 1);
        Assert.assertEquals(new Integer(0), future.get());
        RFuture<Integer> future2 = set.addAndGetRankAsync(0.4, 2);
        Assert.assertEquals(new Integer(1), future2.get());
        RFuture<Integer> future3 = set.addAndGetRankAsync(0.2, 3);
        Assert.assertEquals(new Integer(0), future3.get());

        Assert.assertTrue(set.contains(3));
    }

    @Test
    public void testAddAndGetRevRankAsync() throws InterruptedException, ExecutionException {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        RFuture<Integer> future = set.addAndGetRevRankAsync(0.3, 1);
        Assert.assertEquals(new Integer(0), future.get());
        RFuture<Integer> future2 = set.addAndGetRevRankAsync(0.4, 2);
        Assert.assertEquals(new Integer(0), future2.get());
        RFuture<Integer> future3 = set.addAndGetRevRankAsync(0.2, 3);
        Assert.assertEquals(new Integer(2), future3.get());

        Assert.assertTrue(set.contains(3));
    }

    @Test
    public void testRemoveAsync() throws InterruptedException, ExecutionException {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        set.add(0.11, 1);
        set.add(0.22, 3);
        set.add(0.33, 7);

        Assert.assertTrue(set.removeAsync(1).get());
        Assert.assertFalse(set.contains(1));
        assertThat(set).containsExactly(3, 7);

        Assert.assertFalse(set.removeAsync(1).get());
        assertThat(set).containsExactly(3, 7);

        set.removeAsync(3).get();
        Assert.assertFalse(set.contains(3));
        assertThat(set).containsExactly(7);
    }

    @Test
    public void testIteratorNextNext() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(1, "1");
        set.add(2, "4");

        Iterator<String> iter = set.iterator();
        Assert.assertEquals("1", iter.next());
        Assert.assertEquals("4", iter.next());
        Assert.assertFalse(iter.hasNext());
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

        Assert.assertEquals(4, iteration);

        Assert.assertEquals(0, set.size());
        Assert.assertTrue(set.isEmpty());
    }

    @Test
    public void testIteratorSequence() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        for (int i = 0; i < 1000; i++) {
            set.add(i, Integer.valueOf(i));
        }

        Set<Integer> setCopy = new HashSet<Integer>();
        for (int i = 0; i < 1000; i++) {
            setCopy.add(Integer.valueOf(i));
        }

        checkIterator(set, setCopy);
    }

    private void checkIterator(RScoredSortedSet<Integer> set, Set<Integer> setCopy) {
        for (Iterator<Integer> iterator = set.iterator(); iterator.hasNext();) {
            Integer value = iterator.next();
            if (!setCopy.remove(value)) {
                Assert.fail();
            }
        }

        Assert.assertEquals(0, setCopy.size());
    }

    @Test
    public void testRetainAll() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        for (int i = 0; i < 20000; i++) {
            set.add(i*10, i);
        }

        Assert.assertTrue(set.retainAll(Arrays.asList(1, 2)));
        assertThat(set).containsExactly(1, 2); 
        Assert.assertEquals(2, set.size());
        assertThat(set.getScore(1)).isEqualTo(10);
        assertThat(set.getScore(2)).isEqualTo(20);
    }

    @Test
    public void testRemoveAll() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        set.add(0.1, 1);
        set.add(0.2, 2);
        set.add(0.3, 3);

        Assert.assertTrue(set.removeAll(Arrays.asList(1, 2)));
        assertThat(set).containsOnly(3);
        Assert.assertEquals(1, set.size());
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

    @Test(expected = IllegalArgumentException.class)
    public void testTailSetTreeSet() {
        TreeSet<Integer> set = new TreeSet<Integer>();

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

    @Test(expected = IllegalArgumentException.class)
    public void testHeadSetTreeSet() {
        TreeSet<Integer> set = new TreeSet<Integer>();

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
    public void testSort() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        Assert.assertTrue(set.add(4, 2));
        Assert.assertTrue(set.add(5, 3));
        Assert.assertTrue(set.add(3, 1));
        Assert.assertTrue(set.add(6, 4));
        Assert.assertTrue(set.add(1000, 10));
        Assert.assertTrue(set.add(1, -1));
        Assert.assertTrue(set.add(2, 0));

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

        Assert.assertFalse(set.remove(0));
        Assert.assertTrue(set.remove(3));

        assertThat(set).containsExactly(1, 2, 4, 5);
    }

    @Test
    public void testContainsAll() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        for (int i = 0; i < 200; i++) {
            set.add(i, i);
        }

        Assert.assertTrue(set.containsAll(Arrays.asList(30, 11)));
        Assert.assertFalse(set.containsAll(Arrays.asList(30, 711, 11)));
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

        Assert.assertTrue(set.contains(new TestObject("2", "3")));
        Assert.assertTrue(set.contains(new TestObject("1", "2")));
        Assert.assertFalse(set.contains(new TestObject("1", "9")));
    }

    @Test
    public void testDuplicates() {
        RScoredSortedSet<TestObject> set = redisson.getScoredSortedSet("simple");

        Assert.assertTrue(set.add(0, new TestObject("1", "2")));
        Assert.assertFalse(set.add(0, new TestObject("1", "2")));
        Assert.assertTrue(set.add(2, new TestObject("2", "3")));
        Assert.assertTrue(set.add(3, new TestObject("3", "4")));
        Assert.assertTrue(set.add(4, new TestObject("5", "6")));

        Assert.assertEquals(4, set.size());
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

        Assert.assertEquals(5, set.size());
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
        assertThat(vals).containsExactly(new ScoredEntry<Integer>(10D, 1),
                new ScoredEntry<Integer>(20D, 2),
                new ScoredEntry<Integer>(30D, 3),
                new ScoredEntry<Integer>(40D, 4),
                new ScoredEntry<Integer>(50D, 5));
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
                new ScoredEntry<Integer>(50D, 5),
                new ScoredEntry<Integer>(40D, 4),
                new ScoredEntry<Integer>(30D, 3),
                new ScoredEntry<Integer>(20D, 2),
                new ScoredEntry<Integer>(10D, 1)
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
        Assert.assertArrayEquals(new String[]{"c", "d"}, a);
    }

    @Test
    public void testScoredSortedSetValueRangeLimit() {
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
    public void testScoredSortedSetValueRange() {
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
    public void testScoredSortedSetValueRangeReversedLimit() {
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
    public void testScoredSortedSetValueRangeReversed() {
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
    public void testScoredSortedSetValueRangeNegativeInf() {
        RScoredSortedSet<String> set = redisson.<String>getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<String> r = set.valueRange(Double.NEGATIVE_INFINITY, true, 4, false, 1, 2);
        String[] a = r.toArray(new String[0]);
        Assert.assertArrayEquals(new String[]{"b", "c"}, a);
    }
    
    @Test
    public void testScoredSortedSetValueRangePositiveInf() {
        RScoredSortedSet<String> set = redisson.<String>getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<String> r = set.valueRange(1, true, Double.POSITIVE_INFINITY, false, 1, 2);
        String[] a = r.toArray(new String[0]);
        Assert.assertArrayEquals(new String[]{"c", "d"}, a);
    }

    @Test
    public void testScoredSortedSetEntryRange() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<ScoredEntry<String>> r = set.entryRange(1, true, 4, false, 1, 2);
        Assert.assertEquals(2, r.size());
        ScoredEntry<String>[] a = r.toArray(new ScoredEntry[0]);
        Assert.assertEquals(2d, a[0].getScore(), 0);
        Assert.assertEquals(3d, a[1].getScore(), 0);
        Assert.assertEquals("c", a[0].getValue());
        Assert.assertEquals("d", a[1].getValue());
    }

    @Test
    public void testScoredSortedSetEntryRangeReversed() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<ScoredEntry<String>> r = set.entryRangeReversed(1, true, 4, false, 1, 2);
        Assert.assertEquals(2, r.size());
        ScoredEntry<String>[] a = r.toArray(new ScoredEntry[0]);
        Assert.assertEquals(2d, a[0].getScore(), 0);
        Assert.assertEquals(1d, a[1].getScore(), 0);
        Assert.assertEquals("c", a[0].getValue());
        Assert.assertEquals("b", a[1].getValue());
    }
    
    @Test
    public void testScoredSortedSetEntryRangeNegativeInf() {
        RScoredSortedSet<String> set = redisson.<String>getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<ScoredEntry<String>> r = set.entryRange(Double.NEGATIVE_INFINITY, true, 4, false, 1, 2);
        ScoredEntry<String>[] a = r.toArray(new ScoredEntry[0]);
        Assert.assertEquals(1d, a[0].getScore(), 0);
        Assert.assertEquals(2d, a[1].getScore(), 0);
        Assert.assertEquals("b", a[0].getValue());
        Assert.assertEquals("c", a[1].getValue());
    }
    
    @Test
    public void testScoredSortedSetEntryRangePositiveInf() {
        RScoredSortedSet<String> set = redisson.<String>getScoredSortedSet("simple");

        set.add(0, "a");
        set.add(1, "b");
        set.add(2, "c");
        set.add(3, "d");
        set.add(4, "e");

        Collection<ScoredEntry<String>> r = set.entryRange(1, true, Double.POSITIVE_INFINITY, false, 1, 2);
        ScoredEntry<String>[] a = r.toArray(new ScoredEntry[0]);
        Assert.assertEquals(2d, a[0].getScore(), 0);
        Assert.assertEquals(3d, a[1].getScore(), 0);
        Assert.assertEquals("c", a[0].getValue());
        Assert.assertEquals("d", a[1].getValue());
    }
    
    @Test
    public void testAddAndGet() throws InterruptedException {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(1, "100");

        Double res = set.addScore("100", 11);
        Assert.assertEquals(12, (double)res, 0);
        Double score = set.getScore("100");
        Assert.assertEquals(12, (double)score, 0);

        RScoredSortedSet<String> set2 = redisson.getScoredSortedSet("simple");
        set2.add(100.2, "1");

        Double res2 = set2.addScore("1", new Double(12.1));
        Assert.assertTrue(new Double(112.3).compareTo(res2) == 0);
        res2 = set2.getScore("1");
        Assert.assertTrue(new Double(112.3).compareTo(res2) == 0);
    }
    
    @Test
    public void testAddScoreAndGetRank() throws InterruptedException {
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
    public void testAddScoreAndGetRevRank() throws InterruptedException {
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

    
}
