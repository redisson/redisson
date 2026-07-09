package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.GcraConfig;
import org.redisson.api.GcraResult;
import org.redisson.api.RGcra;
import org.redisson.client.RedisException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 *
 * @author Su Ko
 *
 */
public class RedissonGcraTest extends RedisDockerTest {

    @Test
    public void testTrySetRate() {
        RGcra gcra = redisson.getGcra("testTrySetRate");

        assertThat(gcra.trySetRate(2, 4, Duration.ofSeconds(1))).isTrue();
        assertThat(gcra.trySetRate(10, 20, Duration.ofSeconds(5))).isFalse();

        GcraConfig config = gcra.getConfig();
        assertThat(config.getMaxBurst()).isEqualTo(2);
        assertThat(config.getTokensPerPeriod()).isEqualTo(4);
        assertThat(config.getPeriod()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    public void testTryAcquire() {
        RGcra gcra = redisson.getGcra("testTryAcquire");
        gcra.trySetRate(2, 4, Duration.ofSeconds(1));

        GcraResult first = gcra.tryAcquire();
        GcraResult second = gcra.tryAcquire();
        GcraResult third = gcra.tryAcquire();
        GcraResult fourth = gcra.tryAcquire();

        assertThat(first.isLimited()).isFalse();
        assertThat(second.isLimited()).isFalse();
        assertThat(third.isLimited()).isFalse();
        assertThat(fourth.isLimited()).isTrue();

        assertThat(first.getMaxTokens()).isEqualTo(3);
        assertThat(fourth.getMaxTokens()).isEqualTo(3);

        assertThat(first.getRetryAfterSeconds()).isEqualTo(-1);
        assertThat(fourth.getRetryAfterSeconds()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void testTryAcquireWithTokens() {
        RGcra gcra = redisson.getGcra("testTryAcquireWithTokens");
        gcra.trySetRate(2, 4, Duration.ofMillis(250));

        GcraResult result = gcra.tryAcquire(2);

        assertThat(result.isLimited()).isFalse();
        assertThat(result.getMaxTokens()).isEqualTo(3);
        assertThat(result.getRetryAfterSeconds()).isEqualTo(-1);
    }

    @Test
    public void testSetRate() {
        RGcra gcra = redisson.getGcra("testSetRate");

        gcra.trySetRate(2, 4, Duration.ofSeconds(1));
        assertThat(gcra.tryAcquire().isLimited()).isFalse();
        assertThat(gcra.tryAcquire().isLimited()).isFalse();
        assertThat(gcra.tryAcquire().isLimited()).isFalse();
        assertThat(gcra.tryAcquire().isLimited()).isTrue();

        // overwrites the rate and resets the consumed tokens
        gcra.setRate(5, 10, Duration.ofSeconds(2));

        GcraConfig config = gcra.getConfig();
        assertThat(config.getMaxBurst()).isEqualTo(5);
        assertThat(config.getTokensPerPeriod()).isEqualTo(10);
        assertThat(config.getPeriod()).isEqualTo(Duration.ofSeconds(2));

        assertThat(gcra.tryAcquire().isLimited()).isFalse();
    }

    @Test
    public void testGetConfigNotSet() {
        RGcra gcra = redisson.getGcra("testGetConfigNotSet");

        assertThat(gcra.getConfig()).isNull();
    }

    @Test
    public void testTryAcquireRateNotSet() {
        RGcra gcra = redisson.getGcra("testTryAcquireRateNotSet");

        assertThatThrownBy(gcra::tryAcquire)
                .isInstanceOf(RedisException.class)
                .hasMessageContaining("GCRA rate is not set");
    }

    @Test
    public void testValidation() {
        RGcra gcra = redisson.getGcra("testValidation");

        assertThatThrownBy(() -> gcra.trySetRate(-1, 1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBurst");
        assertThatThrownBy(() -> gcra.trySetRate(1, 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokensPerPeriod");
        assertThatThrownBy(() -> gcra.trySetRate(1, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("period");
        assertThatThrownBy(() -> gcra.tryAcquire(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokens");
    }

    @Test
    public void testDelete() {
        RGcra gcra = redisson.getGcra("testDelete");
        gcra.trySetRate(2, 4, Duration.ofSeconds(1));
        gcra.tryAcquire();

        assertThat(gcra.delete()).isTrue();
        assertThat(gcra.getConfig()).isNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedTryAcquire() {
        RGcra gcra = redisson.getGcra("testDeprecatedTryAcquire");

        GcraResult first = gcra.tryAcquire(2, 4, Duration.ofSeconds(1));
        GcraResult second = gcra.tryAcquire(2, 4, Duration.ofSeconds(1));
        GcraResult third = gcra.tryAcquire(2, 4, Duration.ofSeconds(1));
        GcraResult fourth = gcra.tryAcquire(2, 4, Duration.ofSeconds(1));

        assertThat(first.isLimited()).isFalse();
        assertThat(second.isLimited()).isFalse();
        assertThat(third.isLimited()).isFalse();
        assertThat(fourth.isLimited()).isTrue();
    }
}
