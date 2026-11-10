package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnection.CommandResponse;
import org.springframework.data.redis.connection.ReactiveZSetCommands;
import org.springframework.data.redis.connection.ReactiveZSetCommands.BZPopCommand;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedissonReactiveZSetCommandsTest extends BaseConnectionTest {

    private final ReactiveZSetCommands zSetCommands =
            new RedissonConnectionFactory(redisson).getReactiveConnection().zSetCommands();

    @Test
    public void testBZPopWithTimeoutUnderOneSecond() {
        ByteBuffer key = ByteBuffer.wrap("empty-zset".getBytes());
        Duration timeout = Duration.ofMillis(500);

        long start = System.nanoTime();
        assertThat(zSetCommands.bZPopMin(key, timeout).block(Duration.ofSeconds(10))).isNull();
        assertThat(zSetCommands.bZPopMax(key, timeout).block(Duration.ofSeconds(10))).isNull();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(timeout.multipliedBy(2));
    }

    @Test
    public void testBZPopRejectsNegativeTimeout() {
        BZPopCommand command = BZPopCommand.min().from(ByteBuffer.wrap("empty-zset".getBytes()))
                .blockingFor(-500, TimeUnit.MILLISECONDS);

        assertThatThrownBy(() -> zSetCommands.bZPop(Mono.just(command))
                .flatMap(CommandResponse::getOutput)
                .blockFirst(Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testBZPopWaitsOnlyOnTheGivenKey() {
        connection.zSetCommands().zAdd("0".getBytes(), 1, "from-key-zero".getBytes());
        ByteBuffer key = ByteBuffer.wrap("empty-zset".getBytes());

        assertThat(zSetCommands.bZPopMin(key, Duration.ofSeconds(1)).block(Duration.ofSeconds(10))).isNull();
        assertThat(zSetCommands.bZPopMax(key, Duration.ofSeconds(1)).block(Duration.ofSeconds(10))).isNull();
        assertThat(connection.zSetCommands().zCard("0".getBytes())).isEqualTo(1);
    }

}
