package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveNumberCommands;
import org.springframework.data.redis.connection.ReactiveStringCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.types.Expiration;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

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
    }

    @Test
    public void testSetWithUnmetConditionReturnsFalse() {
        redisson.getBucket("set-present", org.redisson.client.codec.StringCodec.INSTANCE).set("seed");
        Expiration[] expirations = {Expiration.milliseconds(60000), Expiration.persistent()};
        for (Expiration expiration : expirations) {
            assertThat(stringCommands().set(buf("set-missing"), buf("v"), expiration, SetOption.ifPresent()).block()).isFalse();
            assertThat(stringCommands().set(buf("set-present"), buf("v"), expiration, SetOption.ifAbsent()).block()).isFalse();
        }
        assertThat(redisson.getBucket("set-missing").isExists()).isFalse();
        assertThat(redisson.getBucket("set-present", org.redisson.client.codec.StringCodec.INSTANCE).get()).isEqualTo("seed");
    }
}
