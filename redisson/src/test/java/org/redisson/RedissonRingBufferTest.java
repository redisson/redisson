package org.redisson;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.redisson.api.RRingBuffer;

public class RedissonRingBufferTest extends RedisDockerTest {

    @Test
    public void testSetCapacity() {
        RRingBuffer<Integer> buffer = redisson.getRingBuffer("test");
        buffer.trySetCapacity(5);
        for (int i = 0; i < 10; i++) {
            buffer.add(i);
        }

        assertThat(buffer).containsExactly(5, 6, 7, 8, 9);
        buffer.setCapacity(3);
        assertThat(buffer).containsExactly(7, 8, 9);

        RRingBuffer<Integer> buffer2 = redisson.getRingBuffer("test2");
        buffer2.setCapacity(3);
        for (int i = 0; i < 10; i++) {
            buffer2.add(i);
        }
        assertThat(buffer2).containsExactly(7, 8, 9);

    }

    @Test
    public void testReSetCapacity() {
        RRingBuffer<Integer> buffer = redisson.getRingBuffer("test");
        buffer.trySetCapacity(3);
        for (int i = 0; i < 3; i++) {
            buffer.add(i);
        }

        assertThat(buffer).containsExactly(0, 1, 2);
        assertThat(buffer.size()).isEqualTo(3);

        // new capacity greater than list's length, not trim
        buffer.setCapacity(5);
        assertThat(buffer).containsExactly(0, 1, 2);
        assertThat(buffer.size()).isEqualTo(3);

        // new capacity less than list's length, trim size to new capacity
        buffer.setCapacity(1);
        assertThat(buffer).containsExactly(2);
        assertThat(buffer.size()).isEqualTo(1);
    }

    @Test
    public void testAdd() {
        RRingBuffer<Integer> buffer = redisson.getRingBuffer("test");
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
        assertThat(buffer.capacity()).isZero();
        buffer.trySetCapacity(10);
        assertThat(buffer.capacity()).isEqualTo(10);
        
        List<Integer> s = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        buffer.addAll(s);
        
        assertThat(buffer).containsExactly(s.toArray(new Integer[s.size()]));
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

    @Test
    public void testClear() {
        RRingBuffer<Integer> buffer = redisson.getRingBuffer("clear_test");
        buffer.trySetCapacity(10);
        for (int i = 0; i < 10; i++) {
            buffer.add(i);
        }
        assertThat(buffer).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        buffer.clear(); // clear() is only clearing elements, not capacity

        for (int i = 0; i < 10; i++) {
            buffer.add(i);
        }
        assertThat(buffer).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(buffer.capacity()).isEqualTo(10);
    }

    @Test
    public void testReadNewestAndOldest() {
        RRingBuffer<Integer> buffer = redisson.getRingBuffer("read_test");
        buffer.trySetCapacity(5);
        for (int i = 0; i < 8; i++) {
            buffer.add(i); // evicts head down to the last 5: 3, 4, 5, 6, 7
        }
        assertThat(buffer).containsExactly(3, 4, 5, 6, 7);

        // newest N, ordered oldest -> newest (same order as readAll)
        assertThat(buffer.readNewest(3)).containsExactly(5, 6, 7);
        assertThat(buffer.readNewest(1)).containsExactly(7);

        // oldest N, ordered oldest -> newest
        assertThat(buffer.readOldest(3)).containsExactly(3, 4, 5);
        assertThat(buffer.readOldest(1)).containsExactly(3);

        // count greater than size -> whole buffer
        assertThat(buffer.readNewest(100)).containsExactly(3, 4, 5, 6, 7);
        assertThat(buffer.readOldest(100)).containsExactly(3, 4, 5, 6, 7);

        // non-positive count -> empty
        assertThat(buffer.readNewest(0)).isEmpty();
        assertThat(buffer.readOldest(-1)).isEmpty();

        // reads are non-destructive
        assertThat(buffer.size()).isEqualTo(5);
        assertThat(buffer).containsExactly(3, 4, 5, 6, 7);
    }

    @Test
    public void testReadOnEmpty() {
        RRingBuffer<Integer> buffer = redisson.getRingBuffer("read_empty_test");
        buffer.trySetCapacity(5);

        assertThat(buffer.readNewest(3)).isEmpty();
        assertThat(buffer.readOldest(3)).isEmpty();
        assertThat(buffer.peekLast()).isNull();
    }

    @Test
    public void testPeekLast() {
        RRingBuffer<Integer> buffer = redisson.getRingBuffer("peek_last_test");
        buffer.trySetCapacity(3);

        assertThat(buffer.peekLast()).isNull();

        buffer.add(1);
        buffer.add(2);
        buffer.add(3);
        assertThat(buffer.peekLast()).isEqualTo(3);

        buffer.add(4); // evicts 1 -> [2, 3, 4]
        assertThat(buffer.peekLast()).isEqualTo(4);

        // peekLast doesn't remove
        assertThat(buffer.size()).isEqualTo(3);
        assertThat(buffer).containsExactly(2, 3, 4);
    }
}
