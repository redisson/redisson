package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ExpirationOptions;
import org.springframework.data.redis.connection.ReactiveHashCommands;
import org.springframework.data.redis.core.types.Expiration;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveHashCommandsTest extends BaseConnectionTest {

    private final byte[] key = "hash".getBytes(StandardCharsets.UTF_8);
    private final byte[] field = "field".getBytes(StandardCharsets.UTF_8);
    private final byte[] value = "value".getBytes(StandardCharsets.UTF_8);

    private List<Long> applyExpiration(Expiration expiration, ExpirationOptions options) {
        ReactiveHashCommands.HashExpireCommand command = ReactiveHashCommands.HashExpireCommand
                .expire(List.of(ByteBuffer.wrap(field)), expiration)
                .from(ByteBuffer.wrap(key)).withOptions(options);
        return new RedissonConnectionFactory(redisson).getReactiveConnection().hashCommands()
                .applyHashFieldExpiration(Mono.just(command))
                .map(response -> response.getOutput()).collectList().block();
    }

    private Long ttl(TimeUnit unit) {
        if (unit == TimeUnit.MILLISECONDS) {
            return connection.hpTtl(key, field).get(0);
        }
        return connection.hTtl(key, unit, field).get(0);
    }

    @Test
    public void subSecondMillisDoesNotDeleteField() {
        connection.hSet(key, field, value);

        assertThat(applyExpiration(Expiration.milliseconds(500), ExpirationOptions.none())).containsExactly(1L);

        assertThat(connection.hGet(key, field)).isEqualTo(value);
        assertThat(ttl(TimeUnit.MILLISECONDS)).isBetween(1L, 500L);
    }

    @Test
    public void persistentRemovesFieldTtl() {
        connection.hSet(key, field, value);
        connection.hExpire(key, 100, field);

        assertThat(applyExpiration(Expiration.persistent(), ExpirationOptions.none())).containsExactly(1L);

        assertThat(ttl(TimeUnit.MILLISECONDS)).isEqualTo(-1L);
        assertThat(connection.hGet(key, field)).isEqualTo(value);
    }

    @Test
    public void absoluteSecondsUsesHexpireat() {
        connection.hSet(key, field, value);
        Expiration expiration = Expiration.unixTimestamp(Instant.now().plusSeconds(120).getEpochSecond(), TimeUnit.SECONDS);

        assertThat(applyExpiration(expiration, ExpirationOptions.none())).containsExactly(1L);

        assertThat(ttl(TimeUnit.SECONDS)).isBetween(110L, 120L);
    }

    @Test
    public void relativeSecondsUsesHexpire() {
        connection.hSet(key, field, value);

        assertThat(applyExpiration(Expiration.seconds(100), ExpirationOptions.none())).containsExactly(1L);

        assertThat(ttl(TimeUnit.SECONDS)).isBetween(95L, 100L);
        assertThat(connection.hGet(key, field)).isEqualTo(value);
    }

    @Test
    public void millisPrecisionPreservedForNonRoundValue() {
        connection.hSet(key, field, value);

        assertThat(applyExpiration(Expiration.milliseconds(1500), ExpirationOptions.none())).containsExactly(1L);

        assertThat(ttl(TimeUnit.MILLISECONDS)).isBetween(1001L, 1500L);
    }

    @Test
    public void absoluteMillisUsesHpexpireat() {
        connection.hSet(key, field, value);
        Expiration expiration = Expiration.unixTimestamp(Instant.now().plusSeconds(120).toEpochMilli(), TimeUnit.MILLISECONDS);

        assertThat(applyExpiration(expiration, ExpirationOptions.none())).containsExactly(1L);

        assertThat(ttl(TimeUnit.MILLISECONDS)).isBetween(110_000L, 120_000L);
    }

    @Test
    public void conditionForwardedToExpireCommand() {
        connection.hSet(key, field, value);
        connection.hExpire(key, 200, field);

        assertThat(applyExpiration(Expiration.seconds(50), ExpirationOptions.builder().gt().build())).containsExactly(0L);
        assertThat(ttl(TimeUnit.SECONDS)).isBetween(195L, 200L);

        assertThat(applyExpiration(Expiration.seconds(300), ExpirationOptions.builder().gt().build())).containsExactly(1L);
        assertThat(ttl(TimeUnit.SECONDS)).isBetween(295L, 300L);

        assertThat(applyExpiration(Expiration.seconds(50), ExpirationOptions.builder().lt().build())).containsExactly(1L);
        assertThat(ttl(TimeUnit.SECONDS)).isBetween(45L, 50L);
    }

    @Test
    public void conditionMatrixDoesNotThrow() {
        List<String> failures = new ArrayList<>();
        String[] names = {"HEXPIRE", "HPEXPIRE", "HEXPIREAT", "HPEXPIREAT", "HPERSIST"};
        ExpirationOptions[] options = {
                ExpirationOptions.builder().nx().build(),
                ExpirationOptions.builder().xx().build(),
                ExpirationOptions.builder().gt().build(),
                ExpirationOptions.builder().lt().build()
        };

        for (int i = 0; i < names.length; i++) {
            for (ExpirationOptions option : options) {
                connection.hSet(key, field, value);
                connection.hExpire(key, 200, field);
                Expiration[] expirations = {
                        Expiration.seconds(100),
                        Expiration.milliseconds(100_000),
                        Expiration.unixTimestamp(Instant.now().plusSeconds(100).getEpochSecond(), TimeUnit.SECONDS),
                        Expiration.unixTimestamp(Instant.now().plusSeconds(100).toEpochMilli(), TimeUnit.MILLISECONDS),
                        Expiration.persistent()
                };

                try {
                    List<Long> result = applyExpiration(expirations[i], option);
                    if (expirations[i].isPersistent()) {
                        assertThat(result).containsExactly(1L);
                        assertThat(ttl(TimeUnit.MILLISECONDS)).isEqualTo(-1L);
                    } else if (option.getCondition() == ExpirationOptions.Condition.NX
                            || option.getCondition() == ExpirationOptions.Condition.GT) {
                        assertThat(result).containsExactly(0L);
                        assertThat(ttl(TimeUnit.SECONDS)).isBetween(195L, 200L);
                    } else {
                        assertThat(result).containsExactly(1L);
                        assertThat(ttl(TimeUnit.SECONDS)).isBetween(95L, 100L);
                    }
                    assertThat(connection.hGet(key, field)).isEqualTo(value);
                } catch (Exception | AssertionError e) {
                    failures.add(names[i] + " / " + option.getCondition() + ": " + e);
                }
            }
        }
        assertThat(failures).isEmpty();
    }

    @Test
    public void persistIgnoresCondition() {
        connection.hSet(key, field, value);
        connection.hExpire(key, 100, field);

        assertThat(applyExpiration(Expiration.persistent(), ExpirationOptions.builder().xx().build())).containsExactly(1L);

        assertThat(ttl(TimeUnit.MILLISECONDS)).isEqualTo(-1L);
        assertThat(connection.hGet(key, field)).isEqualTo(value);
    }

    @Test
    public void responsesFollowFieldOrder() {
        connection.hSet(key, field, value);
        ReactiveHashCommands.HashExpireCommand command = ReactiveHashCommands.HashExpireCommand
                .expire(List.of(ByteBuffer.wrap("missing".getBytes(StandardCharsets.UTF_8)), ByteBuffer.wrap(field)),
                        Expiration.milliseconds(100_000))
                .from(ByteBuffer.wrap(key));

        List<Long> result = new RedissonConnectionFactory(redisson).getReactiveConnection().hashCommands()
                .applyHashFieldExpiration(Mono.just(command))
                .map(response -> response.getOutput()).collectList().block();

        assertThat(result).containsExactly(-2L, 1L);
        assertThat(ttl(TimeUnit.MILLISECONDS)).isBetween(95_000L, 100_000L);
    }
}
