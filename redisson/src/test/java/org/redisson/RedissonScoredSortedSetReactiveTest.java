package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScoredSortedSetReactive;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.ScoredEntry;

public class RedissonScoredSortedSetReactiveTest extends BaseReactiveTest {

    @Test
    public void testFirstLast() {
        RScoredSortedSetReactive<String> set = redisson.getScoredSortedSet("simple");
        sync(set.add(0.1, "a"));
        sync(set.add(0.2, "b"));
        sync(set.add(0.3, "c"));
        sync(set.add(0.4, "d"));

        Assertions.assertEquals("a", sync(set.first()));
        Assertions.assertEquals("d", sync(set.last()));
    }


    @Test
    public void testRemoveRangeByScore() {
        RScoredSortedSetReactive<String> set = redisson.getScoredSortedSet("simple");
        sync(set.add(0.1, "a"));
        sync(set.add(0.2, "b"));
        sync(set.add(0.3, "c"));
        sync(set.add(0.4, "d"));
        sync(set.add(0.5, "e"));
        sync(set.add(0.6, "f"));
        sync(set.add(0.7, "g"));

        Assertions.assertEquals(2, sync(set.removeRangeByScore(0.1, false, 0.3, true)).intValue());
        assertThat(sync(set)).containsOnly("a", "d", "e", "f", "g");
    }

    @Test
    public void testRemoveRangeByRank() {
        RScoredSortedSetReactive<String> set = redisson.getScoredSortedSet("simple");
        sync(set.add(0.1, "a"));
        sync(set.add(0.2, "b"));
        sync(set.add(0.3, "c"));
        sync(set.add(0.4, "d"));
        sync(set.add(0.5, "e"));
        sync(set.add(0.6, "f"));
        sync(set.add(0.7, "g"));

        Assertions.assertEquals(2, sync(set.removeRangeByRank(0, 1)).intValue());
        assertThat(sync(set)).containsOnly("c", "d", "e", "f", "g");
    }

    @Test
    public void testRank() {
        RScoredSortedSetReactive<String> set = redisson.getScoredSortedSet("simple");
        sync(set.add(0.1, "a"));
        sync(set.add(0.2, "b"));
        sync(set.add(0.3, "c"));
        sync(set.add(0.4, "d"));
        sync(set.add(0.5, "e"));
        sync(set.add(0.6, "f"));
        sync(set.add(0.7, "g"));

        Assertions.assertEquals(3, sync(set.rank("d")).intValue());
    }

    @Test
    public void testAddAsync() throws InterruptedException, ExecutionException {
        RScoredSortedSetReactive<Integer> set = redisson.getScoredSortedSet("simple");
        Assertions.assertTrue(sync(set.add(0.323, 2)));
        Assertions.assertFalse(sync(set.add(0.323, 2)));

        Assertions.assertTrue(sync(set.contains(2)));
    }

    @Test
    public void testRemoveAsync() throws InterruptedException, ExecutionException {
        RScoredSortedSetReactive<Integer> set = redisson.getScoredSortedSet("simple");
        sync(set.add(0.11, 1));
        sync(set.add(0.22, 3));
        sync(set.add(0.33, 7));

        Assertions.assertTrue(sync(set.remove(1)));
        Assertions.assertFalse(sync(set.contains(1)));
        assertThat(sync(set)).containsExactly(3, 7);

        Assertions.assertFalse(sync(set.remove(1)));
        assertThat(sync(set)).containsExactly(3, 7);

        sync(set.remove(3));
        Assertions.assertFalse(sync(set.contains(3)));
        assertThat(sync(set)).containsExactly(7);
    }

    @Test
    public void testIteratorNextNext() {
        RScoredSortedSetReactive<String> set = redisson.getScoredSortedSet("simple");
        sync(set.add(1, "1"));
        sync(set.add(2, "4"));

        Iterator<String> iter = toIterator(set.iterator());
        Assertions.assertEquals("1", iter.next());
        Assertions.assertEquals("4", iter.next());
        Assertions.assertFalse(iter.hasNext());
    }

    @Test
    public void testIteratorSequence() {
        RScoredSortedSetReactive<Integer> set = redisson.getScoredSortedSet("simple");
        for (int i = 0; i < 1000; i++) {
            sync(set.add(i, Integer.valueOf(i)));
        }

        Set<Integer> setCopy = new HashSet<Integer>();
        for (int i = 0; i < 1000; i++) {
            setCopy.add(Integer.valueOf(i));
        }

        checkIterator(set, setCopy);
    }

    private void checkIterator(RScoredSortedSetReactive<Integer> set, Set<Integer> setCopy) {
        for (Iterator<Integer> iterator = toIterator(set.iterator()); iterator.hasNext();) {
            Integer value = iterator.next();
            if (!setCopy.remove(value)) {
                Assertions.fail();
            }
        }

        Assertions.assertEquals(0, setCopy.size());
    }

    @Test
    public void testRetainAll() {
        RScoredSortedSetReactive<Integer> set = redisson.getScoredSortedSet("simple");
        for (int i = 0; i < 20000; i++) {
            sync(set.add(i, i));
        }

        Assertions.assertTrue(sync(set.retainAll(Arrays.asList(1, 2))));
        assertThat(sync(set)).contains(1, 2);
        Assertions.assertEquals(2, sync(set.size()).intValue());
    }

    @Test
    public void testRemoveAll() {
        RScoredSortedSetReactive<Integer> set = redisson.getScoredSortedSet("simple");
        sync(set.add(0.1, 1));
        sync(set.add(0.2, 2));
        sync(set.add(0.3, 3));

        Assertions.assertTrue(sync(set.removeAll(Arrays.asList(1, 2))));
        assertThat(sync(set)).contains(3);
        Assertions.assertEquals(1, sync(set.size()).intValue());
    }


    @Test
    public void testSort() {
        RScoredSortedSetReactive<Integer> set = redisson.getScoredSortedSet("simple");
        Assertions.assertTrue(sync(set.add(4, 2)));
        Assertions.assertTrue(sync(set.add(5, 3)));
        Assertions.assertTrue(sync(set.add(3, 1)));
        Assertions.assertTrue(sync(set.add(6, 4)));
        Assertions.assertTrue(sync(set.add(1000, 10)));
        Assertions.assertTrue(sync(set.add(1, -1)));
        Assertions.assertTrue(sync(set.add(2, 0)));

        assertThat(sync(set)).containsExactly(-1, 0, 1, 2, 3, 4, 10);

        Assertions.assertEquals(-1, (int)sync(set.first()));
        Assertions.assertEquals(10, (int)sync(set.last()));
    }

    @Test
    public void testRemove() {
        RScoredSortedSetReactive<Integer> set = redisson.getScoredSortedSet("simple");
        sync(set.add(4, 5));
        sync(set.add(2, 3));
        sync(set.add(0, 1));
        sync(set.add(1, 2));
        sync(set.add(3, 4));

        Assertions.assertFalse(sync(set.remove(0)));
        Assertions.assertTrue(sync(set.remove(3)));

        assertThat(sync(set)).containsExactly(1, 2, 4, 5);
    }

    @Test
    public void testContainsAll() {
        RScoredSortedSetReactive<Integer> set = redisson.getScoredSortedSet("simple");
        for (int i = 0; i < 200; i++) {
            sync(set.add(i, i));
        }

        Assertions.assertTrue(sync(set.containsAll(Arrays.asList(30, 11))));
        Assertions.assertFalse(sync(set.containsAll(Arrays.asList(30, 711, 11))));
    }

    @Test
    public void testContains() {
        RScoredSortedSetReactive<TestObject> set = redisson.getScoredSortedSet("simple");

        sync(set.add(0, new TestObject("1", "2")));
        sync(set.add(1, new TestObject("1", "2")));
        sync(set.add(2, new TestObject("2", "3")));
        sync(set.add(3, new TestObject("3", "4")));
        sync(set.add(4, new TestObject("5", "6")));

        Assertions.assertTrue(sync(set.contains(new TestObject("2", "3"))));
        Assertions.assertTrue(sync(set.contains(new TestObject("1", "2"))));
        Assertions.assertFalse(sync(set.contains(new TestObject("1", "9"))));
    }

    @Test
    public void testDuplicates() {
        RScoredSortedSetReactive<TestObject> set = redisson.getScoredSortedSet("simple");

        Assertions.assertTrue(sync(set.add(0, new TestObject("1", "2"))));
        Assertions.assertFalse(sync(set.add(0, new TestObject("1", "2"))));
        Assertions.assertTrue(sync(set.add(2, new TestObject("2", "3"))));
        Assertions.assertTrue(sync(set.add(3, new TestObject("3", "4"))));
        Assertions.assertTrue(sync(set.add(4, new TestObject("5", "6"))));

        Assertions.assertEquals(4, sync(set.size()).intValue());
    }

    @Test
    public void testSize() {
        RScoredSortedSetReactive<Integer> set = redisson.getScoredSortedSet("simple");
        sync(set.add(0, 1));
        sync(set.add(1, 2));
        sync(set.add(2, 3));
        sync(set.add(2, 3));
        sync(set.add(3, 4));
        sync(set.add(4, 5));
        sync(set.add(4, 5));

        Assertions.assertEquals(5, sync(set.size()).intValue());
    }

    @Test
    public void testValueRange() {
        RScoredSortedSetReactive<Integer> set = redisson.getScoredSortedSet("simple");
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
        RScoredSortedSetReactive<Integer> set = redisson.getScoredSortedSet("simple");
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
        RScoredSortedSetReactive<String> set = redisson.<String>getScoredSortedSet("simple");

        sync(set.add(0, "a"));
        sync(set.add(1, "b"));
        sync(set.add(2, "c"));
        sync(set.add(3, "d"));
        sync(set.add(4, "e"));

        Collection<String> r = sync(set.valueRange(1, true, 4, false, 1, 2));
        String[] a = r.toArray(new String[0]);
        Assertions.assertArrayEquals(new String[]{"c", "d"}, a);
    }

    @Test
    public void testScoredSortedSetEntryRange() {
        RScoredSortedSetReactive<String> set = redisson.<String>getScoredSortedSet("simple");

        sync(set.add(0, "a"));
        sync(set.add(1, "b"));
        sync(set.add(2, "c"));
        sync(set.add(3, "d"));
        sync(set.add(4, "e"));

        Collection<ScoredEntry<String>> r = sync(set.entryRange(1, true, 4, false, 1, 2));
        ScoredEntry<String>[] a = r.toArray(new ScoredEntry[0]);
        Assertions.assertEquals(2d, a[0].getScore(), 0);
        Assertions.assertEquals(3d, a[1].getScore(), 0);
        Assertions.assertEquals("c", a[0].getValue());
        Assertions.assertEquals("d", a[1].getValue());
    }

    @Test
    public void testAddAndGet() throws InterruptedException {
        RScoredSortedSetReactive<Integer> set = redisson.getScoredSortedSet("simple", StringCodec.INSTANCE);
        sync(set.add(1, 100));

        Double res = sync(set.addScore(100, 11));
        Assertions.assertEquals(12, (double)res, 0);
        Double score = sync(set.getScore(100));
        Assertions.assertEquals(12, (double)score, 0);

        RScoredSortedSetReactive<Integer> set2 = redisson.getScoredSortedSet("simple", StringCodec.INSTANCE);
        sync(set2.add(100.2, 1));

        Double res2 = sync(set2.addScore(1, new Double(12.1)));
        Assertions.assertTrue(new Double(112.3).compareTo(res2) == 0);
        res2 = sync(set2.getScore(1));
        Assertions.assertTrue(new Double(112.3).compareTo(res2) == 0);
    }

}
