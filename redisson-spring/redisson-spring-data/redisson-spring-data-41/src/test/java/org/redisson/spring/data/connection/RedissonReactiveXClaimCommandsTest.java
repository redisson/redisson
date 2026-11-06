package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.ByteBufferRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveXClaimCommandsTest extends BaseConnectionTest {

    @Test
    public void testXClaimSerializesAllOptions() {
        AtomicReference<Object[]> params = new AtomicReference<>();
        RedissonReactiveStreamCommands commands = new RedissonReactiveStreamCommands(null) {
            @Override
            @SuppressWarnings("unchecked")
            <T> Mono<T> write(byte[] key, Codec codec, RedisCommand<?> command, Object... arguments) {
                params.set(arguments);
                return (Mono<T>) Mono.just(Collections.emptyMap());
            }
        };
        byte[] key = bytes("reactive-xclaim-options");
        RedisStreamCommands.XClaimOptions options = RedisStreamCommands.XClaimOptions
                .minIdle(Duration.ofSeconds(1))
                .ids("1-0", "2-0")
                .idle(Duration.ofSeconds(2))
                .time(Instant.ofEpochMilli(3_000))
                .retryCount(4)
                .force();

        commands.xClaim(ByteBuffer.wrap(key), "group", "consumer", options)
                .collectList()
                .block();

        Object[] arguments = params.get();
        assertThat((byte[]) arguments[0]).containsExactly(key);
        assertThat(Arrays.copyOfRange(arguments, 1, arguments.length)).containsExactly(
                "group", "consumer", 1_000L, "1-0", "2-0",
                "IDLE", 2_000L, "TIME", 3_000L, "RETRYCOUNT", 4L, "FORCE");

        commands.xClaimJustId(ByteBuffer.wrap(key), "group", "consumer", options)
                .collectList()
                .block();

        arguments = params.get();
        assertThat(Arrays.copyOfRange(arguments, 1, arguments.length)).containsExactly(
                "group", "consumer", 1_000L, "1-0", "2-0",
                "IDLE", 2_000L, "TIME", 3_000L, "RETRYCOUNT", 4L, "FORCE", "JUSTID");
    }

    @Test
    public void testXClaimWithForceCreatesPendingEntry() {
        byte[] key = bytes("reactive-xclaim-force");
        connection.streamCommands().xGroupCreate(key, "group", ReadOffset.from("0-0"), true);
        RecordId id = connection.streamCommands().xAdd(
                key, Collections.singletonMap(bytes("field"), bytes("value")));
        RedisStreamCommands.XClaimOptions options = RedisStreamCommands.XClaimOptions
                .minIdle(Duration.ZERO)
                .ids(id)
                .force();
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveRedisConnection reactiveConnection = factory.getReactiveConnection();

        List<ByteBufferRecord> claimed = reactiveConnection.streamCommands()
                .xClaim(ByteBuffer.wrap(key), "group", "consumer", options)
                .collectList()
                .block();

        assertThat(claimed).extracting(record -> record.getId().getValue())
                .containsExactly(id.getValue());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
