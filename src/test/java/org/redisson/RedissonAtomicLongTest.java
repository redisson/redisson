package org.redisson;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RAtomicLong;

public class RedissonAtomicLongTest extends BaseTest {

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
