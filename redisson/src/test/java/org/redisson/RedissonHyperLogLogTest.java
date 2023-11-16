package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RHyperLogLog;

import java.util.Arrays;

public class RedissonHyperLogLogTest extends RedisDockerTest {

    @Test
    public void testAddAll() {
        RHyperLogLog<Integer> log = redisson.getHyperLogLog("log");
        log.addAll(Arrays.asList(1, 2, 3));
        
        Assertions.assertEquals(3L, log.count());
    }
    
    @Test
    public void testAdd() {
        RHyperLogLog<Integer> log = redisson.getHyperLogLog("log");
        log.add(1);
        log.add(2);
        log.add(3);

        Assertions.assertEquals(3L, log.count());
    }

    @Test
    public void testMerge() {
        RHyperLogLog<String> hll1 = redisson.getHyperLogLog("hll1");
        Assertions.assertTrue(hll1.add("foo"));
        Assertions.assertTrue(hll1.add("bar"));
        Assertions.assertTrue(hll1.add("zap"));
        Assertions.assertTrue(hll1.add("a"));

        RHyperLogLog<String> hll2 = redisson.getHyperLogLog("hll2");
        Assertions.assertTrue(hll2.add("a"));
        Assertions.assertTrue(hll2.add("b"));
        Assertions.assertTrue(hll2.add("c"));
        Assertions.assertTrue(hll2.add("foo"));
        Assertions.assertFalse(hll2.add("c"));

        RHyperLogLog<String> hll3 = redisson.getHyperLogLog("hll3");
        hll3.mergeWith("hll1", "hll2");

        Assertions.assertEquals(6L, hll3.count());
    }


}
