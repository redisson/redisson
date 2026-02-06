package org.redisson;

import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RPatternTopic;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.BasePatternStatusListener;
import org.redisson.api.listener.PatternMessageListener;
import org.redisson.api.listener.PatternStatusListener;
import org.redisson.api.redisnode.*;
import org.redisson.api.redisnode.RedisNodes;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.redisson.config.SubscriptionMode;
import org.redisson.connection.balancer.RandomLoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.GenericContainer;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class RedissonTopicPatternTest extends RedisDockerTest {

    private static final Logger log = LoggerFactory.getLogger(RedissonTopicPatternTest.class);

    public static class Message implements Serializable {

        private String name;

        public Message() {
        }

        public Message(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Message other = (Message) obj;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "Message{" + "name='" + name + '\'' + '}';
        }
    }

    @Test
    public void testCluster() {
        testInCluster(redisson -> {
            RedisCluster nodes = redisson.getRedisNodes(RedisNodes.CLUSTER);
            for (RedisClusterSlave slave : nodes.getSlaves()) {
                slave.setConfig("notify-keyspace-events", "Eg");
            }
            for (RedisClusterMaster master : nodes.getMasters()) {
                master.setConfig("notify-keyspace-events", "Eg");
            }

            AtomicInteger subscribeCounter = new AtomicInteger();
            RPatternTopic topic = redisson.getPatternTopic("__keyevent@*", StringCodec.INSTANCE);
            topic.addListener(new PatternStatusListener() {
                @Override
                public void onPSubscribe(String pattern) {
                    subscribeCounter.incrementAndGet();
                }

                @Override
                public void onPUnsubscribe(String pattern) {
                    System.out.println("onPUnsubscribe: " + pattern);
                }
            });

            AtomicInteger counter = new AtomicInteger();

            PatternMessageListener<String> listener = (pattern, channel, msg) -> {
                counter.incrementAndGet();
            };
            topic.addListener(String.class, listener);

            for (int i = 0; i < 10; i++) {
                redisson.getBucket("" + i).set(i);
                redisson.getBucket("" + i).delete();
                try {
                    Thread.sleep(7);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> counter.get() > 9);
            assertThat(subscribeCounter.get()).isEqualTo(1);

            for (RedisClusterSlave slave : nodes.getSlaves()) {
                slave.setConfig("notify-keyspace-events", "");
            }
            for (RedisClusterMaster master : nodes.getMasters()) {
                master.setConfig("notify-keyspace-events", "");
            }

            topic.removeAllListeners();
        });
    }

    @Test
    public void testNonEventMessagesInCluster() {
        testInCluster(redisson -> {
            AtomicInteger subscribeCounter = new AtomicInteger();
            RPatternTopic topic = redisson.getPatternTopic("my*", StringCodec.INSTANCE);
            topic.addListener(new PatternStatusListener() {
                @Override
                public void onPSubscribe(String pattern) {
                    subscribeCounter.incrementAndGet();
                }

                @Override
                public void onPUnsubscribe(String pattern) {
                    System.out.println("onPUnsubscribe: " + pattern);
                }
            });

            AtomicInteger counter = new AtomicInteger();

            PatternMessageListener<String> listener = (pattern, channel, msg) -> {
                counter.incrementAndGet();
            };
            topic.addListener(String.class, listener);

            for (int i = 0; i < 100; i++) {
                redisson.getTopic("my" + i).publish(123);
            }

            Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> counter.get() == 100);
            assertThat(subscribeCounter.get()).isEqualTo(1);

            topic.removeAllListeners();
        });
    }

    @Test
    public void testMultiType() throws InterruptedException {
        RPatternTopic topic1 = redisson.getPatternTopic("topic1.*");
        AtomicInteger str = new AtomicInteger(); 
        topic1.addListener(String.class, (pattern, channel, msg) -> {
            str.incrementAndGet();
        });
        AtomicInteger i = new AtomicInteger();
        topic1.addListener(Integer.class, (pattern, channel, msg) -> {
            i.incrementAndGet();
        });
        
        redisson.getTopic("topic1.str").publish("123");
        redisson.getTopic("topic1.int").publish(123);
        
        Thread.sleep(500);
        
        Assertions.assertEquals(1, i.get());
        Assertions.assertEquals(1, str.get());
        topic1.removeAllListeners();
    }

    @Test
    public void testUnsubscribe() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1);

        RPatternTopic topic1 = redisson.getPatternTopic("topic1.*");
        int listenerId = topic1.addListener(Message.class, (pattern, channel, msg) -> {
            Assertions.fail();
        });
        topic1.addListener(Message.class, (pattern, channel, msg) -> {
            Assertions.assertTrue(pattern.equals("topic1.*"));
            Assertions.assertTrue(channel.equals("topic1.t3"));
            Assertions.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });
        topic1.removeListener(listenerId);

        redisson.getTopic("topic1.t3").publish(new Message("123"));

        Assertions.assertTrue(messageRecieved.await(5, TimeUnit.SECONDS));
        topic1.removeAllListeners();
    }

    @Test
    public void testLazyUnsubscribe() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1);

        RPatternTopic topic1 = redisson.getPatternTopic("topic.*");
        int listenerId = topic1.addListener(Message.class, (pattern, channel, msg) -> {
            Assertions.fail();
        });

        Thread.sleep(1000);
        topic1.removeListener(listenerId);
        Thread.sleep(1000);

        RedissonClient redisson2 = createInstance();
        RPatternTopic topic2 = redisson2.getPatternTopic("topic.*");
        topic2.addListener(Message.class, (pattern, channel, msg) -> {
            Assertions.assertTrue(pattern.equals("topic.*"));
            Assertions.assertTrue(channel.equals("topic.t1"));
            Assertions.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });

        RTopic topic3 = redisson2.getTopic("topic.t1");
        topic3.publish(new Message("123"));

        Assertions.assertTrue(messageRecieved.await(5, TimeUnit.SECONDS));

        topic1.removeAllListeners();
        redisson2.shutdown();
    }

    @Test
    public void test() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(5);

        final CountDownLatch statusRecieved = new CountDownLatch(1);
        RPatternTopic topic1 = redisson.getPatternTopic("topic.*");
        topic1.addListener(new BasePatternStatusListener() {
            @Override
            public void onPSubscribe(String pattern) {
                Assertions.assertEquals("topic.*", pattern);
                statusRecieved.countDown();
            }
        });
        topic1.addListener(Message.class, (pattern, channel, msg) -> {
            Assertions.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });

        RedissonClient redisson2 = createInstance();
        RTopic topic2 = redisson2.getTopic("topic.t1");
        topic2.addListener(Message.class, (channel, msg) -> {
            Assertions.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });
        topic2.publish(new Message("123"));
        topic2.publish(new Message("123"));

        RTopic topicz = redisson2.getTopic("topicz.t1");
        topicz.publish(new Message("789")); // this message doesn't get
                                            // delivered, and would fail the
                                            // assertion

        RTopic topict2 = redisson2.getTopic("topic.t2");
        topict2.publish(new Message("123"));

        statusRecieved.await();
        Assertions.assertTrue(messageRecieved.await(5, TimeUnit.SECONDS));

        topic1.removeAllListeners();
        redisson2.shutdown();
    }

    @Test
    public void testListenerRemove() {
        RPatternTopic topic1 = redisson.getPatternTopic("topic.*");
        final CountDownLatch l = new CountDownLatch(1);
        topic1.addListener(new BasePatternStatusListener() {
            @Override
            public void onPUnsubscribe(String pattern) {
                Assertions.assertEquals("topic.*", pattern);
                l.countDown();
            }
        });
        int id = topic1.addListener(Message.class, (pattern, channel, msg) -> {
            Assertions.fail();
        });

        RedissonClient redisson2 = createInstance();
        RTopic topic2 = redisson2.getTopic("topic.t1");
        topic1.removeListener(id);
        topic2.publish(new Message("123"));

        topic1.removeAllListeners();
        redisson2.shutdown();
    }

    @Test
    public void testConcurrentTopic() throws Exception {
        int threads = 16;
        int loops = 25000;
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>(); 
        for (int i = 0; i < threads; i++) {

            Runnable worker = new Runnable() {

                @Override
                public void run() {
                    for (int j = 0; j < loops; j++) {
                        RPatternTopic t = redisson.getPatternTopic("PUBSUB*");
                        int listenerId = t.addListener(new PatternStatusListener() {
                            @Override
                            public void onPUnsubscribe(String channel) {
                            }
                            
                            @Override
                            public void onPSubscribe(String channel) {
                            }
                        });
                        redisson.getTopic("PUBSUB_" + j).publish("message");
                        t.removeListener(listenerId);
                    }
                }
            };
            Future<?> s = executor.submit(worker);
            futures.add(s);
        }
        executor.shutdown();
        Assertions.assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));

        for (Future<?> future : futures) {
            future.get();
        }

        RPatternTopic t = redisson.getPatternTopic("PUBSUB*");
        t.removeAllListeners();
    }

    @Test
    public void testReattachInClusterSlave() {
        testReattachInCluster(SubscriptionMode.SLAVE);
    }

    @Test
    public void testReattachInClusterMaster() {
        testReattachInCluster(SubscriptionMode.MASTER);
    }

    private void testReattachInCluster(SubscriptionMode subscriptionMode) {
        withNewCluster((nds, instance) -> {
            Config config = instance.getConfig();
            config.useClusterServers()
                    .setSubscriptionMode(subscriptionMode);
            RedissonClient redisson = Redisson.create(config);

            RedisCluster nodes = redisson.getRedisNodes(RedisNodes.CLUSTER);
            for (RedisClusterSlave slave : nodes.getSlaves()) {
                slave.setConfig("notify-keyspace-events", "KgE$");
            }
            for (RedisClusterMaster master : nodes.getMasters()) {
                master.setConfig("notify-keyspace-events", "KgE$");
            }

            AtomicInteger executions = new AtomicInteger();

            RPatternTopic topic = redisson.getPatternTopic("__keyevent@*:del", StringCodec.INSTANCE);
            topic.addListener(String.class, new PatternMessageListener<String>() {
                @Override
                public void onMessage(CharSequence pattern, CharSequence channel, String msg) {
                    executions.incrementAndGet();
                }
            });

            List<ContainerState> masters = getMasterNodes(nds);
            stop(masters.get(0));

            try {
                Thread.sleep(40000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            nodes = redisson.getRedisNodes(RedisNodes.CLUSTER);
            for (RedisClusterSlave slave : nodes.getSlaves()) {
                slave.setConfig("notify-keyspace-events", "KgE$");
            }
            for (RedisClusterMaster master : nodes.getMasters()) {
                master.setConfig("notify-keyspace-events", "KgE$");
            }

            for (int i = 0; i < 100; i++) {
                RBucket<Object> b = redisson.getBucket("test" + i);
                b.set(i);
                b.delete();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertThat(executions.get()).isEqualTo(100);

            redisson.shutdown();
        });


    }

    @Test
    public void testReattach() throws InterruptedException {
        GenericContainer<?> redis = createRedis();
        redis.start();

        Config config = createConfig(redis);
        RedissonClient redisson = Redisson.create(config);
        
        final AtomicBoolean executed = new AtomicBoolean();
        
        RPatternTopic topic = redisson.getPatternTopic("topic*");
        topic.addListener(Integer.class, new PatternMessageListener<Integer>() {
            @Override
            public void onMessage(CharSequence pattern, CharSequence channel, Integer msg) {
                if (msg == 1) {
                    executed.set(true);
                }
            }
        });

        restart(redis);

        Thread.sleep(1000);

        redisson.getTopic("topic1").publish(1);
        
        await().atMost(5, TimeUnit.SECONDS).untilTrue(executed);
        
        redisson.shutdown();
        redis.stop();
    }

    @Test
    public void testResubscribeInSentinel() throws InterruptedException {
        withSentinel((nodes, config) -> {
            config.useSentinelServers()
                    .setRetryAttempts(8)
                    .setSubscriptionsPerConnection(20)
                    .setSubscriptionConnectionPoolSize(200);

            RedissonClient redisson = Redisson.create(config);

            RedisSentinelMasterSlave runningNodes = redisson.getRedisNodes(RedisNodes.SENTINEL_MASTER_SLAVE);
            for (RedisSlave slave : runningNodes.getSlaves()) {
                RedisConnection conn = ((org.redisson.redisnode.RedisNode)slave).getClient().connect();
                conn.sync(RedisCommands.CONFIG_SET,"notify-keyspace-events", "KgE$");
            }
            runningNodes.getMaster().setConfig("notify-keyspace-events", "KgE$");

            AtomicInteger counter = new AtomicInteger(0);

            RPatternTopic topic = redisson.getPatternTopic("__keyspace@0__:test.bucket.*", StringCodec.INSTANCE);
            topic.addListener(String.class,
                    (pattern, channel, msg) -> counter.incrementAndGet());

            // ensure everything works before failover
            for (int i=0; i<10; i++) {
                redisson.getBucket(String.format("test.bucket.%d", i+1)).set("value");
            }

            await().atMost(5, TimeUnit.SECONDS).until(() -> counter.get() == 10);

            nodes.getFirst().stop(); // withSentinel() puts the initial master first

            await().atMost(1, TimeUnit.MINUTES).until(() -> {
                // wait for sentinel to do its thing
                try {
                    redisson.getBucket("other.bucket").set("value");
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });

            runningNodes.getMaster().setConfig("notify-keyspace-events", "KgE$");
            log.info("notify-keyspace-events @ master = {}",
                redisson.getRedisNodes(RedisNodes.SENTINEL_MASTER_SLAVE).getMaster().getConfig("notify-keyspace-events"));

            for (int i=0; i<10; i++) {
                redisson.getBucket(String.format("test.bucket.%d", i+1)).set("value");
            }

            await().atMost(5, TimeUnit.SECONDS).until(() -> counter.get() == 20);
        }, 2);
    }
}
