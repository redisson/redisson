package org.redisson;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RAtomicLong;

public class RedissonAtomicLongTest extends BaseTest {

    @Test
    public void test() {
        RAtomicLong al = currentRedisson().getAtomicLong("test");
        Assert.assertEquals(0, al.get());
        Assert.assertEquals(0, al.getAndIncrement());
        Assert.assertEquals(1, al.get());

        long state = currentRedisson().getAtomicLong("test").get();
        Assert.assertEquals(1, state);
        al.set(Long.MAX_VALUE - 1000);

        long newState = currentRedisson().getAtomicLong("test").get();
        Assert.assertEquals(Long.MAX_VALUE - 1000, newState);
    }

}
