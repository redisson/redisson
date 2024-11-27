package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicDouble;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonAtomicDoubleTest extends RedisDockerTest {
    
    @Test
    public void testSetIfLess() {
        RAtomicDouble al = redisson.getAtomicDouble("test");
        assertThat(al.setIfLess(0, 1)).isFalse();
        assertThat(al.get()).isEqualTo(0);
        
        al.set(12);
        assertThat(al.setIfLess(13, 1)).isTrue();
        assertThat(al.get()).isEqualTo(1);
    }
    
    @Test
    public void testSetIfGreater() {
        RAtomicDouble al = redisson.getAtomicDouble("test");
        assertThat(al.setIfLess(0, 1)).isFalse();
        assertThat(al.get()).isEqualTo(0);
        
        al.set(12);
        assertThat(al.setIfGreater(11, 1)).isTrue();
        assertThat(al.get()).isEqualTo(1);
    }
    
    @Test
    public void testGetAndSet() {
        RAtomicDouble al = redisson.getAtomicDouble("test");
        assertThat(al.getAndSet(12)).isEqualTo(0);
    }

    @Test
    public void testGetZero() {
        RAtomicDouble ad2 = redisson.getAtomicDouble("test");
        assertThat(ad2.get()).isZero();
    }
    
    @Test
    public void testGetAndDelete() {
        RAtomicDouble al = redisson.getAtomicDouble("test");
        al.set(10.34);
        assertThat(al.getAndDelete()).isEqualTo(10.34);
        assertThat(al.isExists()).isFalse();

        RAtomicDouble ad2 = redisson.getAtomicDouble("test2");
        assertThat(ad2.getAndDelete()).isZero();
    }
    
    @Test
    public void testCompareAndSet() {
        RAtomicDouble al = redisson.getAtomicDouble("test");
        assertThat(al.compareAndSet(-1, 2.5)).isFalse();
        assertThat(al.get()).isZero();

        assertThat(al.compareAndSet(0, 2.5)).isTrue();
        assertThat(al.get()).isEqualTo(2.5);
    }

    @Test
    public void testSetThenIncrement() {
        RAtomicDouble al = redisson.getAtomicDouble("test");
        al.set(2.81);
        assertThat(al.getAndIncrement()).isEqualTo(2.81);
        assertThat(al.get()).isEqualTo(3.81);
    }

    @Test
    public void testDecrementAndGet() {
        RAtomicDouble al = redisson.getAtomicDouble("test");
        al.set(19.30);
        assertThat(al.decrementAndGet()).isEqualTo(18.30);
        assertThat(al.get()).isEqualTo(18.30);
    }
    
    @Test
    public void testIncrementAndGet() {
        RAtomicDouble al = redisson.getAtomicDouble("test");
        assertThat(al.incrementAndGet()).isEqualTo(1);
        assertThat(al.get()).isEqualTo(1);
    }

    @Test
    public void testGetAndIncrement() {
        RAtomicDouble al = redisson.getAtomicDouble("test");
        assertThat(al.getAndIncrement()).isEqualTo(0);
        assertThat(al.get()).isEqualTo(1);
    }

    @Test
    public void test() {
        RAtomicDouble al = redisson.getAtomicDouble("test");
        assertThat(al.get()).isEqualTo(0);
        assertThat(al.getAndIncrement()).isEqualTo(0);
        assertThat(al.get()).isEqualTo(1);
        assertThat(al.getAndDecrement()).isEqualTo(1);
        assertThat(al.get()).isEqualTo(0);
        assertThat(al.getAndIncrement()).isEqualTo(0);
        assertThat(al.getAndSet(12.8012)).isEqualTo(1);
        assertThat(al.get()).isEqualTo(12.8012);

        al.set(1.00123);

        double state = redisson.getAtomicDouble("test").get();
        assertThat(state).isEqualTo(1.00123);
        al.set(BigDecimal.valueOf(Long.MAX_VALUE).doubleValue());
        al.incrementAndGet();

        double newState = redisson.getAtomicDouble("test").get();
        assertThat(newState).isEqualTo(BigDecimal.valueOf(Long.MAX_VALUE).doubleValue());
    }

}
