package org.redisson;

import org.junit.Test;
import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import org.redisson.api.RRingBuffer;

public class RedissonRingBufferTest extends BaseTest {

    @Test
    public void testAdd() {
        RRingBuffer<Integer> buffer = redisson.getRingBuffer("test");
        assertThat(buffer.remainingCapacity()).isZero();
        assertThat(buffer.capacity()).isZero();
        buffer.trySetCapacity(10);
        assertThat(buffer.capacity()).isEqualTo(10);
        assertThat(buffer.remainingCapacity()).isEqualTo(10);
        
        for (int i = 0; i < 10; i++) {
            buffer.add(i);
        }
        
        assertThat(buffer).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(buffer.capacity()).isEqualTo(10);
        assertThat(buffer.remainingCapacity()).isEqualTo(0);
        
        for (int i = 0; i < 5; i++) {
            buffer.add(i*10);
        }
        
        assertThat(buffer).containsExactly(5, 6, 7, 8, 9, 0, 10, 20, 30, 40);
        assertThat(buffer.capacity()).isEqualTo(10);
        assertThat(buffer.remainingCapacity()).isEqualTo(0);
        
        buffer.poll();
        buffer.poll();
        buffer.poll();
        
        assertThat(buffer).containsExactly(8, 9, 0, 10, 20, 30, 40);
        assertThat(buffer.capacity()).isEqualTo(10);
        assertThat(buffer.remainingCapacity()).isEqualTo(3);
    }
    
    @Test
    public void testAddAll() {
        RRingBuffer<Integer> buffer = redisson.getRingBuffer("test");
        assertThat(buffer.remainingCapacity()).isZero();
        assertThat(buffer.capacity()).isZero();
        buffer.trySetCapacity(10);
        assertThat(buffer.capacity()).isEqualTo(10);
        
        List<Integer> s = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        buffer.addAll(s);
        
        assertThat(buffer).containsExactly((Integer[]) s.toArray());
        assertThat(buffer.capacity()).isEqualTo(10);
        assertThat(buffer.remainingCapacity()).isEqualTo(0);

        List<Integer> newlist = Arrays.asList(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 91, 92, 93);
        buffer.addAll(newlist);
        
        assertThat(buffer).containsExactly(30, 40, 50, 60, 70, 80, 90, 91, 92, 93);
        assertThat(buffer.capacity()).isEqualTo(10);
        assertThat(buffer.remainingCapacity()).isEqualTo(0);
        
        buffer.poll();
        buffer.poll();
        buffer.poll();
        
        assertThat(buffer).containsExactly(60, 70, 80, 90, 91, 92, 93);
        assertThat(buffer.capacity()).isEqualTo(10);
        assertThat(buffer.remainingCapacity()).isEqualTo(3);
    }
    
}
