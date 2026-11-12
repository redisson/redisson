package org.redisson.spring.data.connection;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.redisson.api.RTopic;
import org.redisson.api.redisnode.RedisCluster;
import org.redisson.api.redisnode.RedisClusterMaster;
import org.redisson.api.redisnode.RedisClusterSlave;
import org.redisson.api.redisnode.RedisNodes;
import org.redisson.client.codec.StringCodec;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class RedissonSubscribeTest extends BaseConnectionTest {

//    @Test
    public void testContainersSameName() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            testContainerSameName();
        }
    }

    @Test
    public void testContainerListeners() {
        RedissonConnectionFactory f = new RedissonConnectionFactory(redisson);
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(f);
        container.afterPropertiesSet();
        container.start();

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> runConcurrentSubscribeUnsubscribeTest(container));
    }

    private void runConcurrentSubscribeUnsubscribeTest(RedisMessageListenerContainer container) {
        MessageListener listenerA = (a, b) -> {};
        MessageListener listenerB = (a, b) -> {};

        ChannelTopic topicA = new ChannelTopic("topic1");
        ChannelTopic topicB = new ChannelTopic("topic2");

        for (int i = 0; i < 100; i++) {
            container.addMessageListener(listenerB, topicB);
            container.removeMessageListener(listenerA);
            container.addMessageListener(listenerA, topicA);
            container.removeMessageListener(listenerB);
        }
    }

//    @Test
    public void testContainerSameName() throws InterruptedException {
        RedissonConnectionFactory f = new RedissonConnectionFactory(redisson);
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(f);
        container.afterPropertiesSet();
        container.start();

        Queue<String> msgs = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 2; i++) {
            container.addMessageListener((message, pattern) -> msgs.add(new String(message.getBody())), ChannelTopic.of("test"));
        }

        StringRedisTemplate rt = new StringRedisTemplate(f);
        rt.convertAndSend("test", "1");

        Thread.sleep(100);

        assertThat(msgs).contains("1", "1").hasSize(2);

        container.stop();

        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(f);
        container.afterPropertiesSet();
        container.start();
        for (int i = 0; i < 2; i++) {
            container.addMessageListener((message, pattern) -> {
            }, PatternTopic.of("*"));
        }
        container.stop();
    }

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
            container.addMessageListener((message, pattern) -> {
            }, ChannelTopic.of("test"+i));
        }
        container.stop();

        container= new RedisMessageListenerContainer();
        container.setConnectionFactory(f);
        container.afterPropertiesSet();
        container.start();
        for (int i = 0; i < 2; i++) {
            container.addMessageListener((message, pattern) -> {
            }, PatternTopic.of("*" + i));
        }
        container.stop();
    }

    @Test
    public void testCluster() {
        testInClusterFactory(factory -> {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(factory);

            RedisCluster nodes = redissonCluster.getRedisNodes(RedisNodes.CLUSTER);
            for (RedisClusterMaster master : nodes.getMasters()) {
                master.setConfig("notify-keyspace-events", "KgEx$");
            }
            for (RedisClusterSlave slave : nodes.getSlaves()) {
                slave.setConfig("notify-keyspace-events", "KgEx$");
            }

            Queue<String> names = new ConcurrentLinkedQueue<>();
            container.addMessageListener((message, pattern) -> names.add(new String(message.getBody())), new PatternTopic("__keyevent@0__:expired"));
            container.afterPropertiesSet();
            container.start();

            factory.getConnection().setEx("EG:test:key1".getBytes(), 3, "123".getBytes());
            factory.getConnection().setEx("test:key2".getBytes(), 3, "123".getBytes());
            factory.getConnection().setEx("test:key1".getBytes(), 3, "123".getBytes());

            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(names).containsExactlyInAnyOrder("EG:test:key1", "test:key2", "test:key1");
            });
        });
    }

    @Test
    public void testListenersDuplication() {
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

        RedisConnection c = factory.getConnection();
        c.publish("a".getBytes(), "msg".getBytes());

        Awaitility.await().atMost(Durations.ONE_SECOND)
                .untilAsserted(() -> {
                    assertThat(msg).containsExactly("msg".getBytes());
                });
    }

    @Test
    public void testPatterTopic() throws IOException, InterruptedException {
        testWithParams(redisson -> {
            RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);

            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(factory);
            AtomicInteger counterTest = new AtomicInteger();
            container.addMessageListener((message, pattern) -> counterTest.incrementAndGet(), new PatternTopic("__keyspace@0__:mykey"));
            container.addMessageListener((message, pattern) -> counterTest.incrementAndGet(), new PatternTopic("__keyevent@0__:del"));
            container.afterPropertiesSet();
            container.start();
            assertThat(container.isRunning()).isTrue();

            RedisConnection c = factory.getConnection();
            c.set("mykey".getBytes(), "2".getBytes());
            c.del("mykey".getBytes());

            Awaitility.await().atMost(Durations.FIVE_SECONDS).until(() -> {
                return counterTest.get() == 3;
            });
        }, NOTIFY_KEYSPACE_EVENTS, "KgE$");
    }

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
            assertThat(second).containsExactly("after");
            assertThat(topicMessages).containsExactly("after");
        });
        assertThat(first).isEmpty();
        assertThat(b.isSubscribed()).isTrue();

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
            assertThat(second).containsExactly("after");
        });
        assertThat(first).isEmpty();

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
            assertThat(first).contains("before");
            assertThat(second).contains("before");
        });

        a.stop();
        second.clear();
        c.publish("container".getBytes(), "after".getBytes());

        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            assertThat(second).containsExactly("after");
        });
        b.stop();
    }

    @Test
    public void testCloseEndsSubscription() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        Queue<String> received = new ConcurrentLinkedQueue<>();
        RedisConnection connection = factory.getConnection();
        connection.subscribe((message, pattern) -> received.add(new String(message.getBody())), "closing".getBytes());
        connection.getSubscription().pSubscribe("closing*".getBytes());

        connection.close();

        assertThat(connection.isSubscribed()).isFalse();
        assertThat(connection.getSubscription()).isNull();
        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            assertThat(redisson.getTopic("closing").countSubscribers()).isZero();
        });
        factory.getConnection().publish("closing".getBytes(), "after".getBytes());
        Awaitility.await().pollDelay(Durations.ONE_HUNDRED_MILLISECONDS).atMost(Durations.ONE_SECOND).untilAsserted(() -> {
            assertThat(received).isEmpty();
        });
    }

    @Test
    public void testSubscribe() {
        RedissonConnection connection = new RedissonConnection(redisson);
        AtomicReference<byte[]> msg = new AtomicReference<byte[]>();
        connection.subscribe((message, pattern) -> msg.set(message.getBody()), "test".getBytes());
        
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
