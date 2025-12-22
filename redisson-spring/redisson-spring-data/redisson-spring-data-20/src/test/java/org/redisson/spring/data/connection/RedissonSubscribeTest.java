package org.redisson.spring.data.connection;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

public class RedissonSubscribeTest extends BaseConnectionTest {

    @Test
    public void testMultipleSubscribers() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        AtomicInteger counterTest = new AtomicInteger();
        AtomicInteger counterTest2 = new AtomicInteger();
        container.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                counterTest.incrementAndGet();
            }
        }, new ChannelTopic("test"));
        container.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                counterTest.incrementAndGet();
            }
        }, new ChannelTopic("test"));
        container.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                counterTest2.incrementAndGet();
            }
        }, new ChannelTopic("test2"));
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
    public void testSubscribe() {
        RedissonConnection connection = new RedissonConnection(redisson);
        AtomicReference<byte[]> msg = new AtomicReference<byte[]>();
        connection.subscribe(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                System.out.println("message " + new String(message.getBody()));
                msg.set(message.getBody());
            }
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
        connection.subscribe(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                msg.set(message.getBody());
            }
        }, "test".getBytes());
        
        connection.publish("test".getBytes(), "msg".getBytes());
        Awaitility.await().atMost(Durations.ONE_SECOND)
                    .until(() -> Arrays.equals("msg".getBytes(), msg.get()));
        
        connection.getSubscription().unsubscribe();
        
        
    }

}
