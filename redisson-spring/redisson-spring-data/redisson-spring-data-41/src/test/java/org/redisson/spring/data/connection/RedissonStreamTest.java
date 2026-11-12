package org.redisson.spring.data.connection;

import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.protocol.CommandData;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.redisson.connection.ClientConnectionsEntry;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.RedisStreamCommands.StreamEntryDeletionResult;
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOptions;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.RedisStreamCommands.XPendingOptions;
import org.springframework.data.redis.connection.RedisStreamCommands.XDelOptions;
import org.springframework.data.redis.connection.RedisStreamCommands.XTrimOptions;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

/**
 * @author Nikita Koksharov
 */
public class RedissonStreamTest extends BaseConnectionTest {

    @Test
    public void testReattachment() throws InterruptedException {
        withSentinel((nodes, config) -> {
            RedissonClient redissonClient = Redisson.create(config);

            RedisConnectionFactory redisConnectionFactory = new RedissonConnectionFactory(redissonClient);

            StreamMessageListenerContainer listenerContainer = StreamMessageListenerContainer.create(redisConnectionFactory, getOptions());


            Consumer consumer = Consumer.from("group", "consumer1");
            StreamOffset<String> streamOffset = StreamOffset.create("test", ReadOffset.from(">"));
            String channel = "test";
            AtomicInteger counter = new AtomicInteger();
            Subscription subscription = listenerContainer.register(getReadRequest(consumer, streamOffset),
                    listener(redisConnectionFactory, channel, consumer, counter));

            StringRedisTemplate t1 = new StringRedisTemplate(redisConnectionFactory);
            t1.opsForStream().createGroup("test", "group");

            listenerContainer.start();

            AtomicReference<Boolean> invoked = new AtomicReference<>();

            new MockUp<ClientConnectionsEntry>() {

                @Mock
                void reattachBlockingQueue(Invocation inv, CommandData<?, ?> commandData) {
                    try {
                        inv.proceed(commandData);
                        invoked.compareAndSet(null, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                        invoked.set(false);
                        throw e;
                    }
                }
            };

            for (int i = 0; i < 3; i++) {
                StringRedisTemplate stringRedisTemplate = new StringRedisTemplate(redisConnectionFactory);
                ObjectRecord<String, String> record = StreamRecords.newRecord()
                        .ofObject("message")
                        .withStreamKey(channel);
                RecordId recordId = stringRedisTemplate.opsForStream().add(record);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                nodes.get(0).stop();
                try {
                    Thread.sleep(15000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            assertThat(invoked.get()).isTrue();

            Assertions.assertThat(counter.get()).isEqualTo(3);
            listenerContainer.stop();
            redissonClient.shutdown();
        }, 2);
    }

    private StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, String>> getOptions() {
        return StreamMessageListenerContainer
                .StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofSeconds(1))
                .targetType(String.class)
                .build();
    }

    private StreamMessageListenerContainer.StreamReadRequest<String> getReadRequest(Consumer consumer, StreamOffset<String> streamOffset) {
        return StreamMessageListenerContainer.StreamReadRequest
                .builder(streamOffset)
                .consumer(consumer)
                .autoAcknowledge(false)
                .cancelOnError((err) -> false)  // do not stop consuming after error
                .build();
    }

    private <T> StreamListener listener(RedisConnectionFactory redisConnectionFactory, String channel, Consumer consumer, AtomicInteger counter) {

        return message -> {
            try {
                System.out.println("Acknowledging message: " + message.getId());
                StringRedisTemplate stringRedisTemplate = new StringRedisTemplate(redisConnectionFactory);
                stringRedisTemplate.opsForStream().acknowledge(channel, consumer.getGroup(), message.getId());
                System.out.println("RECEIVED " + consumer + " " + message);
                counter.incrementAndGet();
            } catch(Exception e) {
                e.printStackTrace();
            }
        };
    }

    @Test
    public void testPending() {
        connection.streamCommands().xGroupCreate("test".getBytes(), "testGroup", ReadOffset.latest(), true);

        PendingMessages p = connection.streamCommands().xPending("test".getBytes(), Consumer.from("testGroup", "test1"));
        assertThat(p.size()).isEqualTo(0);

        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("1".getBytes(), "1".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("2".getBytes(), "2".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("3".getBytes(), "3".getBytes()));

        List<ByteRecord> l = connection.streamCommands().xReadGroup(Consumer.from("testGroup", "test1"), StreamOffset.create("test".getBytes(), ReadOffset.from(">")));
        assertThat(l.size()).isEqualTo(3);

        PendingMessages p2 = connection.streamCommands().xPending("test".getBytes(), Consumer.from("testGroup", "test1"));
        assertThat(p2.size()).isEqualTo(3);
    }

    @Test
    public void testGroups() {
        connection.streamCommands().xGroupCreate("test".getBytes(), "testGroup", ReadOffset.latest(), true);
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("1".getBytes(), "1".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("2".getBytes(), "2".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("3".getBytes(), "3".getBytes()));

        StreamInfo.XInfoGroups groups = connection.streamCommands().xInfoGroups("test".getBytes());
        assertThat(groups.size()).isEqualTo(1);
        assertThat(groups.get(0).groupName()).isEqualTo("testGroup");
        assertThat(groups.get(0).pendingCount()).isEqualTo(0);
        assertThat(groups.get(0).consumerCount()).isEqualTo(0);
        assertThat(groups.get(0).lastDeliveredId()).isEqualTo("0-0");
    }

    @Test
    public void testConsumers() {
        connection.streamCommands().xGroupCreate("test".getBytes(), "testGroup", ReadOffset.latest(), true);
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("1".getBytes(), "1".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("2".getBytes(), "2".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("3".getBytes(), "3".getBytes()));

        connection.streamCommands().xGroupCreate("test".getBytes(), "testGroup2", ReadOffset.latest(), true);
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("1".getBytes(), "1".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("2".getBytes(), "2".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("3".getBytes(), "3".getBytes()));

        List<ByteRecord> list = connection.streamCommands().xReadGroup(Consumer.from("testGroup", "consumer1"),
                                    StreamOffset.create("test".getBytes(), ReadOffset.lastConsumed()));
        assertThat(list.size()).isEqualTo(6);

        StreamInfo.XInfoStream info = connection.streamCommands().xInfo("test".getBytes());
        assertThat(info.streamLength()).isEqualTo(6);

        StreamInfo.XInfoConsumers s1 = connection.streamCommands().xInfoConsumers("test".getBytes(), "testGroup");
        assertThat(s1.getConsumerCount()).isEqualTo(1);
        assertThat(s1.get(0).consumerName()).isEqualTo("consumer1");
        assertThat(s1.get(0).pendingCount()).isEqualTo(6);
        assertThat(s1.get(0).idleTimeMs()).isLessThan(100L);
        assertThat(s1.get(0).groupName()).isEqualTo("testGroup");
    }

    @Test
    public void testXAddNoMkStreamDoesNotCreateStream() {
        byte[] key = "xadd-nomkstream".getBytes();
        ByteRecord record = StreamRecords.newRecord().in(key)
                .ofBytes(Collections.singletonMap("field".getBytes(), "value".getBytes()));

        RecordId id = connection.streamCommands().xAdd(record, XAddOptions.makeNoStream());

        assertThat(id).isNull();
        assertThat(connection.keyCommands().exists(key)).isFalse();
    }

    @Test
    public void testXAddTrimsByMinId() {
        byte[] key = "xadd-minid".getBytes();
        addRecords(key, XAddOptions.none(), "1-0", "2-0", "3-0");

        addRecords(key, XAddOptions.trim(TrimOptions.minId(RecordId.of("3-0"))), "4-0");

        assertThat(connection.streamCommands().xRange(key, Range.unbounded()))
                .extracting(record -> record.getId().getValue())
                .containsExactly("3-0", "4-0");
    }

    @Test
    public void testXAddTrimsApproximatelyWithLimit() {
        byte[] key = "xadd-approximate".getBytes();
        addRecords(key, XAddOptions.none(), ids(1, 300));

        addRecords(key, XAddOptions.trim(TrimOptions.maxLen(0).approximate().limit(100)), "301-0");

        assertThat(connection.streamCommands().xLen(key)).isEqualTo(201);
    }

    @Test
    public void testXAddLeavesLimitOutOfExactTrim() {
        byte[] key = "xadd-exact-limit".getBytes();
        addRecords(key, XAddOptions.none(), "1-0", "2-0", "3-0");

        addRecords(key, XAddOptions.trim(TrimOptions.maxLen(2).limit(10)), "4-0");

        assertThat(connection.streamCommands().xRange(key, Range.unbounded()))
                .extracting(record -> record.getId().getValue())
                .containsExactly("3-0", "4-0");
    }

    @Test
    public void testXAddTrimsWithDeletionPolicy() {
        byte[] key = "xadd-acked".getBytes();
        readIntoGroup(key, "1-0", "2-0");
        connection.streamCommands().xAck(key, "group", RecordId.of("1-0"));

        addRecords(key, XAddOptions.trim(TrimOptions.maxLen(1)
                .deletionPolicy(RedisStreamCommands.StreamDeletionPolicy.ACKNOWLEDGED)), "3-0");

        assertThat(connection.streamCommands().xRange(key, Range.unbounded()))
                .extracting(record -> record.getId().getValue())
                .containsExactly("2-0", "3-0");
    }

    @Test
    public void testXTrimByMinId() {
        byte[] key = "xtrim-minid".getBytes();
        addRecords(key, XAddOptions.none(), "1-0", "2-0", "3-0");

        Long trimmed = connection.streamCommands().xTrim(key, XTrimOptions.trim(TrimOptions.minId(RecordId.of("2-0"))));

        assertThat(trimmed).isEqualTo(1);
        assertThat(connection.streamCommands().xRange(key, Range.unbounded()))
                .extracting(record -> record.getId().getValue())
                .containsExactly("2-0", "3-0");
    }

    @Test
    public void testXTrimApproximatelyWithLimit() {
        byte[] key = "xtrim-approximate".getBytes();
        addRecords(key, XAddOptions.none(), ids(1, 300));

        Long trimmed = connection.streamCommands().xTrim(key, XTrimOptions.trim(TrimOptions.maxLen(0).approximate().limit(100)));

        assertThat(trimmed).isEqualTo(100);
        assertThat(connection.streamCommands().xLen(key)).isEqualTo(200);
    }

    @Test
    public void testXTrimLeavesLimitOutOfExactTrim() {
        byte[] key = "xtrim-exact-limit".getBytes();
        addRecords(key, XAddOptions.none(), "1-0", "2-0", "3-0");

        Long trimmed = connection.streamCommands().xTrim(key, XTrimOptions.trim(TrimOptions.maxLen(1).limit(10)));

        assertThat(trimmed).isEqualTo(2);
        assertThat(connection.streamCommands().xRange(key, Range.unbounded()))
                .extracting(record -> record.getId().getValue())
                .containsExactly("3-0");
    }

    @Test
    public void testXTrimWithDeletionPolicy() {
        byte[] key = "xtrim-acked".getBytes();
        readIntoGroup(key, "1-0", "2-0", "3-0");
        connection.streamCommands().xAck(key, "group", RecordId.of("1-0"));

        Long trimmed = connection.streamCommands().xTrim(key, XTrimOptions.trim(TrimOptions.maxLen(0)
                .deletionPolicy(RedisStreamCommands.StreamDeletionPolicy.ACKNOWLEDGED)));

        assertThat(trimmed).isEqualTo(1);
        assertThat(connection.streamCommands().xRange(key, Range.unbounded()))
                .extracting(record -> record.getId().getValue())
                .containsExactly("2-0", "3-0");
    }

    @Test
    public void testXDelEx() {
        byte[] key = "xdelex".getBytes();
        addRecords(key, XAddOptions.none(), "1-0", "2-0");

        List<StreamEntryDeletionResult> results = connection.streamCommands().xDelEx(key,
                XDelOptions.defaults(), RecordId.of("1-0"), RecordId.of("9-0"));

        assertThat(results).containsExactly(StreamEntryDeletionResult.DELETED, StreamEntryDeletionResult.NOT_FOUND);
        assertThat(connection.streamCommands().xLen(key)).isEqualTo(1);
    }

    @Test
    public void testXAckDel() {
        byte[] key = "xackdel".getBytes();
        readIntoGroup(key, "1-0");

        List<StreamEntryDeletionResult> results = connection.streamCommands().xAckDel(key, "group",
                XDelOptions.defaults(), RecordId.of("1-0"));

        assertThat(results).containsExactly(StreamEntryDeletionResult.DELETED);
        assertThat(connection.streamCommands().xPending(key, "group").getTotalPendingMessages()).isZero();
    }

    @Test
    public void testXDelExAndXAckDelInPipeline() {
        byte[] key = "xdelex-pipeline".getBytes();
        readIntoGroup(key, "1-0", "2-0");

        RedissonConnection pipelined = new RedissonConnection(redisson);
        pipelined.openPipeline();
        assertThat(pipelined.streamCommands().xDelEx(key, XDelOptions.defaults(), RecordId.of("1-0"))).isNull();
        assertThat(pipelined.streamCommands().xAckDel(key, "group", XDelOptions.defaults(), RecordId.of("2-0"))).isNull();
        List<Object> results = pipelined.closePipeline();

        assertThat(results).containsExactly(
                Collections.singletonList(StreamEntryDeletionResult.DELETED),
                Collections.singletonList(StreamEntryDeletionResult.DELETED));
    }

    @Test
    public void testXDelExAndXAckDelInTransaction() {
        byte[] key = "xdelex-multi".getBytes();
        readIntoGroup(key, "1-0", "2-0");

        RedissonConnection transaction = new RedissonConnection(redisson);
        transaction.multi();
        transaction.streamCommands().xDelEx(key, XDelOptions.defaults(), RecordId.of("1-0"));
        transaction.streamCommands().xAckDel(key, "group", XDelOptions.defaults(), RecordId.of("2-0"));
        List<Object> results = transaction.exec();

        assertThat(results).containsExactly(
                Collections.singletonList(StreamEntryDeletionResult.DELETED),
                Collections.singletonList(StreamEntryDeletionResult.DELETED));
    }

    @Test
    public void testXClaimWithForce() {
        byte[] key = "xclaim-force".getBytes();
        connection.streamCommands().xGroupCreate(key, "group", ReadOffset.from("0-0"), true);
        addRecords(key, XAddOptions.none(), "1-0", "2-0");

        List<ByteRecord> claimed = connection.streamCommands().xClaim(key, "group", "consumer",
                XClaimOptions.minIdle(Duration.ZERO).ids("1-0").force());
        List<RecordId> claimedIds = connection.streamCommands().xClaimJustId(key, "group", "consumer",
                XClaimOptions.minIdle(Duration.ZERO).ids("2-0").force());

        assertThat(claimed).extracting(record -> record.getId().getValue()).containsExactly("1-0");
        assertThat(claimedIds).containsExactly(RecordId.of("2-0"));
        assertThat(connection.streamCommands().xPending(key, "group").getTotalPendingMessages()).isEqualTo(2);
    }

    @Test
    public void testXClaimWithIdleAndRetryCount() {
        byte[] key = "xclaim-idle".getBytes();
        readIntoGroup(key, "1-0");

        connection.streamCommands().xClaim(key, "group", "other",
                XClaimOptions.minIdle(Duration.ZERO).ids("1-0").idle(Duration.ofMinutes(10)).retryCount(7));

        PendingMessage pending = connection.streamCommands().xPending(key, "group", Range.unbounded(), 10L).get(0);
        assertThat(pending.getConsumerName()).isEqualTo("other");
        assertThat(pending.getTotalDeliveryCount()).isEqualTo(7);
        assertThat(pending.getElapsedTimeSinceLastDelivery()).isBetween(Duration.ofMinutes(10), Duration.ofMinutes(11));
    }

    @Test
    public void testXClaimJustIdWithTime() {
        byte[] key = "xclaim-time".getBytes();
        readIntoGroup(key, "1-0");

        List<RecordId> claimedIds = connection.streamCommands().xClaimJustId(key, "group", "other",
                XClaimOptions.minIdle(Duration.ZERO).ids("1-0").time(Instant.now().minus(Duration.ofMinutes(10))));

        assertThat(claimedIds).containsExactly(RecordId.of("1-0"));
        PendingMessage pending = connection.streamCommands().xPending(key, "group", Range.unbounded(), 10L).get(0);
        assertThat(pending.getElapsedTimeSinceLastDelivery()).isBetween(Duration.ofMinutes(9), Duration.ofMinutes(11));
    }

    @Test
    public void testXPendingWithMinIdleTime() {
        byte[] key = "xpending-idle".getBytes();
        readIntoGroup(key, "1-0");

        assertThat(connection.streamCommands().xPending(key, "group", Range.unbounded(), 10L, Duration.ofMinutes(1))).isEmpty();

        connection.streamCommands().xClaim(key, "group", "consumer",
                XClaimOptions.minIdle(Duration.ZERO).ids("1-0").idle(Duration.ofMinutes(10)));

        assertThat(connection.streamCommands().xPending(key, "group", Range.unbounded(), 10L, Duration.ofMinutes(1)))
                .extracting(PendingMessage::getIdAsString).containsExactly("1-0");
    }

    @Test
    public void testXPendingWithoutCount() {
        byte[] key = "xpending-no-count".getBytes();
        String[] ids = new String[12];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = (i + 1) + "-0";
        }
        readIntoGroup(key, ids);

        assertThat(connection.streamCommands().xPending(key, "group", XPendingOptions.unbounded())).hasSize(12);
        assertThat(connection.streamCommands().xPending(key, "group", "consumer")).hasSize(12);
    }

    @Test
    public void testXGroupDelConsumer() {
        byte[] key = "xgroup-delconsumer".getBytes();
        readIntoGroup(key, "1-0");
        assertThat(connection.streamCommands().xGroupDelConsumer(key, Consumer.from("group", "consumer"))).isTrue();

        connection.streamCommands().xReadGroup(Consumer.from("group", "idle"), StreamOffset.create(key, ReadOffset.from("0-0")));
        assertThat(connection.streamCommands().xGroupDelConsumer(key, Consumer.from("group", "idle"))).isTrue();
        assertThat(connection.streamCommands().xInfoConsumers(key, "group").size()).isZero();
    }

    @Test
    public void testXInfoEntries() {
        byte[] key = "xinfo-entries".getBytes();
        addRecords(key, XAddOptions.none(), "1-0", "2-0");

        StreamInfo.XInfoStream info = connection.streamCommands().xInfo(key);
        assertThat(info.streamLength()).isEqualTo(2);
        assertThat(info.firstEntryId()).isEqualTo("1-0");
        assertThat(info.lastEntryId()).isEqualTo("2-0");
        assertThat(info.getFirstEntry()).containsOnlyKeys("1-0");
        assertThat((Map<Object, Object>) info.getLastEntry().get("2-0")).containsEntry("field", "2-0");
    }

    @Test
    public void testXPendingSummaryOverResp3() {
        Config config = createConfig();
        config.setProtocol(Protocol.RESP3);
        RedissonClient client = Redisson.create(config);
        try {
            RedisConnection resp3 = new RedissonConnectionFactory(client).getConnection();
            byte[] key = "xpending-resp3".getBytes();
            resp3.streamCommands().xGroupCreate(key, "group", ReadOffset.from("0-0"), true);

            PendingMessagesSummary empty = resp3.streamCommands().xPending(key, "group");
            assertThat(empty.getTotalPendingMessages()).isZero();
            assertThat(empty.getPendingMessagesPerConsumer()).isEmpty();

            resp3.streamCommands().xAdd(StreamRecords.newRecord().in(key).withId("1-0")
                    .ofBytes(Collections.singletonMap("field".getBytes(), "value".getBytes())));
            resp3.streamCommands().xReadGroup(Consumer.from("group", "consumer"),
                    StreamOffset.create(key, ReadOffset.lastConsumed()));

            PendingMessagesSummary one = resp3.streamCommands().xPending(key, "group");
            assertThat(one.getTotalPendingMessages()).isEqualTo(1);
            assertThat(one.getPendingMessagesPerConsumer()).containsExactly(entry("consumer", 1L));
        } finally {
            client.shutdown();
        }
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
            addRecords(first, XAddOptions.none(), "1-0");
            addRecords(second, XAddOptions.none(), "2-0");

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
        addRecords(key, XAddOptions.none(), "1-0");

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

    @Test
    public void testXInfoGroupsAndConsumersKeepAllFields() {
        byte[] key = "xinfo-fields".getBytes();
        readIntoGroup(key, "1-0", "2-0");

        StreamInfo.XInfoGroup group = connection.streamCommands().xInfoGroups(key).get(0);
        assertThat(group.getRaw()).containsKeys("name", "consumers", "pending", "last-delivered-id", "entries-read", "lag");
        assertThat(group.groupName()).isEqualTo("group");
        assertThat(group.consumerCount()).isEqualTo(1);
        assertThat(group.pendingCount()).isEqualTo(2);
        assertThat(group.lastDeliveredId()).isEqualTo("2-0");

        StreamInfo.XInfoConsumer consumer = connection.streamCommands().xInfoConsumers(key, "group").get(0);
        assertThat(consumer.getRaw()).containsKeys("name", "pending", "idle", "inactive");
        assertThat(consumer.consumerName()).isEqualTo("consumer");
        assertThat(consumer.pendingCount()).isEqualTo(2);
    }

    private void readIntoGroup(byte[] key, String... ids) {
        connection.streamCommands().xGroupCreate(key, "group", ReadOffset.from("0-0"), true);
        addRecords(key, XAddOptions.none(), ids);
        connection.streamCommands().xReadGroup(Consumer.from("group", "consumer"),
                StreamOffset.create(key, ReadOffset.lastConsumed()));
    }

    private static String[] ids(int from, int to) {
        String[] ids = new String[to - from + 1];
        for (int i = from; i <= to; i++) {
            ids[i - from] = i + "-0";
        }
        return ids;
    }

    private void addRecords(byte[] key, XAddOptions options, String... ids) {
        for (String id : ids) {
            ByteRecord record = StreamRecords.newRecord().in(key).withId(id)
                    .ofBytes(Collections.singletonMap("field".getBytes(), id.getBytes()));
            connection.streamCommands().xAdd(record, options);
        }
    }

}
