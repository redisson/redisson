package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.redisson.core.RMultimapCache;

public class RedissonListMultimapCacheTest extends BaseTest {

    @Test
    public void testContains() {
        RMultimapCache<String, String> multimap = redisson.getListMultimapCache("test");
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
        RMultimapCache<String, String> multimap = redisson.getListMultimapCache("test");
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
        RMultimapCache<String, String> multimap = redisson.getListMultimapCache("test");
        multimap.put("1", "1");
        multimap.put("1", "2");
        multimap.put("1", "3");
        
        assertThat(multimap.getAll("1")).containsOnlyOnce("1", "2", "3");
    }

    @Test
    public void testGetAllExpired() throws InterruptedException {
        RMultimapCache<String, String> multimap = redisson.getListMultimapCache("test");
        multimap.put("1", "1");
        multimap.put("1", "2");
        multimap.put("1", "3");
        multimap.expireKey("1", 1, TimeUnit.SECONDS);
        
        Thread.sleep(1000);
        
        assertThat(multimap.getAll("1")).isEmpty();
    }

    @Test
    public void testValues() throws InterruptedException {
        RMultimapCache<String, String> multimap = redisson.getListMultimapCache("test");
        multimap.put("1", "1");
        multimap.put("1", "2");
        multimap.put("1", "3");
        multimap.put("1", "3");
        
        assertThat(multimap.get("1").size()).isEqualTo(4);
        assertThat(multimap.get("1")).containsExactly("1", "2", "3", "3");
        assertThat(multimap.get("1").remove("3")).isTrue();
        assertThat(multimap.get("1").remove("3")).isTrue();
        assertThat(multimap.get("1").contains("3")).isFalse();
        assertThat(multimap.get("1").contains("2")).isTrue();
        assertThat(multimap.get("1").containsAll(Arrays.asList("1"))).isTrue();
        assertThat(multimap.get("1").containsAll(Arrays.asList("1", "2"))).isTrue();
        assertThat(multimap.get("1").retainAll(Arrays.asList("1"))).isTrue();
        assertThat(multimap.get("1").removeAll(Arrays.asList("1"))).isTrue();
    }
    
    @Test
    public void testValuesExpired() throws InterruptedException {
        RMultimapCache<String, String> multimap = redisson.getListMultimapCache("test");
        multimap.put("1", "1");
        multimap.put("1", "2");
        multimap.put("1", "3");
        multimap.expireKey("1", 1, TimeUnit.SECONDS);
        
        Thread.sleep(1000);
        
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
        RMultimapCache<String, String> cache = redisson.getListMultimapCache("simple33");
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

    
}
