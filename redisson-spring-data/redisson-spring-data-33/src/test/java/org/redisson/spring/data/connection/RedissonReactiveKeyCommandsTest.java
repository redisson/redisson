package org.redisson.spring.data.connection;

import org.junit.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.time.Duration;
import java.util.Collections;

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

    @Test
    public void testPending() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveStringRedisTemplate t = new ReactiveStringRedisTemplate(factory);
        t.opsForStream().createGroup("test", ReadOffset.latest(), "testGroup").block();

        t.opsForStream().add("test", Collections.singletonMap("1", "1")).block();

        assertThat(t.opsForStream().pending("test", "testGroup").block().getTotalPendingMessages()).isEqualTo(0);

        t.opsForStream().read(Consumer.from("testGroup", "test1"), StreamOffset.create("test", ReadOffset.from(">"))).single().block();

        PendingMessages msg = t.opsForStream().pending("test", "testGroup", Range.unbounded(), 10).block();
        assertThat(msg.size()).isEqualTo(1);
    }
}
