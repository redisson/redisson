package org.redisson;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RHyperLogLog;

public class RedissonHyperLogLogTest extends AbstractBaseTest {

    @Test
    public void testAdd() {
        RHyperLogLog<Integer> log = redissonRule.getSharedClient().getHyperLogLog("log");
        log.add(1);
        log.add(2);
        log.add(3);

        Assert.assertEquals(3L, log.count());
    }

    @Test
    public void testMerge() {
        RHyperLogLog<String> hll1 = redissonRule.getSharedClient().getHyperLogLog("hll1");
        Assert.assertTrue(hll1.add("foo"));
        Assert.assertTrue(hll1.add("bar"));
        Assert.assertTrue(hll1.add("zap"));
        Assert.assertTrue(hll1.add("a"));

        RHyperLogLog<String> hll2 = redissonRule.getSharedClient().getHyperLogLog("hll2");
        Assert.assertTrue(hll2.add("a"));
        Assert.assertTrue(hll2.add("b"));
        Assert.assertTrue(hll2.add("c"));
        Assert.assertTrue(hll2.add("foo"));
        Assert.assertFalse(hll2.add("c"));

        RHyperLogLog<String> hll3 = redissonRule.getSharedClient().getHyperLogLog("hll3");
        hll3.mergeWith("hll1", "hll2");

        Assert.assertEquals(6L, hll3.count());
    }
}
