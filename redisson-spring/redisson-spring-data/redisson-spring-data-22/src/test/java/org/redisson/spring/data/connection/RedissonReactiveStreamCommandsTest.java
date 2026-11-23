package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveStreamCommands;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveStreamCommandsTest extends BaseConnectionTest {

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
    public void testXGroupDelConsumer() {
        byte[] key = bytes("reactive-xgroup-delconsumer");
        connection.streamCommands().xGroupCreate(key, "group", ReadOffset.from("0-0"));
        add(key, "1-0", "value");
        connection.streamCommands().xReadGroup(Consumer.from("group", "busy"), StreamOffset.create(key, ReadOffset.lastConsumed()));
        connection.streamCommands().xReadGroup(Consumer.from("group", "idle"), StreamOffset.create(key, ReadOffset.lastConsumed()));
        ReactiveStreamCommands commands = new RedissonConnectionFactory(redisson).getReactiveConnection().streamCommands();

        assertThat(commands.xGroupDelConsumer(ByteBuffer.wrap(key), Consumer.from("group", "busy")).block()).isEqualTo("OK");
        assertThat(commands.xGroupDelConsumer(ByteBuffer.wrap(key), Consumer.from("group", "idle")).block()).isEqualTo("OK");
        assertThat(redisson.getStream("reactive-xgroup-delconsumer").listConsumers("group")).isEmpty();
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
