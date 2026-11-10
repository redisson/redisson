package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.ReactiveStreamCommands;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveStreamCommandsTest extends BaseConnectionTest {

    @Test
    public void testXTrimSerializesAllOptions() {
        AtomicReference<Object[]> params = new AtomicReference<>();
        RedissonReactiveStreamCommands commands = new RedissonReactiveStreamCommands(null) {
            @Override
            @SuppressWarnings("unchecked")
            <T> Mono<T> write(byte[] key, Codec codec, RedisCommand<?> command, Object... arguments) {
                params.set(arguments);
                return (Mono<T>) Mono.just(0L);
            }
        };
        byte[] key = bytes("reactive-xtrim-options");
        RedisStreamCommands.TrimOptions trimOptions = RedisStreamCommands.TrimOptions.maxLen(10)
                .approximate()
                .limit(2)
                .deletionPolicy(RedisStreamCommands.StreamDeletionPolicy.ACKNOWLEDGED);
        ReactiveStreamCommands.TrimCommand command = ReactiveStreamCommands.TrimCommand.stream(
                ByteBuffer.wrap(key), RedisStreamCommands.XTrimOptions.trim(trimOptions));

        commands.xTrim(Mono.just(command)).blockFirst();

        assertThat(params.get()).containsExactly(
                key, "MAXLEN", "~", 10L, "LIMIT", 2L, "ACKED");
    }

    @Test
    public void testXTrimWithMinId() {
        byte[] key = bytes("reactive-xtrim");
        add(key, "1-0", "1");
        add(key, "2-0", "2");
        add(key, "3-0", "3");

        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveRedisConnection reactiveConnection = factory.getReactiveConnection();
        RedisStreamCommands.XTrimOptions options = RedisStreamCommands.XTrimOptions.trim(
                RedisStreamCommands.TrimOptions.minId(RecordId.of("2-0")).exact());

        Long trimmed = reactiveConnection.streamCommands()
                .xTrim(ByteBuffer.wrap(key), options)
                .block();

        assertThat(trimmed).isEqualTo(1);
        List<ByteRecord> remaining = connection.streamCommands().xRange(key, Range.unbounded());
        assertThat(remaining).extracting(record -> record.getId().getValue())
                .containsExactly("2-0", "3-0");
    }

    @Test
    public void testXTrimLeavesLimitOutOfExactTrim() {
        byte[] key = bytes("reactive-xtrim-exact-limit");
        add(key, "1-0", "1");
        add(key, "2-0", "2");
        add(key, "3-0", "3");

        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveRedisConnection reactiveConnection = factory.getReactiveConnection();
        RedisStreamCommands.XTrimOptions options = RedisStreamCommands.XTrimOptions.trim(
                RedisStreamCommands.TrimOptions.maxLen(1).limit(10));

        Long trimmed = reactiveConnection.streamCommands()
                .xTrim(ByteBuffer.wrap(key), options)
                .block();

        assertThat(trimmed).isEqualTo(2);
        List<ByteRecord> remaining = connection.streamCommands().xRange(key, Range.unbounded());
        assertThat(remaining).extracting(record -> record.getId().getValue())
                .containsExactly("3-0");
    }

    private static void add(byte[] key, String id, String value) {
        connection.streamCommands().xAdd(StreamRecords.newRecord()
                .in(key)
                .withId(id)
                .ofBytes(Collections.singletonMap(bytes("field"), bytes(value))));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
