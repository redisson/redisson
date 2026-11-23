package org.redisson.spring.data.connection;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.redisson.api.RTopic;
import org.redisson.client.codec.StringCodec;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

public class RedissonSubscribeTest extends BaseConnectionTest {

    @Test
    public void testUnsubscribeKeepsOtherSubscriptions() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        Queue<String> topicMessages = new ConcurrentLinkedQueue<>();
        RTopic topic = redisson.getTopic("shared", StringCodec.INSTANCE);
        int listenerId = topic.addListener(String.class, (channel, msg) -> topicMessages.add(msg));

        Queue<String> first = new ConcurrentLinkedQueue<>();
        Queue<String> second = new ConcurrentLinkedQueue<>();
        RedisConnection a = factory.getConnection();
        RedisConnection b = factory.getConnection();
        a.subscribe((message, pattern) -> first.add(new String(message.getBody())), "shared".getBytes());
        b.subscribe((message, pattern) -> second.add(new String(message.getBody())), "shared".getBytes());

        a.getSubscription().unsubscribe();
        factory.getConnection().publish("shared".getBytes(), "after".getBytes());

        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            Assertions.assertThat(second).containsExactly("after");
            Assertions.assertThat(topicMessages).containsExactly("after");
        });
        Assertions.assertThat(first).isEmpty();
        Assertions.assertThat(b.isSubscribed()).isTrue();

        b.getSubscription().unsubscribe();
        topic.removeListener(listenerId);
    }

    @Test
    public void testPUnsubscribeKeepsOtherSubscriptions() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        Queue<String> first = new ConcurrentLinkedQueue<>();
        Queue<String> second = new ConcurrentLinkedQueue<>();
        RedisConnection a = factory.getConnection();
        RedisConnection b = factory.getConnection();
        a.pSubscribe((message, pattern) -> first.add(new String(message.getBody())), "shared*".getBytes());
        b.pSubscribe((message, pattern) -> second.add(new String(message.getBody())), "shared*".getBytes());

        a.getSubscription().pUnsubscribe();
        factory.getConnection().publish("shared1".getBytes(), "after".getBytes());

        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            Assertions.assertThat(second).containsExactly("after");
        });
        Assertions.assertThat(first).isEmpty();

        b.getSubscription().pUnsubscribe();
    }

    @Test
    public void testStoppedContainerKeepsOtherContainers() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        Queue<String> first = new ConcurrentLinkedQueue<>();
        Queue<String> second = new ConcurrentLinkedQueue<>();
        RedisMessageListenerContainer a = new RedisMessageListenerContainer();
        a.setConnectionFactory(factory);
        a.addMessageListener((message, pattern) -> first.add(new String(message.getBody())), new ChannelTopic("container"));
        a.afterPropertiesSet();
        a.start();
        RedisMessageListenerContainer b = new RedisMessageListenerContainer();
        b.setConnectionFactory(factory);
        b.addMessageListener((message, pattern) -> second.add(new String(message.getBody())), new ChannelTopic("container"));
        b.afterPropertiesSet();
        b.start();

        RedisConnection c = factory.getConnection();
        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            c.publish("container".getBytes(), "before".getBytes());
            Assertions.assertThat(first).contains("before");
            Assertions.assertThat(second).contains("before");
        });

        a.stop();

        Awaitility.await().pollDelay(Durations.ONE_SECOND).atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            c.publish("container".getBytes(), "after".getBytes());
            Assertions.assertThat(second).contains("after");
        });
        b.stop();
    }

    @Test
    public void testListenerAddedToRunningContainerGetsEachMessageOnce() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        Queue<String> received = new ConcurrentLinkedQueue<>();
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) -> received.add("channel first " + new String(message.getBody())), new ChannelTopic("again"));
        container.addMessageListener((message, pattern) -> received.add("pattern first " + new String(message.getBody())), new PatternTopic("again-*"));
        container.afterPropertiesSet();
        container.start();

        RedisConnection c = factory.getConnection();
        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            c.publish("again".getBytes(), "before".getBytes());
            c.publish("again-1".getBytes(), "before".getBytes());
            Assertions.assertThat(received).contains("channel first before", "pattern first before");
        });

        container.addMessageListener((message, pattern) -> received.add("channel second " + new String(message.getBody())), new ChannelTopic("again"));
        container.addMessageListener((message, pattern) -> received.add("pattern second " + new String(message.getBody())), new PatternTopic("again-*"));
        c.publish("again".getBytes(), "after".getBytes());
        c.publish("again-1".getBytes(), "after".getBytes());

        Awaitility.await().pollDelay(Durations.ONE_SECOND).atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            Assertions.assertThat(received.stream().filter(m -> m.endsWith(" after")))
                    .containsExactlyInAnyOrder("channel first after", "channel second after",
                            "pattern first after", "pattern second after");
        });
        container.stop();
    }

    @Test
    public void testMultipleSubscribers() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        AtomicInteger counterTest = new AtomicInteger();
        AtomicInteger counterTest2 = new AtomicInteger();
        container.addMessageListener((message, pattern) -> counterTest.incrementAndGet(), new ChannelTopic("test"));
        container.addMessageListener((message, pattern) -> counterTest.incrementAndGet(), new ChannelTopic("test"));
        container.addMessageListener((message, pattern) -> counterTest2.incrementAndGet(), new ChannelTopic("test2"));
        container.afterPropertiesSet();
        container.start();
        Assertions.assertThat(container.isRunning()).isTrue();

        RedisConnection c = factory.getConnection();
        c.publish("test".getBytes(), "sdfdsf".getBytes());

        Awaitility.await().atMost(Durations.FIVE_SECONDS).until(() -> {
            return counterTest.get() == 2;
        });
        Assertions.assertThat(counterTest2.get()).isZero();

        container.stop();
    }

    @Test
    public void testCloseEndsSubscription() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        Queue<String> received = new ConcurrentLinkedQueue<>();
        RedisConnection connection = factory.getConnection();
        connection.subscribe((message, pattern) -> received.add(new String(message.getBody())), "closing".getBytes());
        connection.getSubscription().pSubscribe("closing*".getBytes());

        connection.close();

        Assertions.assertThat(connection.isSubscribed()).isFalse();
        Assertions.assertThat(connection.getSubscription()).isNull();
        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            Assertions.assertThat(redisson.getTopic("closing").countSubscribers()).isZero();
        });
        factory.getConnection().publish("closing".getBytes(), "after".getBytes());
        Awaitility.await().pollDelay(Durations.ONE_HUNDRED_MILLISECONDS).atMost(Durations.ONE_SECOND).untilAsserted(() -> {
            Assertions.assertThat(received).isEmpty();
        });
    }

    @Test
    public void testSubscribe() {
        RedissonConnection connection = new RedissonConnection(redisson);
        AtomicReference<byte[]> msg = new AtomicReference<byte[]>();
        connection.subscribe((message, pattern) -> {
            System.out.println("message " + new String(message.getBody()));
            msg.set(message.getBody());
        }, "test".getBytes());
        
        connection.publish("test".getBytes(), "msg".getBytes());
        Awaitility.await().atMost(Durations.ONE_SECOND)
                    .until(() -> Arrays.equals("msg".getBytes(), msg.get()));
        
        connection.getSubscription().unsubscribe();
        
        connection.publish("test".getBytes(), "msg".getBytes());
    }
    
    @Test
    public void testUnSubscribe() {
        RedissonConnection connection = new RedissonConnection(redisson);
        AtomicReference<byte[]> msg = new AtomicReference<byte[]>();
        connection.subscribe((message, pattern) -> msg.set(message.getBody()), "test".getBytes());
        
        connection.publish("test".getBytes(), "msg".getBytes());
        Awaitility.await().atMost(Durations.ONE_SECOND)
                    .until(() -> Arrays.equals("msg".getBytes(), msg.get()));
        
        connection.getSubscription().unsubscribe();
        
        
    }

}
