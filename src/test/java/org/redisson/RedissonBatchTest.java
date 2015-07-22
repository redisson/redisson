package org.redisson;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RBatch;

public class RedissonBatchTest extends BaseTest {

    @Test
    public void test() {
        RBatch batch = redisson.createBatch();
        batch.getMap("test").fastPutAsync("1", "2");
        batch.getMap("test").fastPutAsync("2", "3");
        batch.getAtomicLongAsync("counter").incrementAndGetAsync();
        batch.getAtomicLongAsync("counter").incrementAndGetAsync();
        batch.execute();

        Map<String, String> map = new HashMap<String, String>();
        map.put("1", "2");
        map.put("2", "3");
        Assert.assertEquals(redisson.getMap("test"), map);

        Assert.assertEquals(redisson.getAtomicLong("counter").get(), 2);
    }

}
