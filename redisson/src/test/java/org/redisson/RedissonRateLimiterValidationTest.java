package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedissonRateLimiterValidationTest extends RedisDockerTest {

    @Test
    public void testSetRateRejectsInvalidRateAndInterval() {
        RRateLimiter limiter = redisson.getRateLimiter("testSetRateRejectsInvalidRateAndInterval");
        limiter.delete();

        assertThatThrownBy(() -> limiter.trySetRate(RateType.OVERALL, 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.trySetRate(RateType.OVERALL, -1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.trySetRate(RateType.OVERALL, 10, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.trySetRate(RateType.OVERALL, 10, Duration.ofMillis(-100)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.setRate(RateType.OVERALL, 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.setRate(RateType.OVERALL, 10, Duration.ofMillis(-100)))
                .isInstanceOf(IllegalArgumentException.class);

        // legacy RateIntervalUnit overloads delegate to the same validation
        assertThatThrownBy(() -> limiter.trySetRate(RateType.OVERALL, 0, 1, RateIntervalUnit.SECONDS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.setRate(RateType.OVERALL, 10, 0, RateIntervalUnit.SECONDS))
                .isInstanceOf(IllegalArgumentException.class);

        // validation must reject before any state is written to Redis
        assertThat(limiter.isExists()).isFalse();
    }

    @Test
    public void testSetRateStillAcceptsValidValues() {
        RRateLimiter limiter = redisson.getRateLimiter("testSetRateStillAcceptsValidValues");
        limiter.delete();

        assertThat(limiter.trySetRate(RateType.OVERALL, 1, Duration.ofSeconds(1))).isTrue();
        limiter.setRate(RateType.OVERALL, 10, Duration.ofSeconds(2));
        assertThat(limiter.tryAcquire()).isTrue();
    }
}
