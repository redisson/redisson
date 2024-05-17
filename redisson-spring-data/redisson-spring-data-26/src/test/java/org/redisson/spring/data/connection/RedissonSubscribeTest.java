package org.redisson.spring.data.connection;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.Test;
import org.redisson.RedisRunner;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonSubscribeTest extends BaseConnectionTest {

    @Test
    public void testContainer() {
        RedissonConnectionFactory f = new RedissonConnectionFactory(redisson);
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(f);
        container.afterPropertiesSet();
        container.start();

//        for (int i = 0; i < 2; i++) {
//            container.addMessageListener(new MessageListener() {
//                @Override
//                public void onMessage(Message message, byte[] pattern) {
//                }
//            }, ChannelTopic.of("test"));
//        }
//
//        container.stop();
//
//        container = new RedisMessageListenerContainer();
//        container.setConnectionFactory(f);
//        container.afterPropertiesSet();
//        container.start();
//        for (int i = 0; i < 2; i++) {
//            container.addMessageListener(new MessageListener() {
//                @Override
//                public void onMessage(Message message, byte[] pattern) {
//                }
//            }, PatternTopic.of("*"));
//        }
//        container.stop();
//
//        container= new RedisMessageListenerContainer();
//        container.setConnectionFactory(f);
//        container.afterPropertiesSet();
//        container.start();
        for (int i = 0; i < 2; i++) {
            container.addMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message message, byte[] pattern) {
                }
            }, ChannelTopic.of("test"+i));
        }
        container.stop();

        container= new RedisMessageListenerContainer();
        container.setConnectionFactory(f);
        container.afterPropertiesSet();
        container.start();
        for (int i = 0; i < 2; i++) {
            container.addMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message message, byte[] pattern) {
                }
            }, PatternTopic.of("*" + i));
        }
        container.stop();
    }

    @Test
    public void testListenersDuplication() throws InterruptedException {
        Queue<byte[]> msg = new ConcurrentLinkedQueue<>();
        MessageListener aListener = (message, pattern) -> {
            msg.add(message.getBody());
        };

        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(aListener,
                Arrays.asList(new ChannelTopic("a"), new ChannelTopic("b")));
        container.addMessageListener(aListener,
                Arrays.asList(new PatternTopic("c*")));
        container.afterPropertiesSet();
        container.start();

        Thread.sleep(200);

        RedisConnection c = factory.getConnection();
        c.publish("a".getBytes(), "msg".getBytes());

        Awaitility.await().atMost(Durations.ONE_SECOND)
                .untilAsserted(() -> {
                    assertThat(msg).containsExactly("msg".getBytes());
                });
    }

    @Test
    public void testPatterTopic() throws IOException, InterruptedException {
        RedisRunner.RedisProcess instance = new RedisRunner()
                .nosave()
                .randomPort()
                .randomDir()
                .notifyKeyspaceEvents(
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.K,
                                    RedisRunner.KEYSPACE_EVENTS_OPTIONS.g,
                                    RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                                    RedisRunner.KEYSPACE_EVENTS_OPTIONS.$)
                .run();

        Config config = new Config();
        config.useSingleServer().setAddress(instance.getRedisServerAddressAndPort()).setPingConnectionInterval(0);
        RedissonClient redisson = Redisson.create(config);

        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        AtomicInteger counterTest = new AtomicInteger();
        container.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                counterTest.incrementAndGet();
            }
        }, new PatternTopic("__keyspace@0__:mykey"));
        container.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                counterTest.incrementAndGet();
            }
        }, new PatternTopic("__keyevent@0__:del"));
        container.afterPropertiesSet();
        container.start();
        Assertions.assertThat(container.isRunning()).isTrue();

        RedisConnection c = factory.getConnection();
        c.set("mykey".getBytes(), "2".getBytes());
        c.del("mykey".getBytes());

        Awaitility.await().atMost(Durations.FIVE_SECONDS).until(() -> {
            return counterTest.get() == 3;
        });

        container.stop();
        redisson.shutdown();
    }

    @Test
    public void testSubscribe() {
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
