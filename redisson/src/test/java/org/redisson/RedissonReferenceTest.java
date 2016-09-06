package org.redisson;

import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import org.junit.Test;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RBucketAsync;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonReferenceTest extends BaseTest {
    
    @Test
    public void testBasic() {
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
    
    @Test
    public void testBatch() {
        RBatch batch = redisson.createBatch();
        RBucketAsync<Object> b1 = batch.getBucket("b1");
        RBucketAsync<Object> b2 = batch.getBucket("b2");
        RBucketAsync<Object> b3 = batch.getBucket("b3");
        b2.setAsync(b3);
        b1.setAsync(b2);
        b3.setAsync(b1);
        batch.execute();
        
        batch = redisson.createBatch();
        batch.getBucket("b1").getAsync();
        batch.getBucket("b2").getAsync();
        batch.getBucket("b3").getAsync();
        List<RBucket> result = (List<RBucket>)batch.execute();
        assertEquals("b2", result.get(0).getName());
        assertEquals("b3", result.get(1).getName());
        assertEquals("b1", result.get(2).getName());
    }
    
}
