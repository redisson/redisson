package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.ratelimiter.RateLimiterArgs;

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

    @Test
    public void testSetRateWithArgsRejectsInvalidRateAndInterval() {
        RRateLimiter limiter = redisson.getRateLimiter("testSetRateWithArgsRejectsInvalidRateAndInterval");
        limiter.delete();

        assertThatThrownBy(() -> limiter.setRate(RateLimiterArgs.of(RateType.OVERALL, 0, Duration.ofSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.setRate(RateLimiterArgs.of(RateType.OVERALL, -1, Duration.ofSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.setRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.setRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofMillis(-100))))
                .isInstanceOf(IllegalArgumentException.class);

        // validation must reject before any state is written to Redis
        assertThat(limiter.isExists()).isFalse();
    }

    @Test
    public void testUpdateRateWithArgsRejectsInvalidRateAndInterval() {
        RRateLimiter limiter = redisson.getRateLimiter("testUpdateRateWithArgsRejectsInvalidRateAndInterval");
        limiter.delete();
        limiter.setRate(RateType.OVERALL, 5, Duration.ofSeconds(1));

        assertThatThrownBy(() -> limiter.updateRate(RateLimiterArgs.of(RateType.OVERALL, 0, Duration.ofSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.updateRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);

        // the previously configured rate and interval must stay intact
        assertThat(limiter.getConfig().getRate()).isEqualTo(5);
        assertThat(limiter.getConfig().getRateInterval()).isEqualTo(1000);
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    public void testSetRateWithArgsStillAcceptsValidValues() {
        RRateLimiter limiter = redisson.getRateLimiter("testSetRateWithArgsStillAcceptsValidValues");
        limiter.delete();

        limiter.setRate(RateLimiterArgs.of(RateType.OVERALL, 1, Duration.ofSeconds(1)));
        assertThat(limiter.updateRate(RateLimiterArgs.of(RateType.OVERALL, 10, Duration.ofSeconds(2)))).isTrue();
        assertThat(limiter.getConfig().getRate()).isEqualTo(10);
        assertThat(limiter.getConfig().getRateInterval()).isEqualTo(2000);
        assertThat(limiter.tryAcquire()).isTrue();
    }
}
