package org.redisson.spring.data.connection;

import org.junit.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveKeyCommandsTest extends BaseConnectionTest {

    @Test
    public void testExpiration() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveStringRedisTemplate t = new ReactiveStringRedisTemplate(factory);
        t.opsForValue().set("123", "4343").block();
        t.expire("123", Duration.ofMillis(1001)).block();
        assertThat(t.getExpire("123").block().toMillis()).isBetween(900L, 1000L);
    }

}
