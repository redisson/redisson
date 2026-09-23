package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveStringCommands;
import org.springframework.data.redis.core.types.Expiration;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduction/regression coverage for RedissonReactiveStringCommands.getEx() ignoring the
 * PERSIST / KEEPTTL / EXAT / PXAT options carried by GetExCommand.getExpiration() and always
 * sending "PX &lt;millis&gt;" regardless of what was actually requested. The sync equivalent
 * (RedissonConnection.getEx()) was fixed for this exact bug in commit 6c393a51f, but the reactive
 * implementation in this same file was never updated to match.
 */
public class RedissonReactiveStringCommandsTest extends BaseConnectionTest {

    private ByteBuffer buf(String s) {
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    private ReactiveStringCommands stringCommands() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveRedisConnection conn = factory.getReactiveConnection();
        return conn.stringCommands();
    }

    private Long ttlMillis(String key) {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        return factory.getReactiveConnection().keyCommands().pTtl(buf(key)).block();
    }

    // Baseline: plain relative expiration (milliseconds) - this is the ONLY variant the current
    // (buggy) implementation happens to get right, since it's the one hardcoded case.
    @Test
    public void testGetExRelativeMillis() {
        redisson.getBucket("k1", org.redisson.client.codec.StringCodec.INSTANCE).set("v1");

        ReactiveStringCommands.GetExCommand cmd = ReactiveStringCommands.GetExCommand
                .key(buf("k1")).withExpiration(Expiration.milliseconds(60_000));
        ByteBuffer result = stringCommands().getEx(reactor.core.publisher.Mono.just(cmd))
                .map(r -> r.getOutput()).blockFirst();

        assertThat(StandardCharsets.UTF_8.decode(result).toString()).isEqualTo("v1");
        Long ttl = ttlMillis("k1");
        assertThat(ttl).isBetween(55_000L, 60_000L);
    }

    // PERSIST: should remove the TTL entirely. Currently sends "PX <persistent-sentinel-millis>"
    // instead of "PERSIST", so the key keeps expiring instead of becoming persistent.
    @Test
    public void testGetExPersistRemovesTtl() {
        redisson.getBucket("k2", org.redisson.client.codec.StringCodec.INSTANCE)
                .set("v2", Duration.ofSeconds(30));
        assertThat(ttlMillis("k2")).isGreaterThan(0);

        ReactiveStringCommands.GetExCommand cmd = ReactiveStringCommands.GetExCommand
                .key(buf("k2")).withExpiration(Expiration.persistent());
        stringCommands().getEx(reactor.core.publisher.Mono.just(cmd)).blockFirst();

        Long ttl = ttlMillis("k2");
        assertThat(ttl)
                .as("PERSIST must remove the TTL entirely (PTTL should be -1)")
                .isEqualTo(-1L);
    }

    // KEEPTTL: should leave the existing TTL untouched. Currently overwrites it with a fresh,
    // unrelated "PX" value derived from Expiration's internal sentinel encoding.
    @Test
    public void testGetExKeepTtlPreservesOriginalTtl() {
        redisson.getBucket("k3", org.redisson.client.codec.StringCodec.INSTANCE)
                .set("v3", Duration.ofSeconds(100));
        Long ttlBefore = ttlMillis("k3");

        ReactiveStringCommands.GetExCommand cmd = ReactiveStringCommands.GetExCommand
                .key(buf("k3")).withExpiration(Expiration.keepTtl());
        stringCommands().getEx(reactor.core.publisher.Mono.just(cmd)).blockFirst();

        Long ttlAfter = ttlMillis("k3");
        assertThat(ttlAfter)
                .as("KEEPTTL must leave the original TTL (~100s) untouched, not overwrite it")
                .isCloseTo(ttlBefore, org.assertj.core.data.Offset.offset(2000L));
    }

    // EXAT: absolute expiration in seconds since epoch. Currently the absolute timestamp is sent
    // as a relative "PX" delay instead, producing a wildly different actual expiration time.
    @Test
    public void testGetExAbsoluteSecondsUsesExatSemantics() {
        redisson.getBucket("k4", org.redisson.client.codec.StringCodec.INSTANCE).set("v4");

        long expireAtEpochSeconds = Instant.now().plusSeconds(120).getEpochSecond();
        ReactiveStringCommands.GetExCommand cmd = ReactiveStringCommands.GetExCommand
                .key(buf("k4"))
                .withExpiration(Expiration.unixTimestamp(expireAtEpochSeconds, java.util.concurrent.TimeUnit.SECONDS));
        stringCommands().getEx(reactor.core.publisher.Mono.just(cmd)).blockFirst();

        Long ttl = ttlMillis("k4");
        // Expected: close to 120_000ms (now -> expireAtEpochSeconds). If the bug is present, the
        // command instead sends "PX <raw epoch seconds value>", producing a TTL of roughly
        // (epochSeconds * 1000 - now) milliseconds - i.e. decades in the future - or an outright
        // Redis error depending on how large that number is.
        assertThat(ttl)
                .as("EXAT must set the TTL to ~120s from now, not treat the epoch value as a relative PX delay")
                .isBetween(110_000L, 120_000L);
    }
}
