package org.redisson;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RHyperLogLog;

public class RedissonHyperLogLogTest extends BaseTest {

    @Test
    public void testAdd() {
        RHyperLogLog<Integer> log = redisson.getHyperLogLog("log");
        log.add(1);
        log.add(2);
        log.add(3);

        Assert.assertEquals(3L, log.count());
    }

}
