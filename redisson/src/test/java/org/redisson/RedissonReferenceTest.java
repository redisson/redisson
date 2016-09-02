package org.redisson;

import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import org.junit.Test;
import org.redisson.api.RBucket;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonReferenceTest extends BaseTest {
    
    @Test
    public void test() {
        RBucket<Object> b1 = redisson.getBucket("b1");
        RBucket<Object> b2 = redisson.getBucket("b2");
        RBucket<Object> b3 = redisson.getBucket("b3");
        b2.set(b3);
        b1.set(redisson.getBucket("b2"));
        assertTrue(b1.get().getClass().equals(RedissonBucket.class));
        assertEquals("b3", ((RBucket) ((RBucket) b1.get()).get()).getName());
        RBucket<Object> b4 = redisson.getBucket("b4");
        b4.set(redisson.getMapCache("testCache"));
        assertTrue(b4.get() instanceof RedissonMapCache);
        ((RedissonMapCache) b4.get()).fastPut(b1, b2, 1, TimeUnit.MINUTES);
        assertEquals("b2", ((RBucket)((RedissonMapCache) b4.get()).get(b1)).getName());
    }
}
