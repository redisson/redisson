package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonAtomicLongTest extends RedisDockerTest {
    
    @Test
    public void testSetIfLess() {
        RAtomicLong al = redisson.getAtomicLong("test");
        assertThat(al.setIfLess(0, 1)).isFalse();
        assertThat(al.get()).isEqualTo(0);
        
        al.set(12);
        assertThat(al.setIfLess(13, 1)).isTrue();
        assertThat(al.get()).isEqualTo(1);
    }
    
    @Test
    public void testSetIfGreater() {
        RAtomicLong al = redisson.getAtomicLong("test");
        assertThat(al.setIfLess(0, 1)).isFalse();
        assertThat(al.get()).isEqualTo(0);
        
        al.set(12);
        assertThat(al.setIfGreater(11, 1)).isTrue();
        assertThat(al.get()).isEqualTo(1);
    }
    
    @Test
    public void testGetAndSet() {
        RAtomicLong al = redisson.getAtomicLong("test");
        Assertions.assertEquals(0, al.getAndSet(12));
    }

    @Test
    public void testGetZero() {
        RAtomicLong ad2 = redisson.getAtomicLong("test");
        assertThat(ad2.get()).isZero();
    }
    
    @Test
    public void testGetAndDelete() {
        RAtomicLong al = redisson.getAtomicLong("test");
        al.set(10);
        assertThat(al.getAndDelete()).isEqualTo(10);
        assertThat(al.isExists()).isFalse();

        RAtomicLong ad2 = redisson.getAtomicLong("test2");
        assertThat(ad2.getAndDelete()).isZero();
    }
    
    @Test
    public void testCompareAndSetZero() {
        RAtomicLong al = redisson.getAtomicLong("test");
        Assertions.assertTrue(al.compareAndSet(0, 2));
        Assertions.assertEquals(2, al.get());

        RAtomicLong al2 = redisson.getAtomicLong("test1");
        al2.set(0);
        Assertions.assertTrue(al2.compareAndSet(0, 2));
        Assertions.assertEquals(2, al2.get());

    }


    @Test
    public void testCompareAndSet() {
        RAtomicLong al = redisson.getAtomicLong("test");
        Assertions.assertFalse(al.compareAndSet(-1, 2));
        Assertions.assertEquals(0, al.get());
        Assertions.assertTrue(al.compareAndSet(0, 2));
        Assertions.assertEquals(2, al.get());
    }

    @Test
    public void testSetThenIncrement() {
        RAtomicLong al = redisson.getAtomicLong("test");
        al.set(2);
        Assertions.assertEquals(2, al.getAndIncrement());
        Assertions.assertEquals(3, al.get());
    }

    @Test
    public void testIncrementAndGet() {
        RAtomicLong al = redisson.getAtomicLong("test");
        Assertions.assertEquals(1, al.incrementAndGet());
        Assertions.assertEquals(1, al.get());
    }

    @Test
    public void testGetAndIncrement() {
        RAtomicLong al = redisson.getAtomicLong("test");
        Assertions.assertEquals(0, al.getAndIncrement());
        Assertions.assertEquals(1, al.get());
    }

    @Test
    public void test() {
        RAtomicLong al = redisson.getAtomicLong("test");
        Assertions.assertEquals(0, al.get());
        Assertions.assertEquals(0, al.getAndIncrement());
        Assertions.assertEquals(1, al.get());
        Assertions.assertEquals(1, al.getAndDecrement());
        Assertions.assertEquals(0, al.get());
        Assertions.assertEquals(0, al.getAndIncrement());
        Assertions.assertEquals(1, al.getAndSet(12));
        Assertions.assertEquals(12, al.get());
        al.set(1);

        long state = redisson.getAtomicLong("test").get();
        Assertions.assertEquals(1, state);
        al.set(Long.MAX_VALUE - 1000);

        long newState = redisson.getAtomicLong("test").get();
        Assertions.assertEquals(Long.MAX_VALUE - 1000, newState);
    }

}
