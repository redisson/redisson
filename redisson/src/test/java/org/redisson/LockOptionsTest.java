package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.LockOptions;

import static org.assertj.core.api.Assertions.assertThat;

public class LockOptionsTest {

    @Test
    public void testExponentialBackoff() {
        LockOptions.BackOff backOff = new LockOptions.ExponentialBackOff()
                .initialDelay(10)
                .maxDelay(100)
                .multiplier(3);

        LockOptions.BackOffPolicy backOffPolicy = backOff.create();


        assertThat(backOffPolicy.getNextSleepPeriod()).isBetween(10L, 10L);
        assertThat(backOffPolicy.getNextSleepPeriod()).isBetween(30L, 31L);
        assertThat(backOffPolicy.getNextSleepPeriod()).isBetween(90L, 92L);
        assertThat(backOffPolicy.getNextSleepPeriod()).isBetween(100L, 103L);
        assertThat(backOffPolicy.getNextSleepPeriod()).isBetween(100L, 104L);
    }

    @Test
    public void testConstantBackoff() {
        LockOptions.ConstantBackOff backOffOptions = new LockOptions.ConstantBackOff()
                .delay(30);

        LockOptions.BackOffPolicy backOffPolicy = backOffOptions.create();

        for (int i = 0; i < 10; i++) {
            assertThat(backOffPolicy.getNextSleepPeriod()).isEqualTo(30L);
        }
    }
}
