package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class RedissonStreamTest extends BaseConnectionTest {

    @Test
    public void testXGroupDelConsumer() {
        byte[] key = "xgroup-delconsumer".getBytes();
        connection.streamCommands().xGroupCreate(key, "group", ReadOffset.from("0-0"));
        connection.streamCommands().xAdd(StreamRecords.newRecord()
                .in(key)
                .withId("1-0")
                .ofBytes(Collections.singletonMap("field".getBytes(), "value".getBytes())));
        connection.streamCommands().xReadGroup(Consumer.from("group", "consumer"), StreamOffset.create(key, ReadOffset.lastConsumed()));
        assertThat(connection.streamCommands().xGroupDelConsumer(key, Consumer.from("group", "consumer"))).isTrue();

        connection.streamCommands().xReadGroup(Consumer.from("group", "idle"), StreamOffset.create(key, ReadOffset.from("0-0")));
        assertThat(connection.streamCommands().xGroupDelConsumer(key, Consumer.from("group", "idle"))).isTrue();
        assertThat(redisson.getStream("xgroup-delconsumer").listConsumers("group")).isEmpty();
    }

    @Test
    public void testXReadSeveralStreamsOverResp3() {
        Config config = createConfig();
        config.setProtocol(Protocol.RESP3);
        RedissonClient client = Redisson.create(config);
        try {
            RedisConnection resp3 = new RedissonConnectionFactory(client).getConnection();
            byte[] first = "xread-resp3-a".getBytes();
            byte[] second = "xread-resp3-b".getBytes();
            addRecords(first, "1-0");
            addRecords(second, "2-0");

            List<ByteRecord> records = resp3.streamCommands().xRead(StreamOffset.create(first, ReadOffset.from("0-0")),
                                                                    StreamOffset.create(second, ReadOffset.from("0-0")));
            assertThat(records).extracting(r -> new String(r.getStream()), r -> r.getId().getValue())
                    .containsExactly(tuple("xread-resp3-a", "1-0"), tuple("xread-resp3-b", "2-0"));

            resp3.streamCommands().xGroupCreate(first, "group", ReadOffset.from("0-0"));
            resp3.streamCommands().xGroupCreate(second, "group", ReadOffset.from("0-0"));
            List<ByteRecord> groupRecords = resp3.streamCommands().xReadGroup(Consumer.from("group", "consumer"),
                    StreamOffset.create(first, ReadOffset.lastConsumed()), StreamOffset.create(second, ReadOffset.lastConsumed()));
            assertThat(groupRecords).extracting(r -> new String(r.getStream()), r -> r.getId().getValue())
                    .containsExactly(tuple("xread-resp3-a", "1-0"), tuple("xread-resp3-b", "2-0"));
        } finally {
            client.shutdown();
        }
    }

    @Test
    public void testXReadBinaryStreamKey() {
        byte[] key = {(byte) 0xAC, (byte) 0xED, 0, 5, 's'};
        addRecords(key, "1-0");

        List<ByteRecord> records = connection.streamCommands().xRead(StreamOffset.create(key, ReadOffset.from("0-0")));
        assertThat(records).extracting(ByteRecord::getStream).containsExactly(key);

        connection.streamCommands().xGroupCreate(key, "group", ReadOffset.from("0-0"));
        List<ByteRecord> groupRecords = connection.streamCommands().xReadGroup(Consumer.from("group", "consumer"),
                                                                    StreamOffset.create(key, ReadOffset.lastConsumed()));
        assertThat(groupRecords).extracting(ByteRecord::getStream).containsExactly(key);
    }

    @Test
    public void testReadWithJdkSerializedKey() {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(new RedissonConnectionFactory(redisson));
        template.afterPropertiesSet();
        template.opsForStream().add("jdk-stream", Collections.singletonMap("field", "value"));

        List<MapRecord<Object, Object, Object>> records = template.opsForStream().read(StreamOffset.fromStart("jdk-stream"));

        assertThat(records).extracting(MapRecord::getStream).containsExactly("jdk-stream");
        assertThat(records.get(0).getValue()).containsEntry("field", "value");
    }

    private void addRecords(byte[] key, String... ids) {
        for (String id : ids) {
            ByteRecord record = StreamRecords.newRecord().in(key).withId(id)
                    .ofBytes(Collections.singletonMap("field".getBytes(), id.getBytes()));
            connection.streamCommands().xAdd(record);
        }
    }

}
