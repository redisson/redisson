package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.AsyncIterator;
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

    @Test
    public void testIteratorAsync() {
        RMultimapCache<String, String> multimap = getMultimapCache("test");
        multimap.put("1", "1");
        multimap.put("1", "2");
        multimap.put("1", "3");
        multimap.put("1", "3");

        RedissonSetMultimapValues<String> set = (RedissonSetMultimapValues<String>) multimap.get("1");
        AsyncIterator<String> iterator = set.iteratorAsync(5);

        Set<String> set2 = new HashSet<>();
        CompletionStage<Void> f = iterateAll(iterator, set2);
        f.toCompletableFuture().join();
        Assertions.assertEquals(3, set2.size());
    }

    public CompletionStage<Void> iterateAll(AsyncIterator<String> iterator, Set<String> set) {
        return iterator.hasNext().thenCompose(r -> {
            if (r) {
                return iterator.next().thenCompose(k -> {
                    set.add(k);
                    return iterateAll(iterator, set);
                });
            } else {
                return CompletableFuture.completedFuture(null);
            }
        });
    }
}
