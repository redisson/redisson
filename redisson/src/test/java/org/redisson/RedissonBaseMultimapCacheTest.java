package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RMultimapCache;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class RedissonBaseMultimapCacheTest extends BaseTest {

    abstract RMultimapCache<String, String> getMultimapCache(String name);
    
    @Test
    public void testRemoveAll() {
        RMultimapCache<String, String> multimap = getMultimapCache("test");
        multimap.put("1", "1");
        multimap.put("1", "2");

        multimap.removeAll("1");
        assertThat(multimap.size()).isZero();
    }
    
    @Test
    public void testContains() {
        RMultimapCache<String, String> multimap = getMultimapCache("test");
        multimap.put("1", "1");
        multimap.put("1", "2");
        multimap.put("1", "3");
        
        assertThat(multimap.containsKey("1")).isTrue();
        assertThat(multimap.containsKey("2")).isFalse();
        
        assertThat(multimap.containsValue("1")).isTrue();
        assertThat(multimap.containsValue("3")).isTrue();
        assertThat(multimap.containsValue("4")).isFalse();

        assertThat(multimap.containsEntry("1", "1")).isTrue();
        assertThat(multimap.containsEntry("1", "3")).isTrue();
        assertThat(multimap.containsEntry("1", "4")).isFalse();
    }

    @Test
    public void testContainsExpired() throws InterruptedException {
        RMultimapCache<String, String> multimap = getMultimapCache("test");
        multimap.put("1", "1");
        multimap.put("1", "2");
        multimap.put("1", "3");
        multimap.expireKey("1", 1, TimeUnit.SECONDS);
        
        Thread.sleep(1000);
        
        assertThat(multimap.containsKey("1")).isFalse();
        assertThat(multimap.containsKey("2")).isFalse();
        
        assertThat(multimap.containsValue("1")).isFalse();
        assertThat(multimap.containsValue("3")).isFalse();
        assertThat(multimap.containsValue("4")).isFalse();

        assertThat(multimap.containsEntry("1", "1")).isFalse();
        assertThat(multimap.containsEntry("1", "3")).isFalse();
        assertThat(multimap.containsEntry("1", "4")).isFalse();
    }

    @Test
    public void testGetAll() throws InterruptedException {
        RMultimapCache<String, String> multimap = getMultimapCache("test");
        multimap.put("1", "1");
        multimap.put("1", "2");
        multimap.put("1", "3");
        
        assertThat(multimap.getAll("1")).containsOnlyOnce("1", "2", "3");
    }

    @Test
    public void testGetAllExpired() throws InterruptedException {
        RMultimapCache<String, String> multimap = getMultimapCache("test");
        multimap.put("1", "1");
        multimap.put("1", "2");
        multimap.put("1", "3");
        multimap.expireKey("1", 1, TimeUnit.SECONDS);
        
        Thread.sleep(1000);
        
        assertThat(multimap.getAll("1")).isEmpty();
    }
    
    @Test
    public void testValuesExpired() throws InterruptedException {
        RMultimapCache<String, String> multimap = getMultimapCache("test");
        multimap.put("1", "1");
        multimap.put("1", "2");
        multimap.put("1", "3");
        multimap.expireKey("1", 1, TimeUnit.SECONDS);
        
        Thread.sleep(1500);
        
        assertThat(multimap.get("1").size()).isZero();
        assertThat(multimap.get("1").isEmpty()).isTrue();
        assertThat(multimap.get("1").remove("3")).isFalse();
        assertThat(multimap.get("1").contains("3")).isFalse();
        assertThat(multimap.get("1").retainAll(Arrays.asList("1"))).isFalse();
        assertThat(multimap.get("1").containsAll(Arrays.asList("1"))).isFalse();
        assertThat(multimap.get("1").removeAll(Arrays.asList("1"))).isFalse();
    }

    @Test
    public void testScheduler() throws InterruptedException {
        RMultimapCache<String, String> cache = getMultimapCache("simple33");
        assertThat(cache.put("1", "1")).isTrue();
        assertThat(cache.put("1", "2")).isTrue();
        assertThat(cache.put("1", "3")).isTrue();
        assertThat(cache.put("2", "1")).isTrue();
        assertThat(cache.put("2", "2")).isTrue();
        assertThat(cache.put("2", "3")).isTrue();
        
        assertThat(cache.expireKey("1", 2, TimeUnit.SECONDS)).isTrue();
        assertThat(cache.expireKey("2", 3, TimeUnit.SECONDS)).isTrue();
        assertThat(cache.expireKey("3", 3, TimeUnit.SECONDS)).isFalse();
        
        assertThat(cache.size()).isEqualTo(6);
        
        Thread.sleep(10000);

        assertThat(cache.size()).isZero();

    }

    @Test
    public void testExpire() throws InterruptedException {
        RMultimapCache<String, String> map = getMultimapCache("simple");
        map.put("1", "2");
        map.put("2", "3");

        map.expire(Duration.ofMillis(100));

        Thread.sleep(500);

        assertThat(map.size()).isZero();
    }

    @Test
    public void testExpireAt() throws InterruptedException {
        RMultimapCache<String, String> map = getMultimapCache("simple");
        map.put("1", "2");
        map.put("2", "3");

        map.expireAt(System.currentTimeMillis() + 100);

        Thread.sleep(500);

        assertThat(map.size()).isZero();
    }

    @Test
    public void testClearExpire() throws InterruptedException {
        RMultimapCache<String, String> map = getMultimapCache("simple");
        map.put("1", "2");
        map.put("2", "3");

        map.expireAt(System.currentTimeMillis() + 100);

        map.clearExpire();

        Thread.sleep(500);

        assertThat(map.size()).isEqualTo(2);
    }

    @Test
    public void testDelete() {
        RMultimapCache<String, String> map = getMultimapCache("simple");
        map.put("1", "2");
        map.put("2", "3");
        assertThat(map.delete()).isTrue();
        
        RMultimapCache<String, String> map2 = getMultimapCache("simple1");
        assertThat(map2.delete()).isFalse();
    }
    
}
