package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveStreamCommands;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.ByteBufferRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedissonReactiveXAddCommandsTest extends BaseConnectionTest {

    @Test
    public void testXAddRejectsNullOptions() {
        ByteBufferRecord record = StreamRecords.newRecord()
                .in(ByteBuffer.wrap(bytes("reactive-xadd-null-options")))
                .ofBuffer(Collections.singletonMap(
                        ByteBuffer.wrap(bytes("field")), ByteBuffer.wrap(bytes("value"))));
        ReactiveStreamCommands.AddStreamRecord command = ReactiveStreamCommands.AddStreamRecord
                .of(record)
                .withOptions(null);
        RedissonReactiveStreamCommands commands = new RedissonReactiveStreamCommands(null);

        assertThatThrownBy(() -> commands.xAdd(Mono.just(command)).blockFirst(Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Options must not be null!");
    }

    @Test
    public void testXAddSerializesAllOptions() {
        AtomicReference<Object[]> params = new AtomicReference<>();
        RedissonReactiveStreamCommands commands = new RedissonReactiveStreamCommands(null) {
            @Override
            @SuppressWarnings("unchecked")
            <T> Mono<T> write(byte[] key, Codec codec, RedisCommand<?> command, Object... arguments) {
                params.set(arguments);
                return (Mono<T>) Mono.just(RecordId.of("1-0"));
            }
        };
        byte[] key = bytes("reactive-xadd-options");
        byte[] field = bytes("field");
        byte[] value = bytes("value");
        ByteBufferRecord record = StreamRecords.newRecord()
                .in(ByteBuffer.wrap(key))
                .ofBuffer(Collections.singletonMap(ByteBuffer.wrap(field), ByteBuffer.wrap(value)));
        RedisStreamCommands.TrimOptions trimOptions = RedisStreamCommands.TrimOptions
                .minId(RecordId.of("2-0"))
                .approximate()
                .limit(2)
                .deletionPolicy(RedisStreamCommands.StreamDeletionPolicy.ACKNOWLEDGED);
        RedisStreamCommands.XAddOptions options = RedisStreamCommands.XAddOptions
                .trim(trimOptions)
                .nomkstream(true);
        ReactiveStreamCommands.AddStreamRecord command = ReactiveStreamCommands.AddStreamRecord
                .of(record)
                .withOptions(options);

        commands.xAdd(Mono.just(command)).blockFirst();

        Object[] arguments = params.get();
        assertThat((byte[]) arguments[0]).containsExactly(key);
        assertThat(Arrays.copyOfRange(arguments, 1, 9)).containsExactly(
                "NOMKSTREAM", "ACKED", "MINID", "~", "2-0", "LIMIT", 2L, "*");
        assertThat((byte[]) arguments[9]).containsExactly(field);
        assertThat((byte[]) arguments[10]).containsExactly(value);
    }

    @Test
    public void testXAddWithNoMkStreamDoesNotCreateStream() {
        byte[] key = bytes("reactive-xadd-nomkstream");
        ByteBufferRecord record = StreamRecords.newRecord()
                .in(ByteBuffer.wrap(key))
                .ofBuffer(Collections.singletonMap(
                        ByteBuffer.wrap(bytes("field")), ByteBuffer.wrap(bytes("value"))));
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveRedisConnection reactiveConnection = factory.getReactiveConnection();

        RecordId result = reactiveConnection.streamCommands()
                .xAdd(record, RedisStreamCommands.XAddOptions.makeNoStream())
                .block();

        assertThat(result).isNull();
        assertThat(redisson.getKeys().countExists("reactive-xadd-nomkstream")).isZero();
    }

    @Test
    public void testXAddLeavesLimitOutOfExactTrim() {
        byte[] key = bytes("reactive-xadd-exact-limit");
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveRedisConnection reactiveConnection = factory.getReactiveConnection();
        RedisStreamCommands.XAddOptions options = RedisStreamCommands.XAddOptions.trim(
                RedisStreamCommands.TrimOptions.maxLen(2).limit(10));

        for (String id : Arrays.asList("1-0", "2-0", "3-0", "4-0")) {
            ByteBufferRecord record = StreamRecords.newRecord()
                    .in(ByteBuffer.wrap(key))
                    .withId(id)
                    .ofBuffer(Collections.singletonMap(
                            ByteBuffer.wrap(bytes("field")), ByteBuffer.wrap(bytes(id))));
            reactiveConnection.streamCommands().xAdd(record, options).block();
        }

        assertThat(connection.streamCommands().xRange(key, Range.unbounded()))
                .extracting(entry -> entry.getId().getValue())
                .containsExactly("3-0", "4-0");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
