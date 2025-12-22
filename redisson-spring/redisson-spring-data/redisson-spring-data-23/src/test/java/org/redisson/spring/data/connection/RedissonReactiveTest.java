package org.redisson.spring.data.connection;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

public class RedissonReactiveTest extends BaseConnectionTest {

    @Test
    public void testGetSet() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(factory);
        Assertions.assertThat(template.opsForValue().get("123").block()).isNull();
        template.opsForValue().set("123", "444").block();
        Assertions.assertThat(template.opsForValue().get("123").block()).isEqualTo("444");
    }

}
