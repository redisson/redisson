package org.redisson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.redisson.rule.TestUtil.sync;

import java.util.List;

import org.junit.Test;
import org.redisson.api.RBatch;
import org.redisson.api.RBatchReactive;
import org.redisson.api.RBucket;
import org.redisson.api.RBucketReactive;
import org.redisson.reactive.RedissonBucketReactive;
import org.redisson.reactive.RedissonMapCacheReactive;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonReferenceReactiveTest extends AbstractBaseTest {
    
    @Test
    public void test() throws InterruptedException {
        RBucketReactive<Object> b1 = redissonRule.getSharedReactiveClient().getBucket("b1");
        RBucketReactive<Object> b2 = redissonRule.getSharedReactiveClient().getBucket("b2");
        RBucketReactive<Object> b3 = redissonRule.getSharedReactiveClient().getBucket("b3");
        sync(b2.set(b3));
        sync(b1.set(redissonRule.getSharedReactiveClient().getBucket("b2")));
        assertTrue(sync(b1.get()).getClass().equals(RedissonBucketReactive.class));
        assertEquals("b3", ((RBucketReactive) sync(((RBucketReactive) sync(b1.get())).get())).getName());
        RBucketReactive<Object> b4 = redissonRule.getSharedReactiveClient().getBucket("b4");
        sync(b4.set(redissonRule.getSharedReactiveClient().getMapCache("testCache")));
        assertTrue(sync(b4.get()) instanceof RedissonMapCacheReactive);
        sync(((RedissonMapCacheReactive) sync(b4.get())).fastPut(b1, b2));
        assertEquals("b2", ((RBucketReactive) sync(((RedissonMapCacheReactive) sync(b4.get())).get(b1))).getName());
    }
    
    @Test
    public void testBatch() throws InterruptedException {
        RBatchReactive batch = redissonRule.getSharedReactiveClient().createBatch();
        RBucketReactive<Object> b1 = batch.getBucket("b1");
        RBucketReactive<Object> b2 = batch.getBucket("b2");
        RBucketReactive<Object> b3 = batch.getBucket("b3");
        b2.set(b3);
        b1.set(b2);
        b3.set(b1);
        sync(batch.execute());
        
        batch = redissonRule.getSharedReactiveClient().createBatch();
        batch.getBucket("b1").get();
        batch.getBucket("b2").get();
        batch.getBucket("b3").get();
        List<RBucketReactive> result = (List<RBucketReactive>) sync(batch.execute());
        assertEquals("b2", result.get(0).getName());
        assertEquals("b3", result.get(1).getName());
        assertEquals("b1", result.get(2).getName());
    }
    
    @Test
    public void testReactiveToNormal() throws InterruptedException {
        RBatchReactive batch = redissonRule.getSharedReactiveClient().createBatch();
        RBucketReactive<Object> b1 = batch.getBucket("b1");
        RBucketReactive<Object> b2 = batch.getBucket("b2");
        RBucketReactive<Object> b3 = batch.getBucket("b3");
        b2.set(b3);
        b1.set(b2);
        b3.set(b1);
        sync(batch.execute());
        
        RBatch b = redissonRule.getSharedClient().createBatch();
        b.getBucket("b1").getAsync();
        b.getBucket("b2").getAsync();
        b.getBucket("b3").getAsync();
        List<RBucket> result = (List<RBucket>)b.execute();
        assertEquals("b2", result.get(0).getName());
        assertEquals("b3", result.get(1).getName());
        assertEquals("b1", result.get(2).getName());
    }
}
