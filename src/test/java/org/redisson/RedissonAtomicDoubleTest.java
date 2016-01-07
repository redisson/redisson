package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.Test;
import org.redisson.core.RAtomicDouble;

public class RedissonAtomicDoubleTest extends BaseTest {

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
