package org.redisson.spring.data.connection;

import org.junit.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.core.types.RedisClientInfo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveServerCommandsTest extends BaseConnectionTest {
    @Test
    public void testGetClientList() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveRedisConnection reactiveConnection = factory.getReactiveConnection();
        Flux<RedisClientInfo> flux = reactiveConnection.serverCommands().getClientList();
        Mono<Long> count = flux.count();
        count.subscribe(s -> assertThat(s).isGreaterThan(10));

    }
}
