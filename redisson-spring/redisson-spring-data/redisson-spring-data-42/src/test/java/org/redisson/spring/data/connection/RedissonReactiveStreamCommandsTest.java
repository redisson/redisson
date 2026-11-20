package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.ReactiveStreamCommands;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    public void testXReadKeepsStreamKeysAndOrder() {
        byte[] binary = {(byte) 0xAC, (byte) 0xED, 0, 5, 'r'};
        byte[] first = bytes("reactive-xread-a");
        byte[] second = bytes("reactive-xread-b");
        add(binary, "1-0", "1");
        add(first, "1-0", "2");
        add(second, "1-0", "3");
        ReactiveRedisConnection reactive = new RedissonConnectionFactory(redisson).getReactiveConnection();

        List<ByteBuffer> streams = reactive.streamCommands().xRead(
                        StreamOffset.create(ByteBuffer.wrap(binary), ReadOffset.from("0-0")),
                        StreamOffset.create(ByteBuffer.wrap(first), ReadOffset.from("0-0")),
                        StreamOffset.create(ByteBuffer.wrap(second), ReadOffset.from("0-0")))
                .map(record -> record.getStream())
                .collectList().block();
        assertThat(streams).containsExactly(ByteBuffer.wrap(binary), ByteBuffer.wrap(first), ByteBuffer.wrap(second));

        connection.streamCommands().xGroupCreate(binary, "group", ReadOffset.from("0-0"));
        List<ByteBuffer> groupStreams = reactive.streamCommands().xReadGroup(Consumer.from("group", "consumer"),
                        StreamOffset.create(ByteBuffer.wrap(binary), ReadOffset.lastConsumed()))
                .map(record -> record.getStream())
                .collectList().block();
        assertThat(groupStreams).containsExactly(ByteBuffer.wrap(binary));
    }

    @Test
    public void testXInfoGroupsAndConsumersKeepAllFields() {
        byte[] key = bytes("reactive-xinfo-fields");
        add(key, "1-0", "1");
        connection.streamCommands().xGroupCreate(key, "group", ReadOffset.from("0-0"));
        connection.streamCommands().xReadGroup(Consumer.from("group", "consumer"), StreamOffset.create(key, ReadOffset.lastConsumed()));
        ReactiveRedisConnection reactive = new RedissonConnectionFactory(redisson).getReactiveConnection();

        StreamInfo.XInfoGroup group = reactive.streamCommands().xInfoGroups(ByteBuffer.wrap(key)).blockFirst();
        assertThat(group.getRaw()).containsKeys("name", "consumers", "pending", "last-delivered-id", "entries-read", "lag");
        assertThat(group.consumerCount()).isEqualTo(1);
        assertThat(group.lastDeliveredId()).isEqualTo("1-0");

        StreamInfo.XInfoConsumer consumer = reactive.streamCommands().xInfoConsumers(ByteBuffer.wrap(key), "group").blockFirst();
        assertThat(consumer.getRaw()).containsKeys("name", "pending", "idle", "inactive");
        assertThat(consumer.consumerName()).isEqualTo("consumer");
        assertThat(consumer.pendingCount()).isEqualTo(1);
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

    @Test
    public void testXPendingWithoutCount() {
        byte[] key = bytes("reactive-xpending-no-count");
        connection.streamCommands().xGroupCreate(key, "group", ReadOffset.from("0-0"), true);
        for (int i = 1; i <= 12; i++) {
            add(key, i + "-0", "value" + i);
        }
        connection.streamCommands().xReadGroup(Consumer.from("group", "consumer"), StreamOffset.create(key, ReadOffset.lastConsumed()));
        ReactiveStreamCommands commands = new RedissonConnectionFactory(redisson).getReactiveConnection().streamCommands();

        assertThat(commands.xPending(ByteBuffer.wrap(key), Consumer.from("group", "consumer")).block().size()).isEqualTo(12);
        assertThat(commands.xPending(ByteBuffer.wrap(key), "group", "consumer").block().size()).isEqualTo(12);
    }

    @Test
    public void testXInfo() {
        byte[] key = bytes("reactive-xinfo");
        add(key, "1-0", "first");
        add(key, "2-0", "last");
        connection.streamCommands().xGroupCreate(key, "group", ReadOffset.from("0-0"));
        ReactiveStreamCommands commands = new RedissonConnectionFactory(redisson).getReactiveConnection().streamCommands();

        StreamInfo.XInfoStream info = commands.xInfo(ByteBuffer.wrap(key)).block();
        assertThat(info.streamLength()).isEqualTo(2);
        assertThat(info.groupCount()).isEqualTo(1);
        assertThat(info.firstEntryId()).isEqualTo("1-0");
        assertThat(info.lastEntryId()).isEqualTo("2-0");
        assertThat((Map<Object, Object>) info.getFirstEntry().get("1-0")).containsEntry("field", "first");
    }

    @Test
    public void testXGroupDelConsumer() {
        byte[] key = bytes("reactive-xgroup-delconsumer");
        connection.streamCommands().xGroupCreate(key, "group", ReadOffset.from("0-0"), true);
        add(key, "1-0", "value");
        connection.streamCommands().xReadGroup(Consumer.from("group", "busy"), StreamOffset.create(key, ReadOffset.lastConsumed()));
        connection.streamCommands().xReadGroup(Consumer.from("group", "idle"), StreamOffset.create(key, ReadOffset.lastConsumed()));
        ReactiveStreamCommands commands = new RedissonConnectionFactory(redisson).getReactiveConnection().streamCommands();

        assertThat(commands.xGroupDelConsumer(ByteBuffer.wrap(key), Consumer.from("group", "busy")).block()).isEqualTo("OK");
        assertThat(commands.xGroupDelConsumer(ByteBuffer.wrap(key), Consumer.from("group", "idle")).block()).isEqualTo("OK");
        assertThat(connection.streamCommands().xInfoConsumers(key, "group").size()).isZero();
    }

    @Test
    public void testXGroupCreateCreatesTheStreamOnlyWithMkStream() {
        ByteBuffer key = ByteBuffer.wrap(bytes("reactive-xgroup-mkstream"));
        ReactiveStreamCommands commands = new RedissonConnectionFactory(redisson).getReactiveConnection().streamCommands();

        assertThatThrownBy(() -> commands.xGroupCreate(key, "group", ReadOffset.from("0-0")).block())
                .hasMessageContaining("requires the key to exist");
        assertThat(connection.keyCommands().exists(bytes("reactive-xgroup-mkstream"))).isFalse();

        assertThat(commands.xGroupCreate(key, "group", ReadOffset.from("0-0"), true).block()).isEqualTo("OK");
        assertThat(connection.keyCommands().exists(bytes("reactive-xgroup-mkstream"))).isTrue();
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
