package org.redisson;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import org.junit.Test;
import org.redisson.api.RBatch;
import org.redisson.api.RBatchReactive;
import org.redisson.api.RBucket;
import org.redisson.api.RBucketAsync;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RLiveObject;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSet;
import org.redisson.client.protocol.ScoredEntry;

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
        RBucket<Object> b5 = redisson.getBucket("b5");
        RedissonLiveObjectServiceTest.TestREntity rlo = redisson.getLiveObjectService().create(RedissonLiveObjectServiceTest.TestREntity.class);
        rlo.setName("t1");
        rlo.setValue("t2");
        b5.set(rlo);
        assertTrue(redisson.getBucket("b5").get() instanceof RLiveObject);
        assertEquals("t1", ((RedissonLiveObjectServiceTest.TestREntity) redisson.getBucket("b5").get()).getName());
        assertEquals("t2", ((RedissonLiveObjectServiceTest.TestREntity) redisson.getBucket("b5").get()).getValue());
        
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
    
    @Test
    public void testNormalToReactive() {
        RBatch batch = redisson.createBatch();
        RBucketAsync<Object> b1 = batch.getBucket("b1");
        RBucketAsync<Object> b2 = batch.getBucket("b2");
        RBucketAsync<Object> b3 = batch.getBucket("b3");
        b2.setAsync(b3);
        b1.setAsync(b2);
        b3.setAsync(b1);
        batch.execute();
        
        RBatchReactive b = Redisson.createReactive(redisson.getConfig()).createBatch();
        b.getBucket("b1").get();
        b.getBucket("b2").get();
        b.getBucket("b3").get();
        List<RBucketReactive> result = (List<RBucketReactive>) BaseReactiveTest.sync(b.execute());
        assertEquals("b2", result.get(0).getName());
        assertEquals("b3", result.get(1).getName());
        assertEquals("b1", result.get(2).getName());
    }
    
    @Test
    public void testWithList() {
        RSet<RBucket<String>> b1 = redisson.getSet("set");
        RBucket<String> b2 = redisson.getBucket("bucket");

        b1.add(b2);
        b2.set("test1");
        assertEquals(b2.get(), b1.iterator().next().get());
        assertEquals(2, redisson.getKeys().count());
    }
    
    @Test
    public void testWithZSet() {
        RScoredSortedSet<RBucket<String>> b1 = redisson.getScoredSortedSet("set");
        RBucket<String> b2 = redisson.getBucket("bucket");
        b1.add(0.0, b2);
        b2.set("test1");
        assertEquals(b2.get(), b1.iterator().next().get());
        assertEquals(2, redisson.getKeys().count());
        Collection<ScoredEntry<RBucket<String>>> entryRange = b1.entryRange(0, 1);
        assertEquals(b2.get(), entryRange.iterator().next().getValue().get());
    }
    
    @Test
    public void testWithMap() {
        RMap<RBucket<RMap>, RBucket<RMap>> map = redisson.getMap("set");
        RBucket<RMap> b1 = redisson.getBucket("bucket1");
        RBucket<RMap> b2 = redisson.getBucket("bucket2");

        map.put(b1, b2);
        assertEquals(b2.get(), map.values().iterator().next().get());
        assertEquals(b1.get(), map.keySet().iterator().next().get());
        assertNotEquals(3, redisson.getKeys().count());
        assertEquals(1, redisson.getKeys().count());
        b1.set(map);
        b2.set(map);
        assertNotEquals(1, redisson.getKeys().count());
        assertEquals(3, redisson.getKeys().count());
    }
}
