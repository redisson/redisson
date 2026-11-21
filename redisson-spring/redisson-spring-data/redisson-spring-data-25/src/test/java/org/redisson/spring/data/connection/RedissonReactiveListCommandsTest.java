package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveListCommands;
import org.springframework.data.redis.connection.ReactiveListCommands.LPosCommand;
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
    public void testLPosWithCount() {
        connection.listCommands().rPush("lpos".getBytes(), "a".getBytes(), "b".getBytes(), "c".getBytes(), "b".getBytes(), "b".getBytes());
        ByteBuffer key = ByteBuffer.wrap("lpos".getBytes());
        ByteBuffer element = ByteBuffer.wrap("b".getBytes());

        assertThat(listCommands.lPos(key, element).block()).isEqualTo(1);
        assertThat(listCommands.lPos(LPosCommand.lPosOf(element).from(key).count(2)).collectList().block()).containsExactly(1L, 3L);
        assertThat(listCommands.lPos(LPosCommand.lPosOf(element).from(key).rank(-1).count(0)).collectList().block()).containsExactly(4L, 3L, 1L);
        assertThat(listCommands.lPos(LPosCommand.lPosOf(ByteBuffer.wrap("x".getBytes())).from(key).count(2)).collectList().block()).isEmpty();
        assertThat(listCommands.lPos(key, ByteBuffer.wrap("x".getBytes())).blockOptional()).isEmpty();
    }

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
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(timeout.multipliedBy(2));
    }

    @Test
    public void testBRPopLPushWithTimeoutUnderOneSecond() {
        long start = System.nanoTime();
        assertThat(listCommands.bRPopLPush(source, destination, TIMEOUT).block(HANG_BOUND)).isNull();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(TIMEOUT);
    }

}
