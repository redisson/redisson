package org.redisson;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RAtomicLong;

public class RedissonAtomicLongTest extends BaseTest {

    @Test
    public void testCompareAndSetZero() {
        RAtomicLong al = redisson.getAtomicLong("test");
        Assert.assertTrue(al.compareAndSet(0, 2));
        Assert.assertEquals(2, al.get());

        RAtomicLong al2 = redisson.getAtomicLong("test1");
        al2.set(0);
        Assert.assertTrue(al2.compareAndSet(0, 2));
        Assert.assertEquals(2, al2.get());

    }


    @Test
    public void testCompareAndSet() {
        RAtomicLong al = redisson.getAtomicLong("test");
        Assert.assertFalse(al.compareAndSet(-1, 2));
        Assert.assertEquals(0, al.get());
        Assert.assertTrue(al.compareAndSet(0, 2));
        Assert.assertEquals(2, al.get());
    }

    @Test
    public void testSetThenIncrement() {
        RAtomicLong al = redisson.getAtomicLong("test");
        al.set(2);
        Assert.assertEquals(2, al.getAndIncrement());
        Assert.assertEquals(3, al.get());
    }

    @Test
    public void testIncrementAndGet() {
        RAtomicLong al = redisson.getAtomicLong("test");
        Assert.assertEquals(1, al.incrementAndGet());
        Assert.assertEquals(1, al.get());
    }

    @Test
    public void testGetAndIncrement() {
        RAtomicLong al = redisson.getAtomicLong("test");
        Assert.assertEquals(0, al.getAndIncrement());
        Assert.assertEquals(1, al.get());
    }

    @Test
    public void test() {
        RAtomicLong al = redisson.getAtomicLong("test");
        Assert.assertEquals(0, al.get());
        Assert.assertEquals(0, al.getAndIncrement());
        Assert.assertEquals(1, al.get());
        Assert.assertEquals(1, al.getAndDecrement());
        Assert.assertEquals(0, al.get());
        Assert.assertEquals(0, al.getAndIncrement());
        Assert.assertEquals(1, al.getAndSet(12));
        Assert.assertEquals(12, al.get());
        al.set(1);

        long state = redisson.getAtomicLong("test").get();
        Assert.assertEquals(1, state);
        al.set(Long.MAX_VALUE - 1000);

        long newState = redisson.getAtomicLong("test").get();
        Assert.assertEquals(Long.MAX_VALUE - 1000, newState);
    }

}
