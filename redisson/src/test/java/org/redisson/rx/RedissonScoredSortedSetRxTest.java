package org.redisson.rx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.TestObject;
import org.redisson.api.RScoredSortedSetRx;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.ScoredEntry;

public class RedissonScoredSortedSetRxTest extends BaseRxTest {

    @Test
    public void testFirstLast() {
        RScoredSortedSetRx<String> set = redisson.getScoredSortedSet("simple");
        sync(set.add(0.1, "a"));
        sync(set.add(0.2, "b"));
        sync(set.add(0.3, "c"));
        sync(set.add(0.4, "d"));

        Assert.assertEquals("a", sync(set.first()));
        Assert.assertEquals("d", sync(set.last()));
    }


    @Test
    public void testRemoveRangeByScore() {
        RScoredSortedSetRx<String> set = redisson.getScoredSortedSet("simple");
        sync(set.add(0.1, "a"));
        sync(set.add(0.2, "b"));
        sync(set.add(0.3, "c"));
        sync(set.add(0.4, "d"));
        sync(set.add(0.5, "e"));
        sync(set.add(0.6, "f"));
        sync(set.add(0.7, "g"));

        Assert.assertEquals(2, sync(set.removeRangeByScore(0.1, false, 0.3, true)).intValue());
        assertThat(sync(set)).containsOnly("a", "d", "e", "f", "g");
    }

    @Test
    public void testRemoveRangeByRank() {
        RScoredSortedSetRx<String> set = redisson.getScoredSortedSet("simple");
        sync(set.add(0.1, "a"));
        sync(set.add(0.2, "b"));
        sync(set.add(0.3, "c"));
        sync(set.add(0.4, "d"));
        sync(set.add(0.5, "e"));
        sync(set.add(0.6, "f"));
        sync(set.add(0.7, "g"));

        Assert.assertEquals(2, sync(set.removeRangeByRank(0, 1)).intValue());
        assertThat(sync(set)).containsOnly("c", "d", "e", "f", "g");
    }

    @Test
    public void testRank() {
        RScoredSortedSetRx<String> set = redisson.getScoredSortedSet("simple");
        sync(set.add(0.1, "a"));
        sync(set.add(0.2, "b"));
        sync(set.add(0.3, "c"));
        sync(set.add(0.4, "d"));
        sync(set.add(0.5, "e"));
        sync(set.add(0.6, "f"));
        sync(set.add(0.7, "g"));

        Assert.assertEquals(3, sync(set.rank("d")).intValue());
    }

    @Test
    public void testAddAsync() throws InterruptedException, ExecutionException {
        RScoredSortedSetRx<Integer> set = redisson.getScoredSortedSet("simple");
        Assert.assertTrue(sync(set.add(0.323, 2)));
        Assert.assertFalse(sync(set.add(0.323, 2)));

        Assert.assertTrue(sync(set.contains(2)));
    }

    @Test
    public void testRemoveAsync() throws InterruptedException, ExecutionException {
        RScoredSortedSetRx<Integer> set = redisson.getScoredSortedSet("simple");
        sync(set.add(0.11, 1));
        sync(set.add(0.22, 3));
        sync(set.add(0.33, 7));

        Assert.assertTrue(sync(set.remove(1)));
        Assert.assertFalse(sync(set.contains(1)));
        assertThat(sync(set)).containsExactly(3, 7);

        Assert.assertFalse(sync(set.remove(1)));
        assertThat(sync(set)).containsExactly(3, 7);

        sync(set.remove(3));
        Assert.assertFalse(sync(set.contains(3)));
        assertThat(sync(set)).containsExactly(7);
    }

    @Test
    public void testIteratorNextNext() {
        RScoredSortedSetRx<String> set = redisson.getScoredSortedSet("simple");
        sync(set.add(1, "1"));
        sync(set.add(2, "4"));

        Iterator<String> iter = toIterator(set.iterator());
        Assert.assertEquals("1", iter.next());
        Assert.assertEquals("4", iter.next());
        Assert.assertFalse(iter.hasNext());
    }

    @Test
    public void testIteratorSequence() {
        RScoredSortedSetRx<Integer> set = redisson.getScoredSortedSet("simple");
        for (int i = 0; i < 1000; i++) {
            sync(set.add(i, Integer.valueOf(i)));
        }

        Set<Integer> setCopy = new HashSet<Integer>();
        for (int i = 0; i < 1000; i++) {
            setCopy.add(Integer.valueOf(i));
        }

        checkIterator(set, setCopy);
    }

    private void checkIterator(RScoredSortedSetRx<Integer> set, Set<Integer> setCopy) {
        for (Iterator<Integer> iterator = toIterator(set.iterator()); iterator.hasNext();) {
            Integer value = iterator.next();
            if (!setCopy.remove(value)) {
                Assert.fail();
            }
        }

        Assert.assertEquals(0, setCopy.size());
    }

    @Test
    public void testRetainAll() {
        RScoredSortedSetRx<Integer> set = redisson.getScoredSortedSet("simple");
        for (int i = 0; i < 20000; i++) {
            sync(set.add(i, i));
        }

        Assert.assertTrue(sync(set.retainAll(Arrays.asList(1, 2))));
        assertThat(sync(set)).contains(1, 2);
        Assert.assertEquals(2, sync(set.size()).intValue());
    }

    @Test
    public void testRemoveAll() {
        RScoredSortedSetRx<Integer> set = redisson.getScoredSortedSet("simple");
        sync(set.add(0.1, 1));
        sync(set.add(0.2, 2));
        sync(set.add(0.3, 3));

        Assert.assertTrue(sync(set.removeAll(Arrays.asList(1, 2))));
        assertThat(sync(set)).contains(3);
        Assert.assertEquals(1, sync(set.size()).intValue());
    }


    @Test
    public void testSort() {
        RScoredSortedSetRx<Integer> set = redisson.getScoredSortedSet("simple");
        Assert.assertTrue(sync(set.add(4, 2)));
        Assert.assertTrue(sync(set.add(5, 3)));
        Assert.assertTrue(sync(set.add(3, 1)));
        Assert.assertTrue(sync(set.add(6, 4)));
        Assert.assertTrue(sync(set.add(1000, 10)));
        Assert.assertTrue(sync(set.add(1, -1)));
        Assert.assertTrue(sync(set.add(2, 0)));

        assertThat(sync(set)).containsExactly(-1, 0, 1, 2, 3, 4, 10);

        Assert.assertEquals(-1, (int)sync(set.first()));
        Assert.assertEquals(10, (int)sync(set.last()));
    }

    @Test
    public void testRemove() {
        RScoredSortedSetRx<Integer> set = redisson.getScoredSortedSet("simple");
        sync(set.add(4, 5));
        sync(set.add(2, 3));
        sync(set.add(0, 1));
        sync(set.add(1, 2));
        sync(set.add(3, 4));

        Assert.assertFalse(sync(set.remove(0)));
        Assert.assertTrue(sync(set.remove(3)));

        assertThat(sync(set)).containsExactly(1, 2, 4, 5);
    }

    @Test
    public void testContainsAll() {
        RScoredSortedSetRx<Integer> set = redisson.getScoredSortedSet("simple");
        for (int i = 0; i < 200; i++) {
            sync(set.add(i, i));
        }

        Assert.assertTrue(sync(set.containsAll(Arrays.asList(30, 11))));
        Assert.assertFalse(sync(set.containsAll(Arrays.asList(30, 711, 11))));
    }

    @Test
    public void testContains() {
        RScoredSortedSetRx<TestObject> set = redisson.getScoredSortedSet("simple");

        sync(set.add(0, new TestObject("1", "2")));
        sync(set.add(1, new TestObject("1", "2")));
        sync(set.add(2, new TestObject("2", "3")));
        sync(set.add(3, new TestObject("3", "4")));
        sync(set.add(4, new TestObject("5", "6")));

        Assert.assertTrue(sync(set.contains(new TestObject("2", "3"))));
        Assert.assertTrue(sync(set.contains(new TestObject("1", "2"))));
        Assert.assertFalse(sync(set.contains(new TestObject("1", "9"))));
    }

    @Test
    public void testDuplicates() {
        RScoredSortedSetRx<TestObject> set = redisson.getScoredSortedSet("simple");

        Assert.assertTrue(sync(set.add(0, new TestObject("1", "2"))));
        Assert.assertFalse(sync(set.add(0, new TestObject("1", "2"))));
        Assert.assertTrue(sync(set.add(2, new TestObject("2", "3"))));
        Assert.assertTrue(sync(set.add(3, new TestObject("3", "4"))));
        Assert.assertTrue(sync(set.add(4, new TestObject("5", "6"))));

        Assert.assertEquals(4, sync(set.size()).intValue());
    }

    @Test
    public void testSize() {
        RScoredSortedSetRx<Integer> set = redisson.getScoredSortedSet("simple");
        sync(set.add(0, 1));
        sync(set.add(1, 2));
        sync(set.add(2, 3));
        sync(set.add(2, 3));
        sync(set.add(3, 4));
        sync(set.add(4, 5));
        sync(set.add(4, 5));

        Assert.assertEquals(5, sync(set.size()).intValue());
    }

    @Test
    public void testValueRange() {
        RScoredSortedSetRx<Integer> set = redisson.getScoredSortedSet("simple");
        sync(set.add(0, 1));
        sync(set.add(1, 2));
        sync(set.add(2, 3));
        sync(set.add(3, 4));
        sync(set.add(4, 5));
        sync(set.add(4, 5));

        Collection<Integer> vals = sync(set.valueRange(0, -1));
        assertThat(sync(set)).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    public void testEntryRange() {
        RScoredSortedSetRx<Integer> set = redisson.getScoredSortedSet("simple");
        sync(set.add(10, 1));
        sync(set.add(20, 2));
        sync(set.add(30, 3));
        sync(set.add(40, 4));
        sync(set.add(50, 5));

        Collection<ScoredEntry<Integer>> vals = sync(set.entryRange(0, -1));
        assertThat(vals).contains(new ScoredEntry<Integer>(10D, 1),
                new ScoredEntry<Integer>(20D, 2),
                new ScoredEntry<Integer>(30D, 3),
                new ScoredEntry<Integer>(40D, 4),
                new ScoredEntry<Integer>(50D, 5));
    }

    @Test
    public void testScoredSortedSetValueRange() {
        RScoredSortedSetRx<String> set = redisson.<String>getScoredSortedSet("simple");

        sync(set.add(0, "a"));
        sync(set.add(1, "b"));
        sync(set.add(2, "c"));
        sync(set.add(3, "d"));
        sync(set.add(4, "e"));

        Collection<String> r = sync(set.valueRange(1, true, 4, false, 1, 2));
        String[] a = r.toArray(new String[0]);
        Assert.assertArrayEquals(new String[]{"c", "d"}, a);
    }

    @Test
    public void testScoredSortedSetEntryRange() {
        RScoredSortedSetRx<String> set = redisson.<String>getScoredSortedSet("simple");

        sync(set.add(0, "a"));
        sync(set.add(1, "b"));
        sync(set.add(2, "c"));
        sync(set.add(3, "d"));
        sync(set.add(4, "e"));

        Collection<ScoredEntry<String>> r = sync(set.entryRange(1, true, 4, false, 1, 2));
        ScoredEntry<String>[] a = r.toArray(new ScoredEntry[0]);
        Assert.assertEquals(2d, a[0].getScore(), 0);
        Assert.assertEquals(3d, a[1].getScore(), 0);
        Assert.assertEquals("c", a[0].getValue());
        Assert.assertEquals("d", a[1].getValue());
    }

    @Test
    public void testAddAndGet() throws InterruptedException {
        RScoredSortedSetRx<Integer> set = redisson.getScoredSortedSet("simple", StringCodec.INSTANCE);
        sync(set.add(1, 100));

        Double res = sync(set.addScore(100, 11));
        Assert.assertEquals(12, (double)res, 0);
        Double score = sync(set.getScore(100));
        Assert.assertEquals(12, (double)score, 0);

        RScoredSortedSetRx<Integer> set2 = redisson.getScoredSortedSet("simple", StringCodec.INSTANCE);
        sync(set2.add(100.2, 1));

        Double res2 = sync(set2.addScore(1, new Double(12.1)));
        Assert.assertTrue(new Double(112.3).compareTo(res2) == 0);
        res2 = sync(set2.getScore(1));
        Assert.assertTrue(new Double(112.3).compareTo(res2) == 0);
    }

}
