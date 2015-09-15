package org.redisson;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.client.protocol.ScoredEntry;
import org.redisson.core.RScoredSortedSet;
import org.redisson.core.RSortedSet;

import io.netty.util.concurrent.Future;

public class RedissonScoredSortedSetTest extends BaseTest {

    @Test
    public void testFirstLast() {
        RScoredSortedSet<String> set = redisson.getScoredSortedSet("simple");
        set.add(0.1, "a");
        set.add(0.2, "b");
        set.add(0.3, "c");
        set.add(0.4, "d");

        Assert.assertEquals("a", set.first());
        Assert.assertEquals("d", set.last());
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
        MatcherAssert.assertThat(set, Matchers.contains("a", "d", "e", "f", "g"));
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
        MatcherAssert.assertThat(set, Matchers.contains("c", "d", "e", "f", "g"));
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

        Assert.assertEquals(3, (int)set.rank("d"));
    }

    @Test
    public void testAddAsync() throws InterruptedException, ExecutionException {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        Future<Boolean> future = set.addAsync(0.323, 2);
        Assert.assertTrue(future.get());
        Future<Boolean> future2 = set.addAsync(0.323, 2);
        Assert.assertFalse(future2.get());

        Assert.assertTrue(set.contains(2));
    }

    @Test
    public void testRemoveAsync() throws InterruptedException, ExecutionException {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        set.add(0.11, 1);
        set.add(0.22, 3);
        set.add(0.33, 7);

        Assert.assertTrue(set.removeAsync(1).get());
        Assert.assertFalse(set.contains(1));
        Assert.assertThat(set, Matchers.contains(3, 7));

        Assert.assertFalse(set.removeAsync(1).get());
        Assert.assertThat(set, Matchers.contains(3, 7));

        set.removeAsync(3).get();
        Assert.assertFalse(set.contains(3));
        Assert.assertThat(set, Matchers.contains(7));
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

        Assert.assertThat(set, Matchers.contains("1", "4", "5", "3"));

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
            set.add(i, i);
        }

        Assert.assertTrue(set.retainAll(Arrays.asList(1, 2)));
        Assert.assertThat(set, Matchers.containsInAnyOrder(1, 2));
        Assert.assertEquals(2, set.size());
    }

    @Test
    public void testRemoveAll() {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        set.add(0.1, 1);
        set.add(0.2, 2);
        set.add(0.3, 3);

        Assert.assertTrue(set.removeAll(Arrays.asList(1, 2)));
        Assert.assertThat(set, Matchers.contains(3));
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

        MatcherAssert.assertThat(hs, Matchers.contains(3, 4, 5, 10));

        set.remove(4);

        MatcherAssert.assertThat(hs, Matchers.contains(3, 5, 10));

        set.remove(3);

        MatcherAssert.assertThat(hs, Matchers.contains(5, 10));

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

        MatcherAssert.assertThat(hs, Matchers.contains(0, 1, 2));

        set.remove(2);

        MatcherAssert.assertThat(hs, Matchers.contains(0, 1));

        set.remove(3);

        MatcherAssert.assertThat(hs, Matchers.contains(0, 1));

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

        MatcherAssert.assertThat(hs, Matchers.contains(3, 4, 5, 10));

        set.remove(4);

        MatcherAssert.assertThat(hs, Matchers.contains(3, 5, 10));

        set.remove(3);

        MatcherAssert.assertThat(hs, Matchers.contains(5, 10));

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

        MatcherAssert.assertThat(hs, Matchers.contains(0, 1, 2));

        set.remove(2);

        MatcherAssert.assertThat(hs, Matchers.contains(0, 1));

        set.remove(3);

        MatcherAssert.assertThat(hs, Matchers.contains(0, 1));

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

        MatcherAssert.assertThat(set, Matchers.contains(-1, 0, 1, 2, 3, 4, 10));

//        Assert.assertEquals(-1, (int)set.first());
//        Assert.assertEquals(10, (int)set.last());
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

        Assert.assertThat(set, Matchers.contains(1, 2, 4, 5));
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

        MatcherAssert.assertThat(Arrays.asList(set.toArray()), Matchers.<Object>containsInAnyOrder("1", "2", "4", "5", "3"));

        String[] strs = set.toArray(new String[0]);
        MatcherAssert.assertThat(Arrays.asList(strs), Matchers.containsInAnyOrder("1", "4", "2", "5", "3"));
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
        MatcherAssert.assertThat(vals, Matchers.contains(1, 2, 3, 4, 5));
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
        MatcherAssert.assertThat(vals, Matchers.contains(new ScoredEntry<Integer>(10D, 1),
                new ScoredEntry<Integer>(20D, 2),
                new ScoredEntry<Integer>(30D, 3),
                new ScoredEntry<Integer>(40D, 4),
                new ScoredEntry<Integer>(50D, 5)));
    }


    @Test
    public void testAddAndGet() throws InterruptedException {
        RScoredSortedSet<Integer> set = redisson.getScoredSortedSet("simple");
        set.add(1, 100);

        Double res = set.addScore(100, 11);
        Assert.assertEquals(12, (double)res, 0);
        Double score = set.getScore(100);
        Assert.assertEquals(12, (double)score, 0);

        RScoredSortedSet<Integer> set2 = redisson.getScoredSortedSet("simple");
        set2.add(100.2, 1);

        Double res2 = set2.addScore(1, new Double(12.1));
        Assert.assertTrue(new Double(112.3).compareTo(res2) == 0);
        res2 = set2.getScore(1);
        Assert.assertTrue(new Double(112.3).compareTo(res2) == 0);
    }

}
