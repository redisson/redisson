package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.springframework.data.redis.connection.ExpirationOptions;
import org.springframework.data.redis.connection.ReactiveHashCommands;
import org.springframework.data.redis.connection.RedisHashCommands.HashFieldSetOption;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.types.Expiration;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveHashCommandsTest extends BaseConnectionTest {

    private final byte[] key = "hash".getBytes(StandardCharsets.UTF_8);
    private final byte[] field = "field".getBytes(StandardCharsets.UTF_8);
    private final byte[] value = "value".getBytes(StandardCharsets.UTF_8);

    @Test
    public void testHScanWithBinaryPattern() {
        byte[] binaryField = {(byte) 0xFF, 'f'};
        connection.hashCommands().hSet(key, binaryField, value);
        connection.hashCommands().hSet(key, field, value);
        ReactiveHashCommands hashCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().hashCommands();

        assertThat(hashCommands.hScan(ByteBuffer.wrap(key), ScanOptions.scanOptions().match(new byte[] {(byte) 0xFF, '*'}).build())
                .map(Map.Entry::getKey).collectList().block()).containsExactly(ByteBuffer.wrap(binaryField));
    }

    @Test
    public void testFieldsWithoutValues() {
        ReactiveHashCommands hashCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().hashCommands();
        ByteBuffer hash = ByteBuffer.wrap(key);
        ByteBuffer missing = ByteBuffer.wrap("missing".getBytes(StandardCharsets.UTF_8));

        assertThat(hashCommands.hMGet(hash, Collections.singletonList(missing)).block()).containsExactly((ByteBuffer) null);
        assertThat(hashCommands.hGetDel(hash, Collections.singletonList(missing)).block()).containsExactly((ByteBuffer) null);
        assertThat(hashCommands.hGetEx(hash, Expiration.seconds(100), Collections.singletonList(missing)).block()).containsExactly((ByteBuffer) null);

        connection.hashCommands().hSet(key, field, value);
        List<ByteBuffer> fields = Arrays.asList(ByteBuffer.wrap(field), missing);
        assertThat(hashCommands.hMGet(hash, fields).block()).containsExactly(ByteBuffer.wrap(value), null);
        assertThat(hashCommands.hGetEx(hash, Expiration.seconds(100), Arrays.asList(missing, ByteBuffer.wrap(field))).block())
                .containsExactly(null, ByteBuffer.wrap(value));
        assertThat(hashCommands.hGetDel(hash, fields).block()).containsExactly(ByteBuffer.wrap(value), null);
        assertThat(hashCommands.hMGet(hash, fields).block()).containsExactly(null, null);
    }

    @Test
    public void testHRandField() {
        connection.hashCommands().hSet(key, field, value);
        ReactiveHashCommands hashCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().hashCommands();

        assertThat(hashCommands.hRandField(ByteBuffer.wrap(key)).block()).isEqualTo(ByteBuffer.wrap(field));
        assertThat(hashCommands.hRandField(ByteBuffer.wrap(key), 2).collectList().block()).containsExactly(ByteBuffer.wrap(field));
        assertThat(hashCommands.hRandField(ByteBuffer.wrap(key), -2).collectList().block())
                .containsExactly(ByteBuffer.wrap(field), ByteBuffer.wrap(field));
    }

    @Test
    public void testHRandFieldWithValues() {
        connection.hashCommands().hSet(key, field, value);
        ReactiveHashCommands hashCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().hashCommands();

        Map.Entry<ByteBuffer, ByteBuffer> entry = hashCommands.hRandFieldWithValues(ByteBuffer.wrap(key)).block();
        assertThat(entry.getKey()).isEqualTo(ByteBuffer.wrap(field));
        assertThat(entry.getValue()).isEqualTo(ByteBuffer.wrap(value));
        assertThat(hashCommands.hRandFieldWithValues(ByteBuffer.wrap(key), 2).collectList().block())
                .extracting(Map.Entry::getValue).containsExactly(ByteBuffer.wrap(value));
        assertThat(hashCommands.hRandFieldWithValues(ByteBuffer.wrap(key), -2).collectList().block()).hasSize(2);

        Config config = createConfig();
        config.setProtocol(Protocol.RESP3);
        RedissonClient client = Redisson.create(config);
        try {
            ReactiveHashCommands resp3 = new RedissonConnectionFactory(client).getReactiveConnection().hashCommands();
            assertThat(resp3.hRandFieldWithValues(ByteBuffer.wrap(key), 2).collectList().block())
                    .extracting(Map.Entry::getValue).containsExactly(ByteBuffer.wrap(value));
        } finally {
            client.shutdown();
        }
    }

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

    @Test
    public void testHSetEx() {
        ReactiveHashCommands hashCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().hashCommands();
        ByteBuffer keyBuffer = ByteBuffer.wrap(key);
        byte[] other = "other".getBytes(StandardCharsets.UTF_8);
        byte[] missing = "missing".getBytes(StandardCharsets.UTF_8);

        assertThat(hashCommands.hSetEx(keyBuffer, Collections.singletonMap(ByteBuffer.wrap(field), ByteBuffer.wrap(value)),
                HashFieldSetOption.IF_NONE_EXIST, Expiration.seconds(100)).block()).isTrue();
        assertThat(connection.hGet(key, field)).isEqualTo(value);
        assertThat(ttl(TimeUnit.SECONDS)).isBetween(95L, 100L);

        assertThat(hashCommands.hSetEx(keyBuffer, Collections.singletonMap(ByteBuffer.wrap(field), ByteBuffer.wrap(other)),
                HashFieldSetOption.IF_NONE_EXIST, null).block()).isFalse();
        assertThat(connection.hGet(key, field)).isEqualTo(value);

        assertThat(hashCommands.hSetEx(keyBuffer, Collections.singletonMap(ByteBuffer.wrap(missing), ByteBuffer.wrap(value)),
                HashFieldSetOption.IF_ALL_EXIST, null).block()).isFalse();
        assertThat(connection.hExists(key, missing)).isFalse();
    }
}
