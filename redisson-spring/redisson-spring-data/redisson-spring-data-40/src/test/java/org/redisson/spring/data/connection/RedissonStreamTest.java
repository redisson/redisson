package org.redisson.spring.data.connection;

import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.protocol.CommandData;
import org.redisson.connection.ClientConnectionsEntry;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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

}
