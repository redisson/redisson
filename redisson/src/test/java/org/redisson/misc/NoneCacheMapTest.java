package org.redisson.misc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.ClassRule;
import org.junit.Rule;

import org.junit.Test;
import org.junit.rules.Timeout;

public class NoneCacheMapTest {

    @ClassRule
    public static Timeout classTimeout = new Timeout(1, TimeUnit.HOURS);
    @Rule
    public Timeout testTimeout = new Timeout(15, TimeUnit.MINUTES);

    @Test
    public void testMaxIdleTimeEviction() throws InterruptedException {
        Cache<Integer, Integer> map = new NoneCacheMap<Integer, Integer>(0, 0);
        map.put(1, 0, 0, TimeUnit.MILLISECONDS, 400, TimeUnit.MILLISECONDS);
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
        Cache<Integer, Integer> map = new NoneCacheMap<Integer, Integer>(0, 0);
        map.put(1, 0, 500, TimeUnit.MILLISECONDS, 0, TimeUnit.MILLISECONDS);
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
