package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveNumberCommands;
import org.springframework.data.redis.connection.ReactiveStringCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

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
    public void testDecrByNegativeDelta() {
        ReactiveNumberCommands numberCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().numberCommands();
        assertThat(numberCommands.decrBy(buf("decr"), -3L).block()).isEqualTo(3L);
        assertThat(numberCommands.decrBy(buf("decr"), 1L).block()).isEqualTo(2L);
        assertThat(numberCommands.decrBy(buf("decr-double"), -1.5).block()).isEqualTo(1.5);
        assertThat(numberCommands.decrBy(buf("decr-double"), 0.25).block()).isEqualTo(1.25);

        ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(new RedissonConnectionFactory(redisson));
        assertThat(template.opsForValue().decrement("decr-template", -2L).block()).isEqualTo(2L);
        assertThat(template.opsForValue().get("decr-template").block()).isEqualTo("2");
    }

    @Test
    public void testSetWithUnmetConditionReturnsFalse() {
        redisson.getBucket("set-present", org.redisson.client.codec.StringCodec.INSTANCE).set("seed");
        Expiration[] expirations = {Expiration.milliseconds(60000), Expiration.persistent(), Expiration.keepTtl()};
        for (Expiration expiration : expirations) {
            assertThat(stringCommands().set(buf("set-missing"), buf("v"), expiration, SetOption.ifPresent()).block()).isFalse();
            assertThat(stringCommands().set(buf("set-present"), buf("v"), expiration, SetOption.ifAbsent()).block()).isFalse();
        }
        assertThat(redisson.getBucket("set-missing").isExists()).isFalse();

        ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(new RedissonConnectionFactory(redisson));
        assertThat(template.opsForValue().setIfPresent("set-missing", "v", Duration.ofSeconds(5)).block()).isFalse();
        assertThat(template.opsForValue().get("set-present").block()).isEqualTo("seed");
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
