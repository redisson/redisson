package org.redisson;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RFuture;
import org.redisson.api.RSortedSet;
import org.redisson.client.codec.LongCodec;

public class RedissonSortedSetTest extends BaseTest {

    @Test
    public void test1() {
        RSortedSet<Long> set = redisson.getSortedSet("simple", LongCodec.INSTANCE);
        set.add(2L);
        set.add(0L);
        set.add(1L);
        set.add(5L);
        
        assertThat(set.readAll()).containsExactly(0L, 1L, 2L, 5L);
    }
    
    @Test
    public void testReadAll() {
        RSortedSet<Integer> set = redisson.getSortedSet("simple");
        set.add(2);
        set.add(0);
        set.add(1);
        set.add(5);
        
        assertThat(set.readAll()).containsExactly(0, 1, 2, 5);
    }
    
    @Test
    public void testAddAsync() throws InterruptedException, ExecutionException {
        RSortedSet<Integer> set = redisson.getSortedSet("simple");
        RFuture<Boolean> future = set.addAsync(2);
        Assert.assertTrue(future.get());

        Assert.assertTrue(set.contains(2));
    }

    @Test
    public void testRemoveAsync() throws InterruptedException, ExecutionException {
        RSortedSet<Integer> set = redisson.getSortedSet("simple");
        set.add(1);
        set.add(3);
        set.add(7);

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
        RSortedSet<String> list = redisson.getSortedSet("simple");
        list.add("1");
        list.add("4");

        Iterator<String> iter = list.iterator();
        Assert.assertEquals("1", iter.next());
        Assert.assertEquals("4", iter.next());
        Assert.assertFalse(iter.hasNext());
    }
    
    @Test
    public void testIteratorRemove() {
        RSortedSet<String> list = redisson.getSortedSet("list");
        list.add("1");
        list.add("4");
        list.add("2");
        list.add("5");
        list.add("3");

        for (Iterator<String> iterator = list.iterator(); iterator.hasNext();) {
            String value = iterator.next();
            if (value.equals("2")) {
                iterator.remove();
            }
        }

        assertThat(list).contains("1", "4", "5", "3");

        int iteration = 0;
        for (Iterator<String> iterator = list.iterator(); iterator.hasNext();) {
            iterator.next();
            iterator.remove();
            iteration++;
        }

        Assert.assertEquals(4, iteration);

        Assert.assertEquals(0, list.size());
        Assert.assertTrue(list.isEmpty());
    }
    
    @Test
    public void testIteratorSequence() {
        Set<Integer> set = redisson.getSortedSet("set");
        for (int i = 0; i < 1000; i++) {
            set.add(Integer.valueOf(i));
        }

        Set<Integer> setCopy = new HashSet<Integer>();
        for (int i = 0; i < 1000; i++) {
            setCopy.add(Integer.valueOf(i));
        }
        
        checkIterator(set, setCopy);
    }

    private void checkIterator(Set<Integer> set, Set<Integer> setCopy) {
        for (Iterator<Integer> iterator = set.iterator(); iterator.hasNext();) {
            Integer value = iterator.next();
            if (!setCopy.remove(value)) {
                Assert.fail();
            }
        }

        Assert.assertEquals(0, setCopy.size());
    }
    
    @Test
    public void testTrySetComparator() {
        RSortedSet<Integer> set = redisson.getSortedSet("set");

        boolean setRes = set.trySetComparator(Collections.reverseOrder());
        Assert.assertTrue(setRes);
        Assert.assertTrue(set.add(1));
        Assert.assertTrue(set.add(2));
        Assert.assertTrue(set.add(3));
        Assert.assertTrue(set.add(4));
        Assert.assertTrue(set.add(5));
        assertThat(set).containsExactly(5, 4, 3, 2, 1);

        boolean setRes2 = set.trySetComparator(Collections.reverseOrder(Collections.reverseOrder()));
        Assert.assertFalse(setRes2);
        assertThat(set).containsExactly(5, 4, 3, 2, 1);

        set.clear();
        boolean setRes3 = set.trySetComparator(Collections.reverseOrder(Collections.reverseOrder()));
        Assert.assertTrue(setRes3);
        set.add(3);
        set.add(1);
        set.add(2);
        assertThat(set).containsExactly(1, 2, 3);
    }


    @Test
    public void testOrder2() {
        TreeSet<Integer> set = new TreeSet<Integer>();

        set.add(1);
        set.add(2);
        set.add(3);
        set.add(4);
        set.add(5);

        SortedSet<Integer> hs = set.headSet(6);
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
        RSortedSet<Integer> set = redisson.getSortedSet("set");
        Assert.assertTrue(set.add(2));
        Assert.assertTrue(set.add(3));
        Assert.assertTrue(set.add(1));
        Assert.assertTrue(set.add(4));
        Assert.assertTrue(set.add(10));
        Assert.assertTrue(set.add(-1));
        Assert.assertTrue(set.add(0));

        assertThat(set).containsExactly(-1, 0, 1, 2, 3, 4, 10);

        Assert.assertEquals(-1, (int)set.first());
        Assert.assertEquals(10, (int)set.last());
    }

    @Test
    public void testRemove() {
        RSortedSet<Integer> set = redisson.getSortedSet("set");
        set.add(5);
        set.add(3);
        set.add(1);
        set.add(2);
        set.add(4);

        Assert.assertFalse(set.remove(0));
        Assert.assertTrue(set.remove(3));

        assertThat(set).containsExactly(1, 2, 4, 5);
    }

    @Test
    public void testRetainAll() {
        Set<Integer> set = redisson.getSortedSet("set");
        for (int i = 0; i < 200; i++) {
            set.add(i);
        }

        Assert.assertTrue(set.retainAll(Arrays.asList(1, 2)));
        Assert.assertEquals(2, set.size());
    }

    @Test
    public void testContainsAll() {
        Set<Integer> set = redisson.getSortedSet("set");
        for (int i = 0; i < 200; i++) {
            set.add(i);
        }

        Assert.assertTrue(set.containsAll(Arrays.asList(30, 11)));
        Assert.assertFalse(set.containsAll(Arrays.asList(30, 711, 11)));
    }

    @Test
    public void testToArray() {
        Set<String> set = redisson.getSortedSet("set");
        set.add("1");
        set.add("4");
        set.add("2");
        set.add("5");
        set.add("3");

        assertThat(set.toArray()).contains("1", "4", "2", "5", "3");

        String[] strs = set.toArray(new String[0]);
        assertThat(strs).contains("1", "4", "2", "5", "3");
    }

    @Test
    public void testContains() {
        Set<TestObject> set = redisson.getSortedSet("set");

        set.add(new TestObject("1", "2"));
        set.add(new TestObject("1", "2"));
        set.add(new TestObject("2", "3"));
        set.add(new TestObject("3", "4"));
        set.add(new TestObject("5", "6"));

        Assert.assertTrue(set.contains(new TestObject("2", "3")));
        Assert.assertTrue(set.contains(new TestObject("1", "2")));
        Assert.assertFalse(set.contains(new TestObject("1", "9")));
    }

    @Test
    public void testDuplicates() {
        Set<TestObject> set = redisson.getSortedSet("set");

        Assert.assertTrue(set.add(new TestObject("1", "2")));
        Assert.assertFalse(set.add(new TestObject("1", "2")));
        Assert.assertTrue(set.add(new TestObject("2", "3")));
        Assert.assertTrue(set.add(new TestObject("3", "4")));
        Assert.assertTrue(set.add(new TestObject("5", "6")));

        Assert.assertEquals(4, set.size());
    }

    @Test
    public void testSize() {
        Set<Integer> set = redisson.getSortedSet("set");
        set.add(1);
        set.add(2);
        set.add(3);
        set.add(3);
        set.add(4);
        set.add(5);
        set.add(5);

        Assert.assertEquals(5, set.size());
    }


}
