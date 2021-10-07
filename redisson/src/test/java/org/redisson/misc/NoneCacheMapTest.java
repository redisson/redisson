package org.redisson.misc;

import org.junit.jupiter.api.Test;
import org.redisson.cache.Cache;
import org.redisson.cache.NoneCacheMap;

import static org.assertj.core.api.Assertions.assertThat;

public class NoneCacheMapTest {

    @Test
    public void testMaxIdleTimeEviction() throws InterruptedException {
        Cache<Integer, Integer> map = new NoneCacheMap<Integer, Integer>(0, 400);
        map.put(1, 0);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(200);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(200);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(200);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(410);
        assertThat(map.keySet()).isEmpty();
    }

    @Test
    public void testTTLEviction() throws InterruptedException {
        Cache<Integer, Integer> map = new NoneCacheMap<Integer, Integer>(500, 0);
        map.put(1, 0);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(100);
        assertThat(map.get(1)).isEqualTo(0);
        assertThat(map.keySet()).containsOnly(1);
        Thread.sleep(500);
        assertThat(map.keySet()).isEmpty();
    }
    
    @Test
    public void testSizeEviction() {
        Cache<Integer, Integer> map = new NoneCacheMap<Integer, Integer>(0, 0);
        map.put(1, 0);
        map.put(2, 0);
        
        assertThat(map.keySet()).containsOnly(1, 2);
        map.put(3, 0);
        map.put(4, 0);
        
        assertThat(map.keySet()).containsOnly(1, 2, 3, 4);
    }

}
