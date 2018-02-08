package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.Test;
import org.redisson.api.RMultimapCache;

public class RedissonListMultimapCacheTest extends RedissonBaseMultimapCacheTest {

    @Override
    RMultimapCache<String, String> getMultimapCache(String name) {
        return redisson.getListMultimapCache(name);
    }

    @Test
    public void testValues() throws InterruptedException {
        RMultimapCache<String, String> multimap = getMultimapCache("test");
        multimap.put("1", "1");
        multimap.put("1", "2");
        multimap.put("1", "3");
        multimap.put("1", "3");
        
        assertThat(multimap.get("1").size()).isEqualTo(4);
        assertThat(multimap.get("1")).containsExactly("1", "2", "3", "3");
        assertThat(multimap.get("1").remove("3")).isTrue();
        assertThat(multimap.get("1").remove("3")).isTrue();
        assertThat(multimap.get("1").remove("3")).isFalse();
        assertThat(multimap.get("1").contains("3")).isFalse();
        assertThat(multimap.get("1").contains("2")).isTrue();
        assertThat(multimap.get("1").containsAll(Arrays.asList("1"))).isTrue();
        assertThat(multimap.get("1").containsAll(Arrays.asList("1", "2"))).isTrue();
        assertThat(multimap.get("1").retainAll(Arrays.asList("1"))).isTrue();
        assertThat(multimap.get("1").removeAll(Arrays.asList("1"))).isTrue();
    }
    
}
