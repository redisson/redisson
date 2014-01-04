package org.redisson;

import java.util.Arrays;
import java.util.Set;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

public class RedissonSetTest extends BaseTest {

    @Test
    public void testContainsAll() {
        Redisson redisson = Redisson.create();
        Set<Integer> set = redisson.getSet("set");
        for (int i = 0; i < 200; i++) {
            set.add(i);
        }

        Assert.assertTrue(set.containsAll(Arrays.asList(30, 11)));
        Assert.assertFalse(set.containsAll(Arrays.asList(30, 711, 11)));

        clear(set, redisson);
    }

    @Test
    public void testToArray() {
        Redisson redisson = Redisson.create();
        Set<String> set = redisson.getSet("set");
        set.add("1");
        set.add("4");
        set.add("2");
        set.add("5");
        set.add("3");

        MatcherAssert.assertThat(Arrays.asList(set.toArray()), Matchers.<Object>containsInAnyOrder("1", "2", "4", "5", "3"));

        String[] strs = set.toArray(new String[0]);
        MatcherAssert.assertThat(Arrays.asList(strs), Matchers.containsInAnyOrder("1", "4", "2", "5", "3"));

        clear(set, redisson);
    }

    @Test
    public void testContains() {
        Redisson redisson = Redisson.create();
        Set<TestObject> set = redisson.getSet("set");

        set.add(new TestObject("1", "2"));
        set.add(new TestObject("1", "2"));
        set.add(new TestObject("2", "3"));
        set.add(new TestObject("3", "4"));
        set.add(new TestObject("5", "6"));

        Assert.assertTrue(set.contains(new TestObject("2", "3")));
        Assert.assertTrue(set.contains(new TestObject("1", "2")));
        Assert.assertFalse(set.contains(new TestObject("1", "9")));

        clear(set, redisson);
    }

    @Test
    public void testDuplicates() {
        Redisson redisson = Redisson.create();
        Set<TestObject> set = redisson.getSet("set");

        set.add(new TestObject("1", "2"));
        set.add(new TestObject("1", "2"));
        set.add(new TestObject("2", "3"));
        set.add(new TestObject("3", "4"));
        set.add(new TestObject("5", "6"));

        Assert.assertEquals(4, set.size());

        clear(set, redisson);
    }

    @Test
    public void testSize() {
        Redisson redisson = Redisson.create();
        Set<Integer> set = redisson.getSet("set");
        set.add(1);
        set.add(2);
        set.add(3);
        set.add(3);
        set.add(4);
        set.add(5);
        set.add(5);

        Assert.assertEquals(5, set.size());

        clear(set, redisson);
    }

}
