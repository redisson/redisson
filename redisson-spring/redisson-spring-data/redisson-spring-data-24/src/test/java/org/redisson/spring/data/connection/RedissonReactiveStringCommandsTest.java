package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveStringCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.types.Expiration;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduction/regression coverage for RedissonReactiveStringCommands.set() ignoring
 * Expiration.keepTtl(). This Spring Data Redis version (2.5) does not have
 * Expiration.isUnixTimestamp() yet, so only the KEEPTTL branch applies here (matches the
 * scope used for the blocking sibling fix in RedissonConnectionTest for this same module).
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

    @Test
    public void testSetWithExpirationKeepTtlPreservesOriginalTtl() {
        String key = "set-keepttl-reactive-repro";
        redisson.getBucket(key, org.redisson.client.codec.StringCodec.INSTANCE).set("value1");
        redisson.getBucket(key).expire(java.time.Duration.ofSeconds(100));

        ReactiveStringCommands.SetCommand cmd = ReactiveStringCommands.SetCommand
                .set(buf(key))
                .value(buf("value2"))
                .expiring(Expiration.keepTtl())
                .withSetOption(SetOption.upsert());
        stringCommands().set(reactor.core.publisher.Mono.just(cmd)).blockFirst();

        assertThat(redisson.getBucket(key, org.redisson.client.codec.StringCodec.INSTANCE).get())
                .isEqualTo("value2");
        assertThat(redisson.getBucket(key).remainTimeToLive())
                .as("KEEPTTL must leave the original TTL untouched")
                .isGreaterThan(0);
    }
}
