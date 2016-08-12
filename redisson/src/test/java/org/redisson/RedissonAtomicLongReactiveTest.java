package org.redisson;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RAtomicLongReactive;

public class RedissonAtomicLongReactiveTest extends BaseReactiveTest {

    @Test
    public void testCompareAndSet() {
        RAtomicLongReactive al = redisson.getAtomicLong("test");
        Assert.assertFalse(sync(al.compareAndSet(-1, 2)));
        Assert.assertEquals(0, sync(al.get()).intValue());
        Assert.assertTrue(sync(al.compareAndSet(0, 2)));
        Assert.assertEquals(2, sync(al.get()).intValue());
    }

    @Test
    public void testSetThenIncrement() {
        RAtomicLongReactive al = redisson.getAtomicLong("test");
        sync(al.set(2));
        Assert.assertEquals(2, sync(al.getAndIncrement()).intValue());
        Assert.assertEquals(3, sync(al.get()).intValue());
    }

    @Test
    public void testIncrementAndGet() {
        RAtomicLongReactive al = redisson.getAtomicLong("test");
        Assert.assertEquals(1, sync(al.incrementAndGet()).intValue());
        Assert.assertEquals(1, sync(al.get()).intValue());
    }

    @Test
    public void testGetAndIncrement() {
        RAtomicLongReactive al = redisson.getAtomicLong("test");
        Assert.assertEquals(0, sync(al.getAndIncrement()).intValue());
        Assert.assertEquals(1, sync(al.get()).intValue());
    }

    @Test
    public void test() {
        RAtomicLongReactive al = redisson.getAtomicLong("test");
        Assert.assertEquals(0, sync(al.get()).intValue());
        Assert.assertEquals(0, sync(al.getAndIncrement()).intValue());
        Assert.assertEquals(1, sync(al.get()).intValue());
        Assert.assertEquals(1, sync(al.getAndDecrement()).intValue());
        Assert.assertEquals(0, sync(al.get()).intValue());
        Assert.assertEquals(0, sync(al.getAndIncrement()).intValue());
        Assert.assertEquals(1, sync(al.getAndSet(12)).intValue());
        Assert.assertEquals(12, sync(al.get()).intValue());
        sync(al.set(1));

        long state = sync(redisson.getAtomicLong("test").get());
        Assert.assertEquals(1, state);
        sync(al.set(Long.MAX_VALUE - 1000));

        long newState = sync(redisson.getAtomicLong("test").get());
        Assert.assertEquals(Long.MAX_VALUE - 1000, newState);
    }

}
