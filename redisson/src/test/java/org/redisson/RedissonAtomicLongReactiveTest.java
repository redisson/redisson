package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLongReactive;
import org.redisson.api.RedissonRxClient;

public class RedissonAtomicLongReactiveTest extends BaseReactiveTest {

    @Test
    public void testCompareAndSet() {
        RAtomicLongReactive al = redisson.getAtomicLong("test");
        Assertions.assertFalse(sync(al.compareAndSet(-1, 2)));
        Assertions.assertEquals(0, sync(al.get()).intValue());
        Assertions.assertTrue(sync(al.compareAndSet(0, 2)));
        Assertions.assertEquals(2, sync(al.get()).intValue());
    }

    @Test
    public void testSetThenIncrement() {
        RAtomicLongReactive al = redisson.getAtomicLong("test");
        sync(al.set(2));
        Assertions.assertEquals(2, sync(al.getAndIncrement()).intValue());
        Assertions.assertEquals(3, sync(al.get()).intValue());
    }

    @Test
    public void testIncrementAndGet() {
        RAtomicLongReactive al = redisson.getAtomicLong("test");
        Assertions.assertEquals(1, sync(al.incrementAndGet()).intValue());
        Assertions.assertEquals(1, sync(al.get()).intValue());
    }

    @Test
    public void testGetAndIncrement() {
        RAtomicLongReactive al = redisson.getAtomicLong("test");
        Assertions.assertEquals(0, sync(al.getAndIncrement()).intValue());
        Assertions.assertEquals(1, sync(al.get()).intValue());
    }
    
    @Test
    public void testDecrementAndGet() {
    	RAtomicLongReactive al = redisson.getAtomicLong("test");
    	Assertions.assertEquals(3, sync(al.addAndGet(3)).intValue());
    	Assertions.assertEquals(9, sync(al.addAndGet(6)).intValue());
    	Assertions.assertEquals(7, sync(al.decrementAndGet(2)).intValue());
    	Assertions.assertEquals(4, sync(al.decrementAndGet(3)).intValue());
    	Assertions.assertEquals(-1, sync(al.decrementAndGet(5)).intValue());
    }
    
    @Test
    public void test() {
        RAtomicLongReactive al = redisson.getAtomicLong("test");
        Assertions.assertEquals(0, sync(al.get()).intValue());
        Assertions.assertEquals(0, sync(al.getAndIncrement()).intValue());
        Assertions.assertEquals(1, sync(al.get()).intValue());
        Assertions.assertEquals(1, sync(al.getAndDecrement()).intValue());
        Assertions.assertEquals(0, sync(al.get()).intValue());
        Assertions.assertEquals(0, sync(al.getAndIncrement()).intValue());
        Assertions.assertEquals(1, sync(al.getAndSet(12)).intValue());
        Assertions.assertEquals(12, sync(al.get()).intValue());
        sync(al.set(1));

        long state = sync(redisson.getAtomicLong("test").get());
        Assertions.assertEquals(1, state);
        sync(al.set(Long.MAX_VALUE - 1000));

        long newState = sync(redisson.getAtomicLong("test").get());
        Assertions.assertEquals(Long.MAX_VALUE - 1000, newState);
    }

}
