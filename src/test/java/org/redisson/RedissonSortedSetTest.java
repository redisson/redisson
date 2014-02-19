package org.redisson;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RSortedSet;

public class RedissonSortedSetTest extends BaseTest {

    @Test
    public void testTrySetComparator() {
        RSortedSet<Integer> set = currentRedisson().getSortedSet("set");

        boolean setRes = set.trySetComparator(Collections.reverseOrder());
        Assert.assertTrue(setRes);
        Assert.assertTrue(set.add(1));
        Assert.assertTrue(set.add(2));
        Assert.assertTrue(set.add(3));
        Assert.assertTrue(set.add(4));
        Assert.assertTrue(set.add(5));
        MatcherAssert.assertThat(set, Matchers.contains(5, 4, 3, 2, 1));

        boolean setRes2 = set.trySetComparator(Collections.reverseOrder(Collections.reverseOrder()));
        Assert.assertFalse(setRes2);
        MatcherAssert.assertThat(set, Matchers.contains(5, 4, 3, 2, 1));

        set.clear();
        boolean setRes3 = set.trySetComparator(Collections.reverseOrder(Collections.reverseOrder()));
        Assert.assertTrue(setRes3);
        set.add(3);
        set.add(1);
        set.add(2);
        MatcherAssert.assertThat(set, Matchers.contains(1, 2, 3));


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

    @Test(expected = IllegalArgumentException.class)
    public void testTailSet() {
        RSortedSet<Integer> set = currentRedisson().getSortedSet("set");

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
    public void testHeadSet() {
        RSortedSet<Integer> set = currentRedisson().getSortedSet("set");

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
        RSortedSet<Integer> set = currentRedisson().getSortedSet("set");
        Assert.assertTrue(set.add(2));
        Assert.assertTrue(set.add(3));
        Assert.assertTrue(set.add(1));
        Assert.assertTrue(set.add(4));
        Assert.assertTrue(set.add(10));
        Assert.assertTrue(set.add(-1));
        Assert.assertTrue(set.add(0));

        MatcherAssert.assertThat(set, Matchers.contains(-1, 0, 1, 2, 3, 4, 10));

        Assert.assertEquals(-1, (int)set.first());
        Assert.assertEquals(10, (int)set.last());
    }

    @Test
    public void testRemove() {
        RSortedSet<Integer> set = currentRedisson().getSortedSet("set");
        set.add(5);
        set.add(3);
        set.add(1);
        set.add(2);
        set.add(4);

        Assert.assertFalse(set.remove(0));
        Assert.assertTrue(set.remove(3));

        Assert.assertThat(set, Matchers.contains(1, 2, 4, 5));
    }

    @Test
    public void testRetainAll() {
        Set<Integer> set = currentRedisson().getSortedSet("set");
        for (int i = 0; i < 200; i++) {
            set.add(i);
        }

        Assert.assertTrue(set.retainAll(Arrays.asList(1, 2)));
        Assert.assertEquals(2, set.size());
    }

    @Test
    public void testContainsAll() {
        Set<Integer> set = currentRedisson().getSortedSet("set");
        for (int i = 0; i < 200; i++) {
            set.add(i);
        }

        Assert.assertTrue(set.containsAll(Arrays.asList(30, 11)));
        Assert.assertFalse(set.containsAll(Arrays.asList(30, 711, 11)));
    }

    @Test
    public void testToArray() {
        Set<String> set = currentRedisson().getSortedSet("set");
        set.add("1");
        set.add("4");
        set.add("2");
        set.add("5");
        set.add("3");

        MatcherAssert.assertThat(Arrays.asList(set.toArray()), Matchers.<Object>containsInAnyOrder("1", "2", "4", "5", "3"));

        String[] strs = set.toArray(new String[0]);
        MatcherAssert.assertThat(Arrays.asList(strs), Matchers.containsInAnyOrder("1", "4", "2", "5", "3"));
    }

    @Test
    public void testContains() {
        Set<TestObject> set = currentRedisson().getSortedSet("set");

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
        Set<TestObject> set = currentRedisson().getSortedSet("set");

        Assert.assertTrue(set.add(new TestObject("1", "2")));
        Assert.assertFalse(set.add(new TestObject("1", "2")));
        Assert.assertTrue(set.add(new TestObject("2", "3")));
        Assert.assertTrue(set.add(new TestObject("3", "4")));
        Assert.assertTrue(set.add(new TestObject("5", "6")));

        Assert.assertEquals(4, set.size());
    }

    @Test
    public void testSize() {
        Set<Integer> set = currentRedisson().getSortedSet("set");
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
