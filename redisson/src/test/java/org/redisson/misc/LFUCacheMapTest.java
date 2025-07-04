package org.redisson.misc;

import org.junit.jupiter.api.Test;
import org.redisson.cache.Cache;
import org.redisson.cache.LFUCacheMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class LFUCacheMapTest {

    @Test
    public void testMaxIdleTimeEviction() throws InterruptedException {
        Cache<Integer, Integer> map = new LFUCacheMap<Integer, Integer>(2, 0, 400);
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
        Cache<Integer, Integer> map = new LFUCacheMap<Integer, Integer>(2, 500, 0);
        map.put(1, 0);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(100);
        assertThat(map.get(1)).isEqualTo(0);
        assertThat(map.keySet()).containsOnly(1);
        Thread.sleep(500);
        assertThat(map.keySet()).isEmpty();
    }
    
    @Test
    public void testSizeLFUEviction() throws InterruptedException {
        Cache<Integer, Integer> map = new LFUCacheMap<Integer, Integer>(3, 0, 0);
        
        map.put(1, 0);
        map.put(2, 0);
        map.put(6, 0);

        map.get(1);
        map.put(3, 0);
        
        assertThat(map.keySet()).containsOnly(3, 1, 6);
        
        map.get(1);
        map.put(4, 0);
        
        assertThat(map.keySet()).contains(4, 1).hasSize(3);
    }
    
    @Test
    public void testSizeEviction() throws InterruptedException {
        Cache<Integer, Integer> map = new LFUCacheMap<Integer, Integer>(2, 0, 0);
        map.put(1, 0);
        map.put(2, 0);
        
        assertThat(map.keySet()).containsOnly(1, 2);
        
        map.put(3, 0);
        
        assertThat(map.keySet()).contains(3).hasSize(2);
        
        map.put(4, 0);
        
        assertThat(map.keySet()).contains(4).hasSize(2);
        
        map.put(5, 0);
        
        assertThat(map.keySet()).contains(5).hasSize(2);
    }

    @Test
    public void testLfuOnMapFull(){
        for (int i = 0; i < 1000; i++) {
            LFUCacheMap<String, Integer> map = new LFUCacheMap<>(6, 0, 0);
            //the value is accessCount
            Map<String, Integer> keyAccessCountMap = Map.of("F", 150, "E", 140, "D", 130, "C", 120, "B", 110, "A", 100);
            map.put("F", 150);
            map.put("E", 140);
            map.put("D", 130);
            map.put("C", 120);
            map.put("B", 110);
            map.put("A", 100);
            //add accessCount
            keyAccessCountMap.forEach((key, accessCount) -> {
                for (int j = 0; j < accessCount; j++) {
                    map.get(key);
                }
            });

            Thread thread1 = new Thread(() -> {
                map.put("w", 0);
            });
            Thread thread2 = new Thread(() -> {
                map.put("x", 0);
            });
            Thread thread3 = new Thread(() -> {
                map.put("z", 0);
            });
            thread1.start();
            thread2.start();
            thread3.start();
            try {
                thread1.join();
                thread2.join();
                thread3.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            assertThat(map.get("F") != null);
        }

    }
}
