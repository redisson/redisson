package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RHyperLogLogReactive;

public class RedissonHyperLogLogReactiveTest extends BaseReactiveTest {

    @Test
    public void testAdd() {
        RHyperLogLogReactive<Integer> log = redisson.getHyperLogLog("log");
        sync(log.add(1));
        sync(log.add(2));
        sync(log.add(3));

        Assertions.assertEquals(3L, sync(log.count()).longValue());
    }

    @Test
    public void testMerge() {
        RHyperLogLogReactive<String> hll1 = redisson.getHyperLogLog("hll1");
        Assertions.assertTrue(sync(hll1.add("foo")));
        Assertions.assertTrue(sync(hll1.add("bar")));
        Assertions.assertTrue(sync(hll1.add("zap")));
        Assertions.assertTrue(sync(hll1.add("a")));

        RHyperLogLogReactive<String> hll2 = redisson.getHyperLogLog("hll2");
        Assertions.assertTrue(sync(hll2.add("a")));
        Assertions.assertTrue(sync(hll2.add("b")));
        Assertions.assertTrue(sync(hll2.add("c")));
        Assertions.assertTrue(sync(hll2.add("foo")));
        Assertions.assertFalse(sync(hll2.add("c")));

        RHyperLogLogReactive<String> hll3 = redisson.getHyperLogLog("hll3");
        sync(hll3.mergeWith("hll1", "hll2"));

        Assertions.assertEquals(6L, sync(hll3.count()).longValue());
    }


}
