package org.redisson;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.redisson.api.*;
import org.redisson.client.protocol.ScoredEntry;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonReferenceTest extends BaseTest {

    @Test
    public void testBitSet() {
        RMap<String, RBitSet> data = redisson.getMap("data-00");
        RBitSet bs = redisson.getBitSet("data-01");
        bs.set(5);
        bs.set(7);
        data.put("a", bs);

        assertThat(data.entrySet()).hasSize(1);
        for (Map.Entry<String, RBitSet> entry : data.entrySet()) {
            assertThat(entry.getValue().get(5)).isTrue();
            assertThat(entry.getValue().get(7)).isTrue();
        }        
    }
    
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
        assertEquals("b2", ((RBucket) ((RedissonMapCache) b4.get()).get(b1)).getName());
        RBucket<Object> b5 = redisson.getBucket("b5");
        RLiveObjectService service = redisson.getLiveObjectService();

        RedissonLiveObjectServiceTest.TestREntity rlo = new RedissonLiveObjectServiceTest.TestREntity("123");
        rlo = service.persist(rlo);
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
        List<RBucket> result = (List<RBucket>) batch.execute();
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
    public void testReadAll() throws InterruptedException {
        RSetCache<RBucket<String>> b1 = redisson.getSetCache("set");
        RBucket<String> b2 = redisson.getBucket("bucket");
        b1.add(b2, 1, TimeUnit.MINUTES);
        b2.set("test1");
        assertEquals(b2.get(), b1.readAll().iterator().next().get());
        assertEquals(2, redisson.getKeys().count());

        RMapCache<String, RSetCache<RBucket<String>>> b3 = redisson.getMapCache("map");
        b3.put("1", b1);
        assertEquals(b2.get(), b3.readAllMap().get("1").iterator().next().get());
        assertEquals(b2.get(), b3.readAllEntrySet().iterator().next().getValue().iterator().next().get());
        assertEquals(b2.get(), b3.readAllValues().iterator().next().iterator().next().get());

        RMapCache<RBucket<String>, RSetCache<RBucket<String>>> b4 = redisson.getMapCache("map1");
        b4.put(b2, b1);
        assertEquals(b2.get(), b4.readAllKeySet().iterator().next().get());

        RPriorityQueue<RBucket<String>> q1 = redisson.getPriorityQueue("q1");
        q1.add(b2);
        assertEquals(b2.get(), q1.readAll().get(0).get());

        RQueue<RBucket<String>> q2 = redisson.getQueue("q2");
        q2.add(b2);
        assertEquals(b2.get(), q2.readAll().get(0).get());

        RDelayedQueue<RBucket<String>> q3 = redisson.getDelayedQueue(q2);
        q3.offer(b2, 10, TimeUnit.MINUTES);
        assertEquals(b2.get(), q3.readAll().get(0).get());

        RList<RBucket<String>> l1 = redisson.getList("l1");
        l1.add(b2);
        assertEquals(b2.get(), l1.readAll().get(0).get());
        RList<RBucket<String>> sl1 = l1.subList(0, 0);
        assertEquals(b2.get(), sl1.readAll().get(0).get());

        RLocalCachedMap<String, RBucket<String>> m1 = redisson.getLocalCachedMap("m1", LocalCachedMapOptions.defaults());
        m1.put("1", b2);
        assertEquals(b2.get(), m1.readAllMap().get("1").get());
        assertEquals(b2.get(), m1.readAllEntrySet().iterator().next().getValue().get());
        assertEquals(b2.get(), m1.readAllValues().iterator().next().get());
        m1 = redisson.getLocalCachedMap("m1", LocalCachedMapOptions.defaults());
        assertEquals(b2.get(), m1.readAllMap().get("1").get());
        assertEquals(b2.get(), m1.readAllEntrySet().iterator().next().getValue().get());
        assertEquals(b2.get(), m1.readAllValues().iterator().next().get());

        RLocalCachedMap<RBucket<String>, RBucket<String>> m2 = redisson.getLocalCachedMap("m2", LocalCachedMapOptions.defaults());
        m2.put(b2, b2);
        assertEquals(b2.get(), m2.readAllKeySet().iterator().next().get());
        m2 = redisson.getLocalCachedMap("m2", LocalCachedMapOptions.defaults());
        assertEquals(b2.get(), m2.readAllKeySet().iterator().next().get());

        RMap<String, RSetCache<RBucket<String>>> m3 = redisson.getMap("m3");
        m3.put("1", b1);
        assertEquals(b2.get(), m3.readAllMap().get("1").iterator().next().get());
        assertEquals(b2.get(), m3.readAllEntrySet().iterator().next().getValue().iterator().next().get());
        assertEquals(b2.get(), m3.readAllValues().iterator().next().iterator().next().get());

        RMap<RBucket<String>, RSetCache<RBucket<String>>> m4 = redisson.getMap("m4");
        m4.put(b2, b1);
        assertEquals(b2.get(), m4.readAllKeySet().iterator().next().get());

        //multimap
        RGeo<RBucket<String>> g1 = redisson.getGeo("g1");
        g1.add(13.361389, 38.115556, b2);
        assertEquals(b2.get(), g1.readAll().iterator().next().get());

        RScoredSortedSet<RBucket<String>> s1 = redisson.getScoredSortedSet("s1");
        s1.add(0.0, b2);
        assertEquals(b2.get(), s1.readAll().iterator().next().get());
        
        RListMultimap<String, RBucket<String>> mm1 = redisson.getListMultimap("mm1");
        mm1.put("1", b2);
        assertEquals(b2.get(), mm1.get("1").readAll().get(0).get());
        
        RListMultimap<RBucket<String>, RBucket<String>> mm2 = redisson.getListMultimap("mm2");
        mm2.put(b2, b2);
        assertEquals(b2.get(), mm2.get(b2).readAll().get(0).get());
        
        
        RSetMultimap<String, RBucket<String>> mm3 = redisson.getSetMultimap("mm3");
        mm3.put("1", b2);
        assertEquals(b2.get(), mm3.get("1").readAll().iterator().next().get());
        
        RSetMultimap<RBucket<String>, RBucket<String>> mm4 = redisson.getSetMultimap("mm4");
        mm4.put(b2, b2);
        assertEquals(b2.get(), mm4.get(b2).readAll().iterator().next().get());
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


    @Test
    public void shouldUseDefaultCodec() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        objectMapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, false);
        JsonJacksonCodec codec = new JsonJacksonCodec(objectMapper);

        Config config = new Config();
        config.setCodec(codec);
        config.useSingleServer()
                .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());

        RedissonClient redissonClient = Redisson.create(config);
        RBucket<Object> b1 = redissonClient.getBucket("b1");
        b1.set(new MyObject());
        RSet<Object> s1 = redissonClient.getSet("s1");
        assertTrue(s1.add(b1));
        assertTrue(codec == b1.getCodec());

        Config config1 = new Config();
        config1.setCodec(codec);
        config1.useSingleServer()
                .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());
        RedissonClient redissonClient1 = Redisson.create(config1);

        RSet<RBucket> s2 = redissonClient1.getSet("s1");
        RBucket<MyObject> b2 = s2.iterator(1).next();
        assertTrue(codec == b2.getCodec());
        assertTrue(b2.get() instanceof MyObject);
        redissonClient.shutdown();
        redissonClient1.shutdown();
    }

    public static class MyObject {

        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

    }
}
