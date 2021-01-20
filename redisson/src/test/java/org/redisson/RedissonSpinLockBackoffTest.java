package org.redisson;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonSpinLockBackoffTest {

    @Test
    public void testExponentialBackoff() {
        RedissonSpinLock.BackOffOptions backOffOptions = new RedissonSpinLock.ExponentialBackOffOptions()
                .initialDelay(10)
                .maxDelay(100)
                .multiplier(3);

        RedissonSpinLock.BackOffPolicy backOffPolicy = backOffOptions.create();


        assertThat(backOffPolicy.getNextSleepPeriod()).isBetween(10L, 10L);
        assertThat(backOffPolicy.getNextSleepPeriod()).isBetween(30L, 31L);
        assertThat(backOffPolicy.getNextSleepPeriod()).isBetween(90L, 92L);
        assertThat(backOffPolicy.getNextSleepPeriod()).isBetween(100L, 103L);
        assertThat(backOffPolicy.getNextSleepPeriod()).isBetween(100L, 104L);
    }

    @Test
    public void testConstantBackoff() {
        RedissonSpinLock.ConstantBackOffOptions backOffOptions = new RedissonSpinLock.ConstantBackOffOptions()
                .delay(30);

        RedissonSpinLock.BackOffPolicy backOffPolicy = backOffOptions.create();

        for (int i = 0; i < 10; i++) {
            assertThat(backOffPolicy.getNextSleepPeriod()).isEqualTo(30L);
        }
    }
}
