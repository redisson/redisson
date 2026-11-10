package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveListCommands;
import org.springframework.data.redis.connection.ReactiveListCommands.Direction;
import org.springframework.data.redis.connection.ReactiveListCommands.PopResult;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveListCommandsTest extends BaseConnectionTest {

    private static final Duration TIMEOUT = Duration.ofMillis(500);

    private static final Duration HANG_BOUND = Duration.ofSeconds(10);

    private final ReactiveListCommands listCommands =
            new RedissonConnectionFactory(redisson).getReactiveConnection().listCommands();

    private final ByteBuffer source = ByteBuffer.wrap("empty-list".getBytes());

    private final ByteBuffer destination = ByteBuffer.wrap("destination-list".getBytes());

    @Test
    public void testBLPopAndBRPopWithTimeoutUnderOneSecond() {
        long start = System.nanoTime();
        assertThat(listCommands.blPop(Collections.singletonList(source), TIMEOUT)
                .map(PopResult::getRaw)
                .defaultIfEmpty(Collections.emptyList())
                .block(HANG_BOUND)).isEmpty();
        assertThat(listCommands.brPop(Collections.singletonList(source), TIMEOUT)
                .map(PopResult::getRaw)
                .defaultIfEmpty(Collections.emptyList())
                .block(HANG_BOUND)).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(TIMEOUT.multipliedBy(2));
    }

    @Test
    public void testTimeoutUnderOneMillisecond() {
        Duration timeout = Duration.ofNanos(500_000);

        long start = System.nanoTime();
        assertThat(listCommands.blPop(Collections.singletonList(source), timeout)
                .map(PopResult::getRaw)
                .defaultIfEmpty(Collections.emptyList())
                .block(HANG_BOUND)).isEmpty();
        assertThat(listCommands.bRPopLPush(source, destination, timeout).block(HANG_BOUND)).isNull();
        assertThat(listCommands.bLMove(source, destination, Direction.LEFT, Direction.RIGHT, timeout).block(HANG_BOUND)).isNull();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(timeout.multipliedBy(3));
    }

    @Test
    public void testBRPopLPushWithTimeoutUnderOneSecond() {
        long start = System.nanoTime();
        assertThat(listCommands.bRPopLPush(source, destination, TIMEOUT).block(HANG_BOUND)).isNull();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(TIMEOUT);
    }

    @Test
    public void testBLMoveWithTimeoutUnderOneSecond() {
        long start = System.nanoTime();
        assertThat(listCommands.bLMove(source, destination, Direction.LEFT, Direction.RIGHT, TIMEOUT).block(HANG_BOUND)).isNull();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(TIMEOUT);
    }

}
