package org.redisson.misc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.cache.StdCachedValue;

public class StdCachedValueTest {

    @Test
    public void testGetExpireTime() throws InterruptedException {
        long currentTimeMillis = System.currentTimeMillis();
        StdCachedValue<String, String> value = new StdCachedValue<>("test", "testValue", 150, 100);
        Assertions.assertTrue(value.getExpireTime() >= currentTimeMillis + 100);
        Assertions.assertTrue(value.getExpireTime() < currentTimeMillis + 120);
        Thread.sleep(80);
        value.getValue();
        Assertions.assertTrue(value.getExpireTime() >= currentTimeMillis + 150);
        Assertions.assertTrue(value.getExpireTime() < currentTimeMillis + 160);
    }
}
