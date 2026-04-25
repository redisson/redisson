package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.GcraResult;
import org.redisson.api.RGcra;

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
    public void testTryAcquire() {
        RGcra gcra = redisson.getGcra("test");

        GcraResult first = gcra.tryAcquire(2, 4, Duration.ofSeconds(1));
        GcraResult second = gcra.tryAcquire(2, 4, Duration.ofSeconds(1));
        GcraResult third = gcra.tryAcquire(2, 4, Duration.ofSeconds(1));
        GcraResult fourth = gcra.tryAcquire(2, 4, Duration.ofSeconds(1));

        assertThat(first.isLimited()).isFalse();
        assertThat(second.isLimited()).isFalse();
        assertThat(third.isLimited()).isFalse();
        assertThat(fourth.isLimited()).isTrue();

        assertThat(first.getMaxTokens()).isEqualTo(3);
        assertThat(second.getMaxTokens()).isEqualTo(3);
        assertThat(third.getMaxTokens()).isEqualTo(3);
        assertThat(fourth.getMaxTokens()).isEqualTo(3);

        assertThat(first.getRetryAfterSeconds()).isEqualTo(-1);
        assertThat(fourth.getRetryAfterSeconds()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void testTryAcquireWithTokens() {
        RGcra gcra = redisson.getGcra("test");

        GcraResult result = gcra.tryAcquire(2, 4, Duration.ofMillis(250), 2);

        assertThat(result.isLimited()).isFalse();
        assertThat(result.getMaxTokens()).isEqualTo(3);
        assertThat(result.getRetryAfterSeconds()).isEqualTo(-1);
    }

    @Test
    public void testValidation() {
        RGcra gcra = redisson.getGcra("test");

        assertThatThrownBy(() -> gcra.tryAcquire(-1, 1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBurst");
        assertThatThrownBy(() -> gcra.tryAcquire(1, 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokensPerPeriod");
        assertThatThrownBy(() -> gcra.tryAcquire(1, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("period");
        assertThatThrownBy(() -> gcra.tryAcquire(1, 1, Duration.ofSeconds(1), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokens");
    }
}
