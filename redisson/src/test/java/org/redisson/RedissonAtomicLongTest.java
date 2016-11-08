package org.redisson;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RAtomicLong;

public class RedissonAtomicLongTest extends AbstractBaseTest {

    @Test
    public void testCompareAndSetZero() {
        RAtomicLong al = redissonRule.getSharedClient().getAtomicLong("test");
        Assert.assertTrue(al.compareAndSet(0, 2));
        Assert.assertEquals(2, al.get());

        RAtomicLong al2 = redissonRule.getSharedClient().getAtomicLong("test1");
        al2.set(0);
        Assert.assertTrue(al2.compareAndSet(0, 2));
        Assert.assertEquals(2, al2.get());

    }


    @Test
    public void testCompareAndSet() {
        RAtomicLong al = redissonRule.getSharedClient().getAtomicLong("test");
        Assert.assertFalse(al.compareAndSet(-1, 2));
        Assert.assertEquals(0, al.get());
        Assert.assertTrue(al.compareAndSet(0, 2));
        Assert.assertEquals(2, al.get());
    }

    @Test
    public void testSetThenIncrement() {
        RAtomicLong al = redissonRule.getSharedClient().getAtomicLong("test");
        al.set(2);
        Assert.assertEquals(2, al.getAndIncrement());
        Assert.assertEquals(3, al.get());
    }

    @Test
    public void testIncrementAndGet() {
        RAtomicLong al = redissonRule.getSharedClient().getAtomicLong("test");
        Assert.assertEquals(1, al.incrementAndGet());
        Assert.assertEquals(1, al.get());
    }

    @Test
    public void testGetAndIncrement() {
        RAtomicLong al = redissonRule.getSharedClient().getAtomicLong("test");
        Assert.assertEquals(0, al.getAndIncrement());
        Assert.assertEquals(1, al.get());
    }

    @Test
    public void test() {
        RAtomicLong al = redissonRule.getSharedClient().getAtomicLong("test");
        Assert.assertEquals(0, al.get());
        Assert.assertEquals(0, al.getAndIncrement());
        Assert.assertEquals(1, al.get());
        Assert.assertEquals(1, al.getAndDecrement());
        Assert.assertEquals(0, al.get());
        Assert.assertEquals(0, al.getAndIncrement());
        Assert.assertEquals(1, al.getAndSet(12));
        Assert.assertEquals(12, al.get());
        al.set(1);

        long state = redissonRule.getSharedClient().getAtomicLong("test").get();
        Assert.assertEquals(1, state);
        al.set(Long.MAX_VALUE - 1000);

        long newState = redissonRule.getSharedClient().getAtomicLong("test").get();
        Assert.assertEquals(Long.MAX_VALUE - 1000, newState);
    }

}
