package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLongReactive;

public class RedissonAtomicLongReactiveTest extends BaseReactiveTest {
    
    @Test
    public void testSetIfLessThan() {
        RAtomicLongReactive al = redisson.getAtomicLong("test");
        Assertions.assertFalse(sync(al.setIfLessThan(0, 1)));
        Assertions.assertEquals(sync(al.get()).longValue(), 0L);
        
        sync(al.set(12));
        Assertions.assertTrue(sync(al.setIfLessThan(13, 1)));
        Assertions.assertEquals(sync(al.get()).longValue(), 1L);
    }
    
    @Test
    public void testSetIfGreaterThan() {
        RAtomicLongReactive al = redisson.getAtomicLong("test");
        Assertions.assertFalse(sync(al.setIfGreaterThan(0, 1)));
        Assertions.assertEquals(sync(al.get()).longValue(), 0L);
        
        sync(al.set(12));
        Assertions.assertTrue(sync(al.setIfGreaterThan(11, 1)));
        Assertions.assertEquals(sync(al.get()).longValue(), 1L);
    }
    
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
