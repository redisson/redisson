package org.redisson;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RHyperLogLogReactive;

public class RedissonHyperLogLogReactiveTest extends BaseReactiveTest {

    @Test
    public void testAdd() {
        RHyperLogLogReactive<Integer> log = redisson.getHyperLogLog("log");
        sync(log.add(1));
        sync(log.add(2));
        sync(log.add(3));

        Assert.assertEquals(3L, sync(log.count()).longValue());
    }

    @Test
    public void testMerge() {
        RHyperLogLogReactive<String> hll1 = redisson.getHyperLogLog("hll1");
        Assert.assertTrue(sync(hll1.add("foo")));
        Assert.assertTrue(sync(hll1.add("bar")));
        Assert.assertTrue(sync(hll1.add("zap")));
        Assert.assertTrue(sync(hll1.add("a")));

        RHyperLogLogReactive<String> hll2 = redisson.getHyperLogLog("hll2");
        Assert.assertTrue(sync(hll2.add("a")));
        Assert.assertTrue(sync(hll2.add("b")));
        Assert.assertTrue(sync(hll2.add("c")));
        Assert.assertTrue(sync(hll2.add("foo")));
        Assert.assertFalse(sync(hll2.add("c")));

        RHyperLogLogReactive<String> hll3 = redisson.getHyperLogLog("hll3");
        sync(hll3.mergeWith("hll1", "hll2"));

        Assert.assertEquals(6L, sync(hll3.count()).longValue());
    }


}
