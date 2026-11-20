package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveStreamCommands;
import org.springframework.data.redis.connection.RedisStreamCommands.StreamDeletionPolicy;
import org.springframework.data.redis.connection.RedisStreamCommands.StreamEntryDeletionResult;
import org.springframework.data.redis.connection.RedisStreamCommands.XDelOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveXDelExCommandsTest extends BaseConnectionTest {

    private final ReactiveStreamCommands streamCommands =
            new RedissonConnectionFactory(redisson).getReactiveConnection().streamCommands();

    @Test
    public void testXDelEx() {
        byte[] key = "reactive-xdelex".getBytes();
        RecordId id = connection.streamCommands().xAdd(key, Collections.singletonMap("field".getBytes(), "value".getBytes()));

        List<StreamEntryDeletionResult> results = streamCommands
                .xDelEx(ByteBuffer.wrap(key), XDelOptions.defaults(), id, RecordId.of("9-0"))
                .collectList()
                .block();

        assertThat(results).containsExactly(StreamEntryDeletionResult.DELETED, StreamEntryDeletionResult.NOT_FOUND);
        assertThat(connection.streamCommands().xLen(key)).isZero();
    }

    @Test
    public void testXDelExWithAckedPolicyKeepsPendingEntry() {
        byte[] key = "reactive-xdelex-acked".getBytes();
        RecordId id = addPendingRecord(key);

        List<StreamEntryDeletionResult> results = streamCommands
                .xDelEx(ByteBuffer.wrap(key), XDelOptions.deletionPolicy(StreamDeletionPolicy.ACKNOWLEDGED), id)
                .collectList()
                .block();

        assertThat(results).containsExactly(StreamEntryDeletionResult.NOT_DELETED_UNACKNOWLEDGED_OR_STILL_REFERENCED);
        assertThat(connection.streamCommands().xLen(key)).isEqualTo(1);
    }

    @Test
    public void testXAckDel() {
        byte[] key = "reactive-xackdel".getBytes();
        RecordId id = addPendingRecord(key);

        List<StreamEntryDeletionResult> results = streamCommands
                .xAckDel(ByteBuffer.wrap(key), "group", XDelOptions.defaults(), id)
                .collectList()
                .block();

        assertThat(results).containsExactly(StreamEntryDeletionResult.DELETED);
        assertThat(connection.streamCommands().xPending(key, "group").getTotalPendingMessages()).isZero();
        assertThat(connection.streamCommands().xLen(key)).isZero();
    }

    private RecordId addPendingRecord(byte[] key) {
        connection.streamCommands().xGroupCreate(key, "group", ReadOffset.from("0-0"), true);
        RecordId id = connection.streamCommands().xAdd(key, Collections.singletonMap("field".getBytes(), "value".getBytes()));
        connection.streamCommands().xReadGroup(Consumer.from("group", "consumer"),
                StreamOffset.create(key, ReadOffset.lastConsumed()));
        return id;
    }

}
