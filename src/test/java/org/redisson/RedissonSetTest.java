package org.redisson;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class RedissonSetTest extends BaseRedissonTest {

    @Test
    public void testDuplicates() {
        Redisson redisson = Redisson.create();
        Set<TestObject> set = redisson.getSet("list");

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
