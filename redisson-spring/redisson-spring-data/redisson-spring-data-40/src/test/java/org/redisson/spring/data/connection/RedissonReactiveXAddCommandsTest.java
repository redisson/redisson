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
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveXAddCommandsTest extends BaseConnectionTest {

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

        commands.xAdd(Mono.just(ReactiveStreamCommands.AddStreamRecord.of(record)
                .makeNoStream()
                .minId(RecordId.of("2-0"))
                .approximateTrimming(true))).blockFirst();

        Object[] arguments = params.get();
        assertThat((byte[]) arguments[0]).containsExactly(key);
        assertThat(Arrays.copyOfRange(arguments, 1, 6)).containsExactly("NOMKSTREAM", "MINID", "~", "2-0", "*");
        assertThat((byte[]) arguments[6]).containsExactly(field);
        assertThat((byte[]) arguments[7]).containsExactly(value);

        commands.xAdd(Mono.just(ReactiveStreamCommands.AddStreamRecord.of(record)
                .maxlen(10)
                .approximateTrimming(true))).blockFirst();

        assertThat(Arrays.copyOfRange(params.get(), 1, 5)).containsExactly("MAXLEN", "~", 10L, "*");
    }

    @Test
    public void testXAddWithNoMkStreamDoesNotCreateStream() {
        byte[] key = bytes("reactive-xadd-nomkstream");
        ByteBufferRecord record = StreamRecords.newRecord()
                .in(ByteBuffer.wrap(key))
                .ofBuffer(Collections.singletonMap(
                        ByteBuffer.wrap(bytes("field")), ByteBuffer.wrap(bytes("value"))));
        ReactiveRedisConnection reactiveConnection = new RedissonConnectionFactory(redisson).getReactiveConnection();

        RecordId result = reactiveConnection.streamCommands()
                .xAdd(record, RedisStreamCommands.XAddOptions.makeNoStream())
                .block();

        assertThat(result).isNull();
        assertThat(redisson.getKeys().countExists("reactive-xadd-nomkstream")).isZero();
    }

    @Test
    public void testXAddTrimsByMinId() {
        byte[] key = bytes("reactive-xadd-minid");
        ReactiveRedisConnection reactiveConnection = new RedissonConnectionFactory(redisson).getReactiveConnection();
        RedisStreamCommands.XAddOptions options = RedisStreamCommands.XAddOptions.none().minId(RecordId.of("3-0"));

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
