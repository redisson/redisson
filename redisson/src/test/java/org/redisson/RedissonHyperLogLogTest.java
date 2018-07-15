package org.redisson;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RHyperLogLog;

public class RedissonHyperLogLogTest extends BaseTest {

    @Test
    public void testAddAll() {
        RHyperLogLog<Integer> log = redisson.getHyperLogLog("log");
        log.addAll(Arrays.asList(1, 2, 3));
        
        Assert.assertEquals(3L, log.count());
    }
    
    @Test
    public void testAdd() {
        RHyperLogLog<Integer> log = redisson.getHyperLogLog("log");
        log.add(1);
        log.add(2);
        log.add(3);

        Assert.assertEquals(3L, log.count());
    }

    @Test
    public void testMerge() {
        RHyperLogLog<String> hll1 = redisson.getHyperLogLog("hll1");
        Assert.assertTrue(hll1.add("foo"));
        Assert.assertTrue(hll1.add("bar"));
        Assert.assertTrue(hll1.add("zap"));
        Assert.assertTrue(hll1.add("a"));

        RHyperLogLog<String> hll2 = redisson.getHyperLogLog("hll2");
        Assert.assertTrue(hll2.add("a"));
        Assert.assertTrue(hll2.add("b"));
        Assert.assertTrue(hll2.add("c"));
        Assert.assertTrue(hll2.add("foo"));
        Assert.assertFalse(hll2.add("c"));

        RHyperLogLog<String> hll3 = redisson.getHyperLogLog("hll3");
        hll3.mergeWith("hll1", "hll2");

        Assert.assertEquals(6L, hll3.count());
    }


}
