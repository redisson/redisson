package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.redisson.api.RMultimapCache;

public class RedissonSetMultimapCacheTest extends RedissonBaseMultimapCacheTest {

    @Override
    RMultimapCache<String, String> getMultimapCache(String name) {
        return redisson.getSetMultimapCache(name);
    }

    @Test
    public void testValues() throws InterruptedException {
        RMultimapCache<String, String> multimap = getMultimapCache("test");
        multimap.put("1", "1");
        multimap.put("1", "2");
        multimap.put("1", "3");
        multimap.put("1", "3");
        
        assertThat(multimap.get("1").size()).isEqualTo(3);
        assertThat(multimap.get("1")).containsExactlyInAnyOrder("1", "2", "3");
        assertThat(multimap.get("1").remove("3")).isTrue();
        assertThat(multimap.get("1").contains("3")).isFalse();
        assertThat(multimap.get("1").contains("2")).isTrue();
        assertThat(multimap.get("1").containsAll(Arrays.asList("1"))).isTrue();
        assertThat(multimap.get("1").containsAll(Arrays.asList("1", "2"))).isTrue();
        assertThat(multimap.get("1").retainAll(Arrays.asList("1"))).isTrue();
        assertThat(multimap.get("1").removeAll(Arrays.asList("1"))).isTrue();
    }

    @Test
    public void testContainsAll() {
        RMultimapCache<String, String> multimap = getMultimapCache("test");
        multimap.put("1", "1");
        multimap.put("1", "2");
        multimap.put("1", "3");
        multimap.put("1", "3");

        assertThat(multimap.get("1").containsAll(List.of("1", "1", "1"))).isTrue();
        assertThat(multimap.get("1").containsAll(List.of("1", "2", "4"))).isFalse();
        assertThat(multimap.get("1").containsAll(List.of("1", "2", "1"))).isTrue();
        assertThat(multimap.get("1").containsAll(List.of("1", "1"))).isTrue();

    }
}
