package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.RedissonLocalCachedMap.CacheKey;
import org.redisson.RedissonLocalCachedMap.CacheValue;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.LocalCachedMapOptions.EvictionPolicy;
import org.redisson.api.RLocalCachedMap;
import org.redisson.misc.Cache;

import mockit.Deencapsulation;

public class RedissonLocalCachedMapTest extends BaseTest {

//    @Test
    public void testPerf() {
        LocalCachedMapOptions options = LocalCachedMapOptions.defaults().evictionPolicy(EvictionPolicy.LFU).cacheSize(100000).invalidateEntryOnChange(true);
        Map<String, Integer> map = redisson.getLocalCachedMap("test", options);
        
//        Map<String, Integer> map = redisson.getMap("test");

        
        for (int i = 0; i < 100000; i++) {
            map.put("" + i, i);
        }
        
        long s = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < 100000; j++) {
                map.get("" + j);
            }
        }
        System.out.println(System.currentTimeMillis() - s);

    }
    
    @Test
    public void testInvalidationOnUpdate() throws InterruptedException {
        LocalCachedMapOptions options = LocalCachedMapOptions.defaults().evictionPolicy(EvictionPolicy.LFU).cacheSize(5).invalidateEntryOnChange(true);
        RLocalCachedMap<String, Integer> map1 = redisson.getLocalCachedMap("test", options);
        Cache<CacheKey, CacheValue> cache1 = Deencapsulation.getField(map1, "cache");
        
        RLocalCachedMap<String, Integer> map2 = redisson.getLocalCachedMap("test", options);
        Cache<CacheKey, CacheValue> cache2 = Deencapsulation.getField(map2, "cache");
        
        map1.put("1", 1);
        map1.put("2", 2);
        
        assertThat(map2.get("1")).isEqualTo(1);
        assertThat(map2.get("2")).isEqualTo(2);
        
        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);

        map1.put("1", 3);
        map2.put("2", 4);
        Thread.sleep(50);
        
        assertThat(cache1.size()).isEqualTo(0);
        assertThat(cache2.size()).isEqualTo(0);
    }
    
    @Test
    public void testNoInvalidationOnUpdate() throws InterruptedException {
        LocalCachedMapOptions options = LocalCachedMapOptions.defaults().evictionPolicy(EvictionPolicy.LFU).cacheSize(5).invalidateEntryOnChange(false);
        RLocalCachedMap<String, Integer> map1 = redisson.getLocalCachedMap("test", options);
        Cache<CacheKey, CacheValue> cache1 = Deencapsulation.getField(map1, "cache");
        
        RLocalCachedMap<String, Integer> map2 = redisson.getLocalCachedMap("test", options);
        Cache<CacheKey, CacheValue> cache2 = Deencapsulation.getField(map2, "cache");
        
        map1.put("1", 1);
        map1.put("2", 2);
        
        assertThat(map2.get("1")).isEqualTo(1);
        assertThat(map2.get("2")).isEqualTo(2);
        
        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);

        map1.put("1", 3);
        map2.put("2", 4);
        Thread.sleep(50);
        
        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);
    }
    
    @Test
    public void testNoInvalidationOnRemove() throws InterruptedException {
        LocalCachedMapOptions options = LocalCachedMapOptions.defaults().evictionPolicy(EvictionPolicy.LFU).cacheSize(5).invalidateEntryOnChange(false);
        RLocalCachedMap<String, Integer> map1 = redisson.getLocalCachedMap("test", options);
        Cache<CacheKey, CacheValue> cache1 = Deencapsulation.getField(map1, "cache");
        
        RLocalCachedMap<String, Integer> map2 = redisson.getLocalCachedMap("test", options);
        Cache<CacheKey, CacheValue> cache2 = Deencapsulation.getField(map2, "cache");
        
        map1.put("1", 1);
        map1.put("2", 2);
        
        assertThat(map2.get("1")).isEqualTo(1);
        assertThat(map2.get("2")).isEqualTo(2);
        
        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);

        map1.remove("1");
        map2.remove("2");
        Thread.sleep(50);
        
        assertThat(cache1.size()).isEqualTo(1);
        assertThat(cache2.size()).isEqualTo(1);
    }
    
    @Test
    public void testInvalidationOnRemove() throws InterruptedException {
        LocalCachedMapOptions options = LocalCachedMapOptions.defaults().evictionPolicy(EvictionPolicy.LFU).cacheSize(5).invalidateEntryOnChange(true);
        RLocalCachedMap<String, Integer> map1 = redisson.getLocalCachedMap("test", options);
        Cache<CacheKey, CacheValue> cache1 = Deencapsulation.getField(map1, "cache");
        
        RLocalCachedMap<String, Integer> map2 = redisson.getLocalCachedMap("test", options);
        Cache<CacheKey, CacheValue> cache2 = Deencapsulation.getField(map2, "cache");
        
        map1.put("1", 1);
        map1.put("2", 2);
        
        assertThat(map2.get("1")).isEqualTo(1);
        assertThat(map2.get("2")).isEqualTo(2);
        
        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);

        map1.remove("1");
        map2.remove("2");
        Thread.sleep(50);
        
        assertThat(cache1.size()).isEqualTo(0);
        assertThat(cache2.size()).isEqualTo(0);
    }
    
    @Test
    public void testLFU() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults().evictionPolicy(EvictionPolicy.LFU).cacheSize(5));
        Cache<CacheKey, CacheValue> cache = Deencapsulation.getField(map, "cache");

        map.put("12", 1);
        map.put("14", 2);
        map.put("15", 3);
        map.put("16", 4);
        map.put("17", 5);
        map.put("18", 6);
        
        assertThat(cache.size()).isEqualTo(5);
        assertThat(map.size()).isEqualTo(6);
        assertThat(map.keySet()).containsOnly("12", "14", "15", "16", "17", "18");
        assertThat(map.values()).containsOnly(1, 2, 3, 4, 5, 6);
    }
    
    @Test
    public void testLRU() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults().evictionPolicy(EvictionPolicy.LRU).cacheSize(5));
        Cache<CacheKey, CacheValue> cache = Deencapsulation.getField(map, "cache");

        map.put("12", 1);
        map.put("14", 2);
        map.put("15", 3);
        map.put("16", 4);
        map.put("17", 5);
        map.put("18", 6);
        
        assertThat(cache.size()).isEqualTo(5);
        assertThat(map.size()).isEqualTo(6);
        assertThat(map.keySet()).containsOnly("12", "14", "15", "16", "17", "18");
        assertThat(map.values()).containsOnly(1, 2, 3, 4, 5, 6);
    }

    @Test
    public void testSize() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        Cache<CacheKey, CacheValue> cache = Deencapsulation.getField(map, "cache");

        map.put("12", 1);
        map.put("14", 2);
        map.put("15", 3);
        
        assertThat(cache.size()).isEqualTo(3);
        assertThat(map.size()).isEqualTo(3);
    }

    
    @Test
    public void testPut() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());

        map.put("12", 1);
        map.put("14", 2);
        map.put("15", 3);
        
        Deencapsulation.setField(map, "map", null);
     
        assertThat(map.get("12")).isEqualTo(1);
        assertThat(map.get("14")).isEqualTo(2);
        assertThat(map.get("15")).isEqualTo(3);
    }
    
    @Test
    public void testRemove() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        Cache<CacheKey, CacheValue> cache = Deencapsulation.getField(map, "cache");
        map.put("12", 1);

        assertThat(cache.size()).isEqualTo(1);
        
        assertThat(map.remove("12")).isEqualTo(1);
        
        assertThat(cache.size()).isEqualTo(0);
        
        assertThat(map.remove("14")).isNull();
    }

    @Test
    public void testFastRemoveAsync() throws InterruptedException, ExecutionException {
        RLocalCachedMap<Integer, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        map.put(1, 3);
        map.put(7, 8);

        assertThat(map.fastRemoveAsync(1).get()).isTrue();
        assertThat(map.fastRemoveAsync(2).get()).isFalse();
        assertThat(map.size()).isEqualTo(1);
    }

    @Test
    public void testFastPut() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        Assert.assertTrue(map.fastPut("1", 2));
        assertThat(map.get("1")).isEqualTo(2);
        Assert.assertFalse(map.fastPut("1", 3));
        assertThat(map.get("1")).isEqualTo(3);
        Assert.assertEquals(1, map.size());
    }

    
}
