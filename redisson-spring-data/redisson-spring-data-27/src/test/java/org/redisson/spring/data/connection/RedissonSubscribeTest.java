package org.redisson.spring.data.connection;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.junit.Test;
import org.redisson.ClusterRunner;
import org.redisson.RedisRunner;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.connection.balancer.RandomLoadBalancer;
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
    public void testCluster() throws IOException, InterruptedException {
        RedisRunner master1 = new RedisRunner().port(6890).randomDir().nosave()                .notifyKeyspaceEvents(
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.K,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.g,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.x,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.$)
                ;
        RedisRunner master2 = new RedisRunner().port(6891).randomDir().nosave()                .notifyKeyspaceEvents(
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.K,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.g,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.x,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.$)
                ;
        RedisRunner master3 = new RedisRunner().port(6892).randomDir().nosave()                .notifyKeyspaceEvents(
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.K,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.g,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.x,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.$)
                ;
        RedisRunner slave1 = new RedisRunner().port(6900).randomDir().nosave()                .notifyKeyspaceEvents(
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.K,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.g,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.x,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.$)
                ;
        RedisRunner slave2 = new RedisRunner().port(6901).randomDir().nosave()                .notifyKeyspaceEvents(
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.K,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.g,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.x,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.$)
                ;
        RedisRunner slave3 = new RedisRunner().port(6902).randomDir().nosave()                .notifyKeyspaceEvents(
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.K,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.g,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.x,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.$)
                ;

        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterRunner.ClusterProcesses process = clusterRunner.run();

        Thread.sleep(5000);

        Config config = new Config();
        config.useClusterServers()
                .setPingConnectionInterval(0)
                .setLoadBalancer(new RandomLoadBalancer())
                .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        Queue<String> names = new ConcurrentLinkedQueue<>();
        container.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                names.add(new String(message.getBody()));
            }
        }, new PatternTopic("__keyevent@0__:expired"));
        container.afterPropertiesSet();
        container.start();

        factory.getConnection().setEx("EG:test:key1".getBytes(), 3, "123".getBytes());
        factory.getConnection().setEx("test:key2".getBytes(), 3, "123".getBytes());
        factory.getConnection().setEx("test:key1".getBytes(), 3, "123".getBytes());

        Awaitility.await().atMost(Duration.FIVE_SECONDS).untilAsserted(() -> {
            assertThat(names).containsExactlyInAnyOrder("EG:test:key1", "test:key2", "test:key1");
        });

        redisson.shutdown();
        process.shutdown();
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

        Awaitility.await().atMost(Duration.ONE_SECOND)
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
        assertThat(container.isRunning()).isTrue();

        RedisConnection c = factory.getConnection();
        c.set("mykey".getBytes(), "2".getBytes());
        c.del("mykey".getBytes());

        Awaitility.await().atMost(Duration.FIVE_SECONDS).until(() -> {
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
        Awaitility.await().atMost(Duration.ONE_SECOND)
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
        Awaitility.await().atMost(Duration.ONE_SECOND)
                    .until(() -> Arrays.equals("msg".getBytes(), msg.get()));
        
        connection.getSubscription().unsubscribe();
        
        
    }

}
