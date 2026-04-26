package org.redisson.spring.data.connection;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.redisson.api.redisnode.RedisCluster;
import org.redisson.api.redisnode.RedisClusterMaster;
import org.redisson.api.redisnode.RedisClusterSlave;
import org.redisson.api.redisnode.RedisNodes;
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
