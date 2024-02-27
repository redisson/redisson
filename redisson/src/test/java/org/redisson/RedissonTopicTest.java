package org.redisson;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.ClusterRunner.ClusterProcesses;
import org.redisson.RedisRunner.KEYSPACE_EVENTS_OPTIONS;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.api.*;
import org.redisson.api.listener.*;
import org.redisson.api.redisnode.RedisCluster;
import org.redisson.api.redisnode.RedisClusterMaster;
import org.redisson.api.redisnode.RedisClusterSlave;
import org.redisson.api.redisnode.RedisNodes;
import org.redisson.client.*;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.cluster.ClusterNodeInfo;
import org.redisson.config.Config;
import org.redisson.config.SubscriptionMode;
import org.redisson.connection.balancer.RandomLoadBalancer;
import org.redisson.misc.RedisURI;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class RedissonTopicTest extends RedisDockerTest {

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

    }

    @Test
    public void testCluster() throws IOException, InterruptedException {
        GenericContainer redisCluster = new GenericContainer<>("vishnunair/docker-redis-cluster")
                .withExposedPorts(6379, 6380, 6381, 6382, 6383, 6384)
                .withStartupCheckStrategy(new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(10)));
        redisCluster.start();

        Config config = new Config();
        config.setProtocol(protocol);
        config.useClusterServers()
                .setNatMapper(new NatMapper() {
                    @Override
                    public RedisURI map(RedisURI uri) {
                        if (redisCluster.getMappedPort(uri.getPort()) == null) {
                            return uri;
                        }
                        return new RedisURI(uri.getScheme(), redisCluster.getHost(), redisCluster.getMappedPort(uri.getPort()));
                    }
                })
                .addNodeAddress("redis://127.0.0.1:" + redisCluster.getFirstMappedPort());
        RedissonClient redisson = Redisson.create(config);

        RedisCluster nodes = redisson.getRedisNodes(RedisNodes.CLUSTER);
        for (RedisClusterSlave slave : nodes.getSlaves()) {
            slave.setConfig("notify-keyspace-events", "Eg");
        }
        for (RedisClusterMaster master : nodes.getMasters()) {
            master.setConfig("notify-keyspace-events", "Eg");
        }

        AtomicInteger subscribedCounter = new AtomicInteger();
        AtomicInteger unsubscribedCounter = new AtomicInteger();
        RTopic topic = redisson.getTopic("__keyevent@0__:del", StringCodec.INSTANCE);
        int id1 = topic.addListener(new StatusListener() {
            @Override
            public void onSubscribe(String channel) {
                subscribedCounter.incrementAndGet();
            }

            @Override
            public void onUnsubscribe(String channel) {
                unsubscribedCounter.incrementAndGet();
            }
        });

        AtomicInteger counter = new AtomicInteger();

        MessageListener<String> listener = (channel, msg) -> {
            System.out.println("mes " + channel + " counter " + counter.get());
            counter.incrementAndGet();
        };
        int id2 = topic.addListener(String.class, listener);

        for (int i = 0; i < 10; i++) {
            redisson.getBucket("" + i).set(i);
            redisson.getBucket("" + i).delete();
            Thread.sleep(7);
        }

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> counter.get() > 9);
        assertThat(subscribedCounter.get()).isEqualTo(1);
        assertThat(unsubscribedCounter.get()).isZero();

        topic.removeListener(id1, id2);

        redisson.shutdown();
        redisCluster.stop();
    }

    @Test
    public void testCountSubscribers() {
        RTopic topic1 = redisson.getTopic("topic", LongCodec.INSTANCE);
        assertThat(topic1.countSubscribers()).isZero();
        int id = topic1.addListener(Long.class, (channel, msg) -> {
        });
        assertThat(topic1.countSubscribers()).isOne();
        topic1.removeListener(id);
        assertThat(topic1.countSubscribers()).isZero();
    }
    
    @Test
    public void testCountListeners() {
        RTopic topic1 = redisson.getTopic("topic", LongCodec.INSTANCE);
        assertThat(topic1.countListeners()).isZero();
        int id = topic1.addListener(Long.class, (channel, msg) -> {
        });
        assertThat(topic1.countListeners()).isOne();

        RTopic topic2 = redisson.getTopic("topic2", LongCodec.INSTANCE);
        assertThat(topic2.countListeners()).isZero();
        int id2 = topic2.addListener(Long.class, (channel, msg) -> {
        });
        assertThat(topic2.countListeners()).isOne();

        topic1.removeListener(id);
        assertThat(topic1.countListeners()).isZero();

        topic2.removeListener(id2);
        assertThat(topic2.countListeners()).isZero();
    }
    
    @Test
    public void testPing() throws InterruptedException {
        Config config = createConfig();
        config.useSingleServer()
            .setPingConnectionInterval(50)
            .setConnectTimeout(20_000)
            .setTimeout(25_000_000)
            .setRetryInterval(750)
            .setConnectionMinimumIdleSize(4)
            .setConnectionPoolSize(16);        
        RedissonClient redisson = Redisson.create(config);

        int count = 3000;
        CountDownLatch latch = new CountDownLatch(count);
        
        RTopic eventsTopic = redisson.getTopic("eventsTopic");
        eventsTopic.addListener(String.class, (channel, msg) -> {
            latch.countDown();
        });

        for(int i = 0; i<count; i++){
            final String message = UUID.randomUUID().toString();
            eventsTopic.publish(message);
            Thread.sleep(10);
        }
        
        assertThat(latch.await(60, TimeUnit.SECONDS)).isTrue();
        
        redisson.shutdown();
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
                        RTopic t = redisson.getTopic("PUBSUB_" + j);
                        int listenerId = t.addListener(new StatusListener() {
                            @Override
                            public void onUnsubscribe(String channel) {
                            }
                            
                            @Override
                            public void onSubscribe(String channel) {
                            }
                        });
                        t.publish("message");
                        t.removeListener(listenerId);
                    }
                }
            };
            Future<?> s = executor.submit(worker);
            futures.add(s);
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(120, TimeUnit.SECONDS)).isTrue();

        for (Future<?> future : futures) {
            future.get();
        }
    }


    @Test
    public void testCommandsOrdering() {
        RTopic topic1 = redisson.getTopic("topic", LongCodec.INSTANCE);
        AtomicBoolean stringMessageReceived = new AtomicBoolean();
        topic1.addListener(Long.class, (channel, msg) -> {
            assertThat(msg).isEqualTo(123);
            stringMessageReceived.set(true);
        });
        topic1.publish(123L);

        await().atMost(Duration.ofSeconds(1)).untilTrue(stringMessageReceived);

        topic1.removeAllListeners();
    }

    @Test
    public void testTopicState() throws InterruptedException {
        RTopic stringTopic = redisson.getTopic("test1", StringCodec.INSTANCE);
        for (int i = 0; i < 3; i++) {
            AtomicInteger stringMessageReceived = new AtomicInteger();
            int listenerId = stringTopic.addListener(String.class, new MessageListener<String>() {
                @Override
                public void onMessage(CharSequence channel, String msg) {
                    assertThat(msg).isEqualTo("testmsg");
                    stringMessageReceived.incrementAndGet();
                }
            });
            RPatternTopic patternTopic = redisson.getPatternTopic("test*", StringCodec.INSTANCE);
            int patternListenerId = patternTopic.addListener(String.class, new PatternMessageListener<String>() {
                @Override
                public void onMessage(CharSequence pattern, CharSequence channel, String msg) {
                    assertThat(msg).isEqualTo("testmsg");
                    stringMessageReceived.incrementAndGet();
                }
            });

            stringTopic.publish("testmsg");
            
            await().atMost(Duration.ofSeconds(1)).until(() -> stringMessageReceived.get() == 2);
            
            stringTopic.removeListener(listenerId);
            patternTopic.removeListener(patternListenerId);
        }
    }
    
    @Test
    public void testMultiTypeConnection() throws InterruptedException {
        RTopic stringTopic = redisson.getTopic("test1", StringCodec.INSTANCE);
        AtomicBoolean stringMessageReceived = new AtomicBoolean();
        stringTopic.addListener(String.class, new MessageListener<String>() {
            @Override
            public void onMessage(CharSequence channel, String msg) {
                assertThat(msg).isEqualTo("testmsg");
                stringMessageReceived.set(true);
            }
        });
        stringTopic.publish("testmsg");
        
        RTopic longTopic = redisson.getTopic("test2", LongCodec.INSTANCE);
        AtomicBoolean longMessageReceived = new AtomicBoolean();
        longTopic.addListener(Long.class, new MessageListener<Long>() {

            @Override
            public void onMessage(CharSequence channel, Long msg) {
                assertThat(msg).isEqualTo(1L);
                longMessageReceived.set(true);
            }
        });
        longTopic.publish(1L);
        
        await().atMost(Duration.ofSeconds(1)).untilTrue(stringMessageReceived);
        await().atMost(Duration.ofSeconds(1)).untilTrue(longMessageReceived);
        stringTopic.removeAllListeners();
    }
    
    @Test
    public void testSyncCommands() throws InterruptedException {
        RTopic topic = redisson.getTopic("system_bus");
        RSet<String> redissonSet = redisson.getSet("set1");
        CountDownLatch latch = new CountDownLatch(1);
        topic.addListener(String.class, (channel, msg) -> {
            for (int j = 0; j < 1000; j++) {
                redissonSet.contains("" + j);
            }
            latch.countDown();
        });
        
        topic.publish("sometext");
        
        latch.await();
        topic.removeAllListeners();
    }
    
    @Test
    public void testInnerPublish() throws InterruptedException {

        final RTopic topic1 = redisson.getTopic("topic1");
        final CountDownLatch messageRecieved = new CountDownLatch(3);
        int listenerId = topic1.addListener(Message.class, (channel, msg) -> {
            assertThat(msg).isEqualTo(new Message("test"));
            messageRecieved.countDown();
        });

        RedissonClient redisson2 = createInstance();
        final RTopic topic2 = redisson2.getTopic("topic2");
        topic2.addListener(Message.class, (channel, msg) -> {
            messageRecieved.countDown();
            Message m = new Message("test");
            if (!msg.equals(m)) {
                topic1.publish(m);
                topic2.publish(m);
            }
        });
        topic2.publish(new Message("123"));

        assertThat(messageRecieved.await(5, TimeUnit.SECONDS)).isTrue();

        topic1.removeAllListeners();
        redisson2.shutdown();
    }

    @Test
    public void testStatus() throws InterruptedException {
        final RTopic topic1 = redisson.getTopic("topic1");
        final CountDownLatch l = new CountDownLatch(1);
        int listenerId = topic1.addListener(new BaseStatusListener() {
            @Override
            public void onSubscribe(String channel) {
                assertThat(channel).isEqualTo("topic1");
                l.countDown();
            }
        });

        Thread.sleep(500);

        int listenerId2 = topic1.addListener(new BaseStatusListener() {
            @Override
            public void onUnsubscribe(String channel) {
                assertThat(channel).isEqualTo("topic1");
                l.countDown();
            }
        });
        topic1.removeListener(listenerId);
        topic1.removeListener(listenerId2);

        assertThat(l.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testSlotMigrationInCluster() {
        withCluster(client -> {
            Config config = client.getConfig();
            config.useClusterServers()
                    .setScanInterval(1000)
                    .setSubscriptionMode(SubscriptionMode.MASTER);

            RedissonClient redisson = Redisson.create(config);

            RedisClientConfig cfg = new RedisClientConfig();
            cfg.setAddress(client.getConfig().useClusterServers().getNodeAddresses().get(0));
            RedisClient c = RedisClient.create(cfg);
            RedisConnection cc = c.connect();
            List<ClusterNodeInfo> mastersList = cc.sync(RedisCommands.CLUSTER_NODES);
            mastersList = mastersList.stream().filter(i -> i.containsFlag(ClusterNodeInfo.Flag.MASTER)).collect(Collectors.toList());
            c.shutdown();

            int slot = 10922;

            ClusterNodeInfo destination = mastersList.stream().filter(i -> !i.getSlotRanges().isEmpty() &&
                                    !i.getSlotRanges().iterator().next().hasSlot(slot)).findAny().get();
            ClusterNodeInfo source = mastersList.stream().filter(i -> !i.getSlotRanges().isEmpty() &&
                                    i.getSlotRanges().iterator().next().hasSlot(slot)).findAny().get();

            RedisClientConfig sourceCfg = new RedisClientConfig();
            sourceCfg.setAddress(config.useClusterServers().getNatMapper().map(source.getAddress()));
            RedisClient sourceClient = RedisClient.create(sourceCfg);
            RedisConnection sourceConnection = sourceClient.connect();

            RedisClientConfig destinationCfg = new RedisClientConfig();
            destinationCfg.setAddress(config.useClusterServers().getNatMapper().map(destination.getAddress()));
            RedisClient destinationClient = RedisClient.create(destinationCfg);
            RedisConnection destinationConnection = destinationClient.connect();

            AtomicReference<String> reference = new AtomicReference();
            String channelName = "test{kaO}";
            RTopic topic = redisson.getTopic(channelName);
            topic.addListener(String.class, (ch, m) -> {
                reference.set(m);
            });

            List<String> destList = destinationConnection.sync(RedisCommands.PUBSUB_CHANNELS);
            assertThat(destList).isEmpty();
            List<String> sourceList = sourceConnection.sync(RedisCommands.PUBSUB_CHANNELS);
            assertThat(sourceList).containsOnly(channelName);

            destinationConnection.sync(RedisCommands.CLUSTER_SETSLOT, slot, "IMPORTING", source.getNodeId());
            sourceConnection.sync(RedisCommands.CLUSTER_SETSLOT, slot, "MIGRATING", destination.getNodeId());

            List<String> keys = sourceConnection.sync(RedisCommands.CLUSTER_GETKEYSINSLOT, slot, 100);
            List<Object> params = new ArrayList<>();
            params.add(destination.getAddress().getHost());
            params.add(destination.getAddress().getPort());
            params.add("");
            params.add(0);
            params.add(2000);
            params.add("KEYS");
            params.addAll(keys);
            sourceConnection.sync(RedisCommands.MIGRATE, params.toArray());

            for (ClusterNodeInfo node : mastersList) {
                if (node.getSlotRanges().isEmpty()) {
                    continue;
                }
                RedisClientConfig cc1 = new RedisClientConfig();
                cc1.setAddress(config.useClusterServers().getNatMapper().map(node.getAddress()));
                RedisClient ccc = RedisClient.create(cc1);
                RedisConnection connection = ccc.connect();
                connection.sync(RedisCommands.CLUSTER_SETSLOT, slot,
                        "NODE", destination.getNodeId());
                ccc.shutdownAsync();
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            topic.publish("mymessage");
            Awaitility.waitAtMost(Duration.ofSeconds(1)).until(() -> "mymessage".equals(reference.get()));

            List<String> sourceList2 = sourceConnection.sync(RedisCommands.PUBSUB_CHANNELS);
            assertThat(sourceList2).isEmpty();
            List<String> destList2 = destinationConnection.sync(RedisCommands.PUBSUB_CHANNELS);
            assertThat(destList2).containsOnly(channelName);

            sourceClient.shutdown();
            destinationClient.shutdown();
            redisson.shutdown();
        });
    }

    @Test
    public void testUnsubscribe() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1);

        RTopic topic1 = redisson.getTopic("topic1");
        int listenerId = topic1.addListener(Message.class, (channel, msg) -> {
            Assertions.fail();
        });
        topic1.addListener(Message.class, (channel, msg) -> {
            assertThat(channel.toString()).isEqualTo("topic1");
            assertThat(msg).isEqualTo(new Message("123"));
            messageRecieved.countDown();
        });
        topic1.removeListener(listenerId);

        topic1 = redisson.getTopic("topic1");
        topic1.publish(new Message("123"));

        assertThat(messageRecieved.await(5, TimeUnit.SECONDS)).isTrue();

        topic1.removeAllListeners();
    }

    @Test
    public void testRemoveAllListeners() throws InterruptedException {
        RTopic topic1 = redisson.getTopic("topic1");
        AtomicInteger counter = new AtomicInteger();
        
        for (int i = 0; i < 10; i++) {
            topic1.addListener(Message.class, (channel, msg) -> {
                counter.incrementAndGet();
            });
        }

        topic1 = redisson.getTopic("topic1");
        topic1.removeAllListeners();
        topic1.publish(new Message("123"));

        Thread.sleep(1000);
        assertThat(counter.get()).isZero();
        topic1.removeAllListeners();
    }

    @Test
    public void testSubscribeLimit() {
        Config config = redisson.getConfig();
        int connection = 10;
        int subscription = 5;
        config.useSingleServer()
                .setSubscriptionConnectionPoolSize(connection)
                .setSubscriptionsPerConnection(subscription);
        RedissonClient redissonClient = Redisson.create(config);
        final Queue<RTopic> queue = new LinkedList<>();
        int i = 0;
        boolean timeout = false;
        while (true) {
           try{
              if (timeout) {
                  System.out.println("destroy");
                  queue.poll().removeAllListeners();
              }
              RTopic topic = redissonClient.getTopic(++i + "");
                topic.addListener(Object.class, new MessageListener<Object>() {
                    @Override
                    public void onMessage(CharSequence channel, Object msg) {
                        // TODO Auto-generated method stub

                    }
                });
              queue.offer(topic);
              if (i > 1000) {
                  break;
              }
              System.out.println(i + " - " + queue.size());
           }catch(Exception e){
                timeout = true;
                e.printStackTrace();
           }
        }
        
        redissonClient.shutdown();
    }
    
    @Test
    public void testRemoveAllListeners2() throws InterruptedException {
        RTopic topic1 = redisson.getTopic("topic1");
        AtomicInteger counter = new AtomicInteger();
        
        for (int j = 0; j < 100; j++) {
            for (int i = 0; i < 10; i++) {
                topic1.addListener(Message.class, (channel, msg) -> {
                    counter.incrementAndGet();
                });
            }
            
            topic1 = redisson.getTopic("topic1");
            topic1.removeAllListeners();
            topic1.publish(new Message("123"));
        }

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(counter.get()).isZero());
    }
    
    @Test
    public void testRemoveByInstance() throws InterruptedException {
        RTopic topic1 = redisson.getTopic("topic1");
        MessageListener listener = new MessageListener() {
            @Override
            public void onMessage(CharSequence channel, Object msg) {
                Assertions.fail();
            }
        };
        
        topic1.addListener(Message.class, listener);

        topic1 = redisson.getTopic("topic1");
        topic1.removeListener(listener);
        topic1.publish(new Message("123"));
    }


    @Test
    public void testLazyUnsubscribe() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1);

        RTopic topic1 = redisson.getTopic("topic");
        int listenerId = topic1.addListener(Message.class, (channel, msg) -> {
            Assertions.fail();
        });
        Thread.sleep(1000);
        topic1.removeListener(listenerId);
        Thread.sleep(1000);

        RedissonClient redisson2 = createInstance();
        RTopic topic2 = redisson2.getTopic("topic");
        topic2.addListener(Message.class, (channel, msg) -> {
            assertThat(msg).isEqualTo(new Message("123"));
            messageRecieved.countDown();
        });
        topic2.publish(new Message("123"));

        assertThat(messageRecieved.await(5, TimeUnit.SECONDS)).isTrue();

        redisson2.shutdown();
    }

    @Test
    public void test() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(2);

        RTopic topic1 = redisson.getTopic("topic");
        topic1.addListener(Message.class, (channel, msg) -> {
            assertThat(msg).isEqualTo(new Message("123"));
            messageRecieved.countDown();
        });

        RedissonClient redisson2 = createInstance();
        RTopic topic2 = redisson2.getTopic("topic");
        topic2.addListener(Message.class, (channel, msg) -> {
            assertThat(msg).isEqualTo(new Message("123"));
            messageRecieved.countDown();
        });
        topic2.publish(new Message("123"));

        messageRecieved.await();

        topic1.removeAllListeners();
        redisson2.shutdown();
    }

    @Test
    public void testHeavyLoad() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1000);

        AtomicLong counter = new AtomicLong();
        RTopic topic1 = redisson.getTopic("topic");
        topic1.addListener(Message.class, (channel, msg) -> {
            assertThat(msg).isEqualTo(new Message("123"));
            messageRecieved.countDown();
            counter.incrementAndGet();
        });

        RedissonClient redisson2 = createInstance();
        RTopic topic2 = redisson2.getTopic("topic");
        topic2.addListener(Message.class, (channel, msg) -> {
            assertThat(msg).isEqualTo(new Message("123"));
            messageRecieved.countDown();
        });

        int count = 10000;
        for (int i = 0; i < count; i++) {
            topic2.publish(new Message("123"));
        }

        messageRecieved.await();

        Thread.sleep(1000);

        assertThat(count).isEqualTo(counter.get());

        topic1.removeAllListeners();
        redisson2.shutdown();
    }
    
    @Test
    public void testListenerRemove() throws InterruptedException {
        RTopic topic1 = redisson.getTopic("topic");
        int id = topic1.addListener(Message.class, (channel, msg) -> {
            Assertions.fail();
        });

        RedissonClient redisson2 = createInstance();
        RTopic topic2 = redisson2.getTopic("topic");
        topic1.removeListener(id);
        topic2.publish(new Message("123"));

        Thread.sleep(1000);

        redisson2.shutdown();
    }

    @Test
    public void testReattach() throws Exception {
        GenericContainer<?> redis =
                new GenericContainer<>("redis:7.2")
                        .withExposedPorts(6379);
        redis.start();

        Config config = new Config();
        config.setProtocol(protocol);
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:" + redis.getFirstMappedPort());
        RedissonClient redisson = Redisson.create(config);
        
        final AtomicBoolean executed = new AtomicBoolean();
        final AtomicInteger subscriptions = new AtomicInteger();
        
        RTopic topic = redisson.getTopic("topic");
        topic.addListener(new StatusListener() {
            
            @Override
            public void onUnsubscribe(String channel) {
            }
            
            @Override
            public void onSubscribe(String channel) {
                subscriptions.incrementAndGet();
            }
        });
        topic.addListener(Integer.class, new MessageListener<Integer>() {
            @Override
            public void onMessage(CharSequence channel, Integer msg) {
                executed.set(true);
            }
        });

        redis.setPortBindings(Arrays.asList(redis.getFirstMappedPort() + ":6379"));
        redis.stop();
        redis.start();

        Thread.sleep(2000);

        redisson.getTopic("topic").publish(1);
        
        await().atMost(2, TimeUnit.SECONDS).untilTrue(executed);
        await().atMost(2, TimeUnit.SECONDS).until(() -> subscriptions.get() == 2);
        
        redisson.shutdown();
        redis.stop();
    }

    @Test
    public void testAddListenerFailover() throws Exception {
        GenericContainer<?> redis =
                new GenericContainer<>("redis:7.2")
                        .withExposedPorts(6379);
        redis.start();

        Config config = new Config();
        config.setProtocol(protocol);
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:" + redis.getFirstMappedPort());
        RedissonClient redisson = Redisson.create(config);

        redis.setPortBindings(Arrays.asList(redis.getFirstMappedPort() + ":6379"));
        redis.stop();

        RTopic topic = redisson.getTopic("topic");
        Assertions.assertThrows(RedisException.class, () -> {
            topic.addListener(Integer.class, (channel, msg) -> {
            });
        });

        redis.start();

        Thread.sleep(3000);

        AtomicBoolean executed = new AtomicBoolean();
        topic.addListener(Integer.class, (channel, msg) -> {
            assertThat(msg).isEqualTo(1);
            executed.set(true);
        });

        redisson.getTopic("topic").publish(1);

        await().atMost(1, TimeUnit.SECONDS).untilTrue(executed);

        redisson.shutdown();
        redis.stop();
    }


//    @Test
    public void testReattachInSentinelLong() throws Exception {
        for (int i = 0; i < 25; i++) {
            testReattachInSentinel();
        }
    }
    
//    @Test
    public void testReattachInClusterLong() throws Exception {
        for (int i = 0; i < 25; i++) {
            testReattachInClusterSlave();
        }
    }
    
    @Test
    public void testResubscriptionAfterFailover() throws Exception {
        withSentinel((nodes, config) -> {
            config.useSentinelServers()
                    .setSubscriptionsPerConnection(20)
                    .setSubscriptionConnectionPoolSize(200);

            RedissonClient redissonClient = Redisson.create(config);

            ScheduledExecutorService executor1 = Executors.newScheduledThreadPool(5);

            AtomicBoolean exceptionDetected = new AtomicBoolean(false);

            Deque<String> status = new ConcurrentLinkedDeque<>();
            Runnable rLockPayload =
                    () -> {
                        try {
                            Integer randomLock = ThreadLocalRandom.current().nextInt(100);
                            RLock lock = redissonClient.getLock(randomLock.toString());
                            lock.lock(10, TimeUnit.SECONDS);
                            lock.unlock();
                            status.add("ok");
                        } catch (Exception e) {
                            if (e.getMessage().contains("READONLY")
                                    || e.getMessage().contains("ERR WAIT cannot be used")) {
                                // skip
                                return;
                            }

                            status.add("failed");
                            if (e.getCause() != null
                                    && e.getCause().getMessage().contains("slaves were synced")) {
                                return;
                            }
                            e.printStackTrace();
                            exceptionDetected.set(true);
                        }
                    };

            executor1.scheduleAtFixedRate(rLockPayload, 100, 50, TimeUnit.MILLISECONDS);
            executor1.scheduleAtFixedRate(rLockPayload, 100, 50, TimeUnit.MILLISECONDS);
            executor1.scheduleAtFixedRate(rLockPayload, 100, 50, TimeUnit.MILLISECONDS);
            executor1.scheduleAtFixedRate(rLockPayload, 100, 50, TimeUnit.MILLISECONDS);
            executor1.scheduleAtFixedRate(rLockPayload, 100, 50, TimeUnit.MILLISECONDS);

            try {
                TimeUnit.SECONDS.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            nodes.get(0).stop();

            try {
                TimeUnit.SECONDS.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            GenericContainer<?> slave =
                    new GenericContainer<>("bitnami/redis:6.2.13")
                            .withNetwork(nodes.get(1).getNetwork())
                            .withEnv("REDIS_REPLICATION_MODE", "slave")
                            .withEnv("REDIS_MASTER_HOST", "slave0")
                            .withEnv("ALLOW_EMPTY_PASSWORD", "yes")
                            .withNetworkAliases("slave2")
                            .withExposedPorts(6379);
            nodes.add(slave);
            slave.start();

            System.out.println("Failover Finished, start to see Subscribe timeouts now. Can't recover this without a refresh of redison client ");
            try {
                TimeUnit.SECONDS.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            assertThat(exceptionDetected.get()).isFalse();
            assertThat(status.peekLast()).isEqualTo("ok");

            executor1.shutdown();

            redissonClient.shutdown();

        }, 1);
    }

    private void withSentinel(BiConsumer<List<GenericContainer<?>>, Config> callback, int slaves) throws InterruptedException {
        Network network = Network.newNetwork();

        List<GenericContainer<? extends GenericContainer<?>>> nodes = new ArrayList<>();

        GenericContainer<?> master =
                new GenericContainer<>("bitnami/redis:6.2.13")
                        .withNetwork(network)
                        .withEnv("REDIS_REPLICATION_MODE", "master")
                        .withEnv("ALLOW_EMPTY_PASSWORD", "yes")
                        .withNetworkAliases("redis")
                        .withExposedPorts(6379);
        master.start();
        assert master.getNetwork() == network;
        int masterPort = master.getFirstMappedPort();
        master.withCreateContainerCmdModifier(cmd -> {
            cmd.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindPort(Integer.valueOf(masterPort)),
                            cmd.getExposedPorts()[0]));
        });
        nodes.add(master);

        for (int i = 0; i < slaves; i++) {
            GenericContainer<?> slave =
                    new GenericContainer<>("bitnami/redis:6.2.13")
                            .withNetwork(network)
                            .withEnv("REDIS_REPLICATION_MODE", "slave")
                            .withEnv("REDIS_MASTER_HOST", "redis")
                            .withEnv("ALLOW_EMPTY_PASSWORD", "yes")
                            .withNetworkAliases("slave" + i)
                            .withExposedPorts(6379);
            slave.start();
            int slavePort = slave.getFirstMappedPort();
            slave.withCreateContainerCmdModifier(cmd -> {
                cmd.getHostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindPort(Integer.valueOf(slavePort)),
                                cmd.getExposedPorts()[0]));
            });
            nodes.add(slave);
        }

        GenericContainer<?> sentinel1 =
                new GenericContainer<>("bitnami/redis-sentinel:6.2.13")
                        .withNetwork(network)
                        .withEnv("REDIS_SENTINEL_DOWN_AFTER_MILLISECONDS", "5000")
                        .withEnv("REDIS_SENTINEL_FAILOVER_TIMEOUT", "10000")
                        .withNetworkAliases("sentinel1")
                        .withExposedPorts(26379);
        sentinel1.start();
        int sentinel1Port = sentinel1.getFirstMappedPort();
        sentinel1.withCreateContainerCmdModifier(cmd -> {
            cmd.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindPort(Integer.valueOf(sentinel1Port)),
                            cmd.getExposedPorts()[0]));
        });
        nodes.add(sentinel1);

        GenericContainer<?> sentinel2 =
                new GenericContainer<>("bitnami/redis-sentinel:6.2.13")
                        .withNetwork(network)
                        .withEnv("REDIS_SENTINEL_DOWN_AFTER_MILLISECONDS", "5000")
                        .withEnv("REDIS_SENTINEL_FAILOVER_TIMEOUT", "10000")
                        .withNetworkAliases("sentinel2")
                        .withExposedPorts(26379);
        sentinel2.start();
        int sentinel2Port = sentinel2.getFirstMappedPort();
        sentinel2.withCreateContainerCmdModifier(cmd -> {
            cmd.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindPort(Integer.valueOf(sentinel2Port)),
                            cmd.getExposedPorts()[0]));
        });
        nodes.add(sentinel2);

        GenericContainer<?> sentinel3 =
                new GenericContainer<>("bitnami/redis-sentinel:6.2.13")
                        .withNetwork(network)
                        .withEnv("REDIS_SENTINEL_DOWN_AFTER_MILLISECONDS", "5000")
                        .withEnv("REDIS_SENTINEL_FAILOVER_TIMEOUT", "10000")
                        .withNetworkAliases("sentinel3")
                        .withExposedPorts(26379);
        sentinel3.start();
        int sentinel3Port = sentinel3.getFirstMappedPort();
        sentinel3.withCreateContainerCmdModifier(cmd -> {
            cmd.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindPort(Integer.valueOf(sentinel3Port)),
                            cmd.getExposedPorts()[0]));
        });
        nodes.add(sentinel3);

        Thread.sleep(5000);

        Config config = new Config();
        config.setProtocol(protocol);
        config.useSentinelServers()
                .setNatMapper(new NatMapper() {

                    @Override
                    public RedisURI map(RedisURI uri) {
                        for (GenericContainer<? extends GenericContainer<?>> node : nodes) {
                            if (node.getContainerInfo() == null) {
                                continue;
                            }

                            Ports.Binding[] mappedPort = node.getContainerInfo().getNetworkSettings()
                                    .getPorts().getBindings().get(new ExposedPort(uri.getPort()));

                            Map<String, ContainerNetwork> ss = node.getContainerInfo().getNetworkSettings().getNetworks();
                            ContainerNetwork s = ss.values().iterator().next();

                            if (uri.getPort() == 6379 && node.getNetworkAliases().contains("slave0")) {
                                return new RedisURI(uri.getScheme(), "127.0.0.1", Integer.valueOf(mappedPort[0].getHostPortSpec()));
                            }

                            if ("redis".equals(uri.getHost())
                                    && node.getNetworkAliases().contains(uri.getHost())) {
                                return new RedisURI(uri.getScheme(), "127.0.0.1", Integer.valueOf(mappedPort[0].getHostPortSpec()));
                            }

                            if (mappedPort != null
                                    && s.getIpAddress().equals(uri.getHost())) {
                                return new RedisURI(uri.getScheme(), "127.0.0.1", Integer.valueOf(mappedPort[0].getHostPortSpec()));
                            }
                        }
                        return uri;
                    }
                })
                .addSentinelAddress("redis://127.0.0.1:" + sentinel1.getFirstMappedPort())
                .setMasterName("mymaster");

        callback.accept(nodes, config);

        nodes.forEach(n -> n.stop());
        network.close();
    }


    @Test
    public void testReattachInSentinel() throws Exception {
        withSentinel((nodes, config) -> {
            final AtomicBoolean executed = new AtomicBoolean();
            final AtomicInteger subscriptions = new AtomicInteger();

            RedissonClient redisson = Redisson.create(config);
            RTopic topic = redisson.getTopic("topic");
            topic.addListener(new StatusListener() {

                @Override
                public void onUnsubscribe(String channel) {
                }

                @Override
                public void onSubscribe(String channel) {
                    subscriptions.incrementAndGet();
                }
            });
            topic.addListener(Integer.class, new MessageListener<Integer>() {
                @Override
                public void onMessage(CharSequence channel, Integer msg) {
                    executed.set(true);
                }
            });

            sendCommands(redisson, "topic");

            nodes.forEach(n -> n.stop());

            try {
                TimeUnit.SECONDS.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            nodes.forEach(n -> n.start());

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            redisson.getTopic("topic").publish(1);

            await().atMost(20, TimeUnit.SECONDS).until(() -> subscriptions.get() == 2);
            assertThat(executed.get()).isTrue();

            redisson.shutdown();
        }, 2);
    }

//    @Test
    public void testReattachInSentinel2() throws Exception {
        RedisRunner.RedisProcess master = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6440)
                .run();
        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6440)
                .run();
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6440)
                .run();
        RedisRunner.RedisProcess sentinel1 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26379)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6440, 2)
                .run();
        RedisRunner.RedisProcess sentinel2 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26380)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6440, 2)
                .run();
        RedisRunner.RedisProcess sentinel3 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26381)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6440, 2)
                .run();
        
        Thread.sleep(5000); 
        
        Config config = new Config();
        config.useSentinelServers()
            .setLoadBalancer(new RandomLoadBalancer())
            .addSentinelAddress(sentinel3.getRedisServerAddressAndPort()).setMasterName("myMaster");
        RedissonClient redisson = Redisson.create(config);
        
        final AtomicBoolean executed = new AtomicBoolean();
        final AtomicInteger subscriptions = new AtomicInteger();
        
        RTopic topic = redisson.getTopic("topic");
        topic.addListener(new StatusListener() {
            
            @Override
            public void onUnsubscribe(String channel) {
            }
            
            @Override
            public void onSubscribe(String channel) {
                subscriptions.incrementAndGet();
            }
        });
        topic.addListener(Integer.class, new MessageListener<Integer>() {
            @Override
            public void onMessage(CharSequence channel, Integer msg) {
                executed.set(true);
            }
        });
        
        sendCommands(redisson, "topic");
        
        sentinel1.stop();
        sentinel2.stop();
        sentinel3.stop();
        master.stop();
        slave1.stop();
        slave2.stop();
        
        Thread.sleep(TimeUnit.SECONDS.toMillis(20));
        
        topic.removeAllListeners();
        long t = System.currentTimeMillis();
        topic.addListenerAsync(new StatusListener() {
            
            @Override
            public void onUnsubscribe(String channel) {
            }
            
            @Override
            public void onSubscribe(String channel) {
                subscriptions.incrementAndGet();
            }
        });
        topic.addListenerAsync(Integer.class, new MessageListener<Integer>() {
            @Override
            public void onMessage(CharSequence channel, Integer msg) {
                executed.set(true);
            }
        });
        
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        
        master = new RedisRunner()
                .port(6390)
                .nosave()
                .randomDir()
                .run();
        slave1 = new RedisRunner()
                .port(6391)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6390)
                .run();
        slave2 = new RedisRunner()
                .port(6392)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6390)
                .run();
        sentinel1 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26379)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6390, 2)
                .run();
        sentinel2 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26380)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6390, 2)
                .run();
        sentinel3 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26381)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6390, 2)
                .run();
        
        redisson.getTopic("topic").publish(1);
        
        await().atMost(20, TimeUnit.SECONDS).until(() -> subscriptions.get() == 2);
        assertThat(executed.get()).isTrue();
        
        redisson.shutdown();
        sentinel1.stop();
        sentinel2.stop();
        sentinel3.stop();
        master.stop();
        slave1.stop();
        slave2.stop();
    }
    
    protected Thread sendCommands(RedissonClient redisson, String topicName) {
        Thread t = new Thread() {
            @Override
            public void run() {
                List<RFuture<?>> futures = new ArrayList<RFuture<?>>();
                
                for (int i = 0; i < 100; i++) {
                    RFuture<?> f1 = redisson.getBucket("i" + i).getAsync();
                    RFuture<?> f2 = redisson.getBucket("i" + i).setAsync("");
                    RFuture<?> f3 = redisson.getTopic(topicName).publishAsync(1);
                    futures.add(f1);
                    futures.add(f2);
                    futures.add(f3);
                }
                
                for (RFuture<?> rFuture : futures) {
                    try {
                        rFuture.toCompletableFuture().join();
                    } catch (Exception e) {
                        // skip
                    }
                }
            };
        };
        t.start();
        return t;
    }

    @Test
    public void testClusterSharding() throws IOException, InterruptedException {
        testInCluster(redisson -> {
            AtomicInteger counter = new AtomicInteger();
            for (int i = 0; i < 10; i++) {
                int j = i;
                RTopic topic = redisson.getTopic("test" + i);
                topic.addListener(Integer.class, (c, v) -> {
                    assertThat(v).isEqualTo(j);
                    counter.incrementAndGet();
                });
            }

            for (int i = 0; i < 10; i++) {
                RTopic topic = redisson.getTopic("test" + i);
                topic.publish(i);
            }

            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> counter.get() == 10);

            for (int i = 0; i < 10; i++) {
                RTopic topic = redisson.getTopic("test" + i);
                topic.removeAllListeners();
            }
        });
    }

    @Test
    public void testReattachInClusterSlave() {
        withCluster(client -> {
            Config config = client.getConfig();
            config.useClusterServers()
                    .setSubscriptionMode(SubscriptionMode.SLAVE);
            RedissonClient redisson = Redisson.create(config);

            final AtomicBoolean executed = new AtomicBoolean();
            final AtomicInteger subscriptions = new AtomicInteger();

            RTopic topic = redisson.getTopic("topic");
            topic.addListener(new StatusListener() {

                @Override
                public void onUnsubscribe(String channel) {
                }

                @Override
                public void onSubscribe(String channel) {
                    subscriptions.incrementAndGet();
                }
            });
            topic.addListener(Integer.class, new MessageListener<Integer>() {
                @Override
                public void onMessage(CharSequence channel, Integer msg) {
                    executed.set(true);
                }
            });
            assertThat(topic.countListeners()).isEqualTo(2);

            sendCommands(redisson, "topic");

            assertThat(subscriptions.get()).isEqualTo(1);

            RedisCluster nodes = redisson.getRedisNodes(RedisNodes.CLUSTER);
            for (RedisClusterSlave slave : nodes.getSlaves()) {
                RedisClientConfig cc = new RedisClientConfig();
                cc.setAddress("redis://" + slave.getAddr().getHostString() + ":" + slave.getAddr().getPort());
                RedisClient c = RedisClient.create(cc);
                c.connect().async(RedisCommands.SHUTDOWN);
                c.shutdown();
            }

            await().atMost(25, TimeUnit.SECONDS).until(() -> subscriptions.get() == 2);

            executed.set(false);
            redisson.getTopic("topic").publish(1);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertThat(executed.get()).isTrue();
            assertThat(topic.countListeners()).isEqualTo(2);

            redisson.shutdown();
        });
    }

    @Test
    public void testReattachInSentinel3() throws Exception {
        withSentinel((nodes, config) -> {
            config.useSentinelServers()
                    .setSubscriptionsPerConnection(20)
                    .setSubscriptionConnectionPoolSize(200);

            RedissonClient redissonClient = Redisson.create(config);

            ScheduledExecutorService executor1 = Executors.newScheduledThreadPool(5);

            AtomicBoolean exceptionDetected = new AtomicBoolean(false);

            Deque<String> status = new ConcurrentLinkedDeque<>();
            Runnable rLockPayload =
                    () -> {
                        try {
                            Integer randomLock = ThreadLocalRandom.current().nextInt(100);
                            RLock lock = redissonClient.getLock(randomLock.toString());
                            lock.lock(10, TimeUnit.SECONDS);
                            lock.unlock();

                            RTopic t = redissonClient.getTopic("topic_" + randomLock);
                            int s = t.addListener(new StatusListener() {
                                @Override
                                public void onSubscribe(String channel) {
                                }

                                @Override
                                public void onUnsubscribe(String channel) {
                                }
                            });
                            t.removeListener(s);

                            status.add("ok");
                        } catch (Exception e) {
                            status.add("failed");

                            if (e.getMessage().contains("READONLY")
                                    || e.getMessage().contains("ERR WAIT cannot be used")) {
                                // skip
                                return;
                            }

                            if (e.getCause() != null
                                    && e.getCause().getMessage().contains("slaves were synced")) {
                                return;
                            }
                            e.printStackTrace();
                            exceptionDetected.set(true);
                        }
                    };

            executor1.scheduleAtFixedRate(rLockPayload, 100, 50, TimeUnit.MILLISECONDS);
            executor1.scheduleAtFixedRate(rLockPayload, 100, 50, TimeUnit.MILLISECONDS);
            executor1.scheduleAtFixedRate(rLockPayload, 100, 50, TimeUnit.MILLISECONDS);
            executor1.scheduleAtFixedRate(rLockPayload, 100, 50, TimeUnit.MILLISECONDS);
            executor1.scheduleAtFixedRate(rLockPayload, 100, 50, TimeUnit.MILLISECONDS);

            try {
                TimeUnit.SECONDS.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            Integer port = nodes.get(0).getFirstMappedPort();
            nodes.get(0).stop();
            System.out.println("master has been stopped! " + port);

            try {
                TimeUnit.SECONDS.sleep(30);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            assertThat(exceptionDetected.get()).isFalse();
            assertThat(status.peekLast()).isEqualTo("ok");

            executor1.shutdown();

            redissonClient.shutdown();

        }, 1);
    }

    @Test
    public void testReattachInClusterMaster2() throws Exception {
        RedisRunner master1 = new RedisRunner().port(6890).randomDir().nosave();
        RedisRunner master2 = new RedisRunner().port(6891).randomDir().nosave();
        RedisRunner master3 = new RedisRunner().port(6892).randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().port(6900).randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().port(6901).randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().port(6902).randomDir().nosave();

        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterProcesses process = clusterRunner.run();

        Thread.sleep(7000);

        Config config = new Config();
        config.useClusterServers()
                .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        Queue<String> messages = new ConcurrentLinkedQueue<>();
        Queue<String> subscriptions = new ConcurrentLinkedQueue<>();

        int topicsAmount = 100;
        for (int i = 0; i < topicsAmount; i++) {
            RTopic topic = redisson.getTopic("topic" + i);
            int finalI = i;
            topic.addListener(new StatusListener() {

                @Override
                public void onUnsubscribe(String channel) {
                }

                @Override
                public void onSubscribe(String channel) {
                    subscriptions.add("topic" + finalI);
                }
            });
            topic.addListener(String.class, (channel, msg) -> messages.add(msg));
        }

        RedisRunner.RedisProcess master = process.getNodes().stream().filter(x -> x.getRedisServerPort() == master1.getPort()).findFirst().get();
        master.stop();

        Thread.sleep(TimeUnit.SECONDS.toMillis(40));

        assertThat(subscriptions).hasSize(140);

        for (int i = 0; i < topicsAmount; i++) {
            RTopic topic = redisson.getTopic("topic" + i);
            topic.publish("topic" + i);
        }

        Thread.sleep(100);
        assertThat(messages).hasSize(topicsAmount);
    }

    public void withCluster(Consumer<RedissonClient> callback) {
        GenericContainer redisCluster = new GenericContainer<>("vishnunair/docker-redis-cluster")
                .withExposedPorts(6379, 6380, 6381, 6382, 6383, 6384)
                .withStartupCheckStrategy(new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(10)));
        redisCluster.start();

        Config config = new Config();
        config.setProtocol(protocol);
        config.useClusterServers()
                .setNatMapper(new NatMapper() {
                    @Override
                    public RedisURI map(RedisURI uri) {
                        if (redisCluster.getMappedPort(uri.getPort()) == null) {
                            return uri;
                        }
                        return new RedisURI(uri.getScheme(), redisCluster.getHost(), redisCluster.getMappedPort(uri.getPort()));
                    }
                })
                .addNodeAddress("redis://127.0.0.1:" + redisCluster.getFirstMappedPort());

        RedissonClient redisson = Redisson.create(config);
        callback.accept(redisson);
        redisson.shutdown();
        redisCluster.stop();
    }

    public void withCluster2(Consumer<RedissonClient> callback) {
        List<InspectContainerResponse> nodes = new ArrayList<>();

        DockerComposeContainer environment =
                new DockerComposeContainer(new File("src/test/resources/docker-compose.yml"))
                        .withExposedService("redis-node-0", 6379)
                        .withExposedService("redis-node-1", 6379)
                        .withExposedService("redis-node-2", 6379)
                        .withExposedService("redis-node-3", 6379)
                        .withExposedService("redis-node-4", 6379)
                        .withExposedService("redis-node-5", 6379)
                        .withExposedService("redis-node-6", 6379)
                        .withExposedService("redis-node-7", 6379);

        environment.start();

        try {
            Thread.sleep(25000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < 8; i++) {
            Optional<ContainerState> cc = environment.getContainerByServiceName("redis-node-" + i);
            nodes.add(cc.get().getContainerInfo());
        }

        Optional<ContainerState> cc2 = environment.getContainerByServiceName("redis-node-0");

        Config config = new Config();
        config.useClusterServers()
                .setNatMapper(new NatMapper() {

                    @Override
                    public RedisURI map(RedisURI uri) {
                        for (InspectContainerResponse node : nodes) {
                            Ports.Binding[] mappedPort = node.getNetworkSettings()
                                    .getPorts().getBindings().get(new ExposedPort(uri.getPort()));

                            Map<String, ContainerNetwork> ss = node.getNetworkSettings().getNetworks();
                            ContainerNetwork s = ss.values().iterator().next();

                            if (mappedPort != null
                                    && s.getIpAddress().equals(uri.getHost())) {
                                return new RedisURI(uri.getScheme(), "127.0.0.1", Integer.valueOf(mappedPort[0].getHostPortSpec()));
                            }
                        }
                        return uri;
                    }
                })
                .addNodeAddress("redis://127.0.0.1:" + cc2.get().getFirstMappedPort());

        RedissonClient redisson = Redisson.create(config);

        RedisCluster nodes2 = redisson.getRedisNodes(RedisNodes.CLUSTER);
        for (RedisClusterSlave slave : nodes2.getSlaves()) {
            slave.setConfig("cluster-node-timeout", "1000");
        }
        for (RedisClusterMaster master : nodes2.getMasters()) {
            master.setConfig("cluster-node-timeout", "1000");
        }

        callback.accept(redisson);
        redisson.shutdown();
        environment.stop();
    }

    @Test
    public void testReattachInClusterMaster() throws Exception {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master3 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().randomPort().randomDir().nosave();


        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterProcesses process = clusterRunner.run();

        Thread.sleep(5000);

        Config config = new Config();
        config.useClusterServers()
                .setSubscriptionMode(SubscriptionMode.MASTER)
                .setLoadBalancer(new RandomLoadBalancer())
                .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        final AtomicBoolean executed = new AtomicBoolean();
        final AtomicInteger subscriptions = new AtomicInteger();

        RTopic topic = redisson.getTopic("3");
        topic.addListener(new StatusListener() {

            @Override
            public void onUnsubscribe(String channel) {
            }

            @Override
            public void onSubscribe(String channel) {
                subscriptions.incrementAndGet();
            }
        });
        topic.addListener(Integer.class, new MessageListener<Integer>() {
            @Override
            public void onMessage(CharSequence channel, Integer msg) {
                executed.set(true);
            }
        });

        sendCommands(redisson, "3");

        process.getNodes().stream().filter(x -> master1.getPort() == x.getRedisServerPort())
                .forEach(x -> {
                    try {
                        x.stop();
                        Thread.sleep(18000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                });

        Thread.sleep(25000);

        redisson.getTopic("3").publish(1);

        await().atMost(75, TimeUnit.SECONDS).until(() -> subscriptions.get() == 2);
        assertThat(executed.get()).isTrue();

        redisson.shutdown();
        process.shutdown();
    }

    @Test
    public void testReattachPatternTopicListenersOnClusterFailover() throws Exception {
        final KEYSPACE_EVENTS_OPTIONS keyspaceEvents[] =
                {KEYSPACE_EVENTS_OPTIONS.K, KEYSPACE_EVENTS_OPTIONS.E, KEYSPACE_EVENTS_OPTIONS.A};
        final RedisRunner master = new RedisRunner().randomPort().randomDir().nosave()
                .notifyKeyspaceEvents(keyspaceEvents);
        final RedisRunner slave = new RedisRunner().randomPort().randomDir().nosave()
                .notifyKeyspaceEvents(keyspaceEvents);

        final ClusterRunner clusterRunner = new ClusterRunner().addNode(master, slave);
        final ClusterProcesses process = clusterRunner.run();

        final Config config = new Config();
        config.useClusterServers().addNodeAddress(
                process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());

        final RedissonClient redisson = Redisson.create(config);

        final AtomicInteger subscriptions = new AtomicInteger();
        final AtomicInteger messagesReceived = new AtomicInteger();

        final RPatternTopic topic =
                redisson.getPatternTopic("__keyspace*__:i*", StringCodec.INSTANCE);
        topic.addListener(new PatternStatusListener() {
            @Override
            public void onPUnsubscribe(String pattern) {}

            @Override
            public void onPSubscribe(String pattern) {
                subscriptions.incrementAndGet();
            }
        });
        topic.addListener(String.class,
                (pattern, channel, msg) -> messagesReceived.incrementAndGet());
        assertThat(subscriptions.get()).isEqualTo(1);

        sendCommands(redisson, "dummy").join();
        await().atMost(30, TimeUnit.SECONDS).until(() -> messagesReceived.get() == 100);

        failover(process, master, slave);

        redisson.getBucket("i100").set("");
        await().atMost(30, TimeUnit.SECONDS).until(() -> subscriptions.get() == 2);
        await().atMost(5, TimeUnit.SECONDS).until(() -> messagesReceived.get() == 101);

        redisson.shutdown();
        process.shutdown();
    }

    private void failover(ClusterProcesses processes, RedisRunner master, RedisRunner slave)
            throws InterruptedException {
        final RedisClient masterClient = connect(processes, master);
        try {
            masterClient.connect().sync(new RedisStrictCommand<Void>("DEBUG", "SEGFAULT"));
        } catch (RedisTimeoutException e) {
            // node goes down, so this command times out waiting for the response
        }
        Thread.sleep(java.time.Duration.ofSeconds(25).toMillis());

        final RedisClient slaveClient = connect(processes, slave);
        slaveClient.connect().sync(new RedisStrictCommand<Void>("CLUSTER", "FAILOVER"), "TAKEOVER");
        Thread.sleep(java.time.Duration.ofSeconds(25).toMillis());
    }

    private RedisClient connect(ClusterProcesses processes, RedisRunner runner) {
        return RedisClient.create(new RedisClientConfig()
                .setAddress(processes.getNodes().stream()
                        .filter(node -> node.getRedisServerPort() == runner.getPort())
                        .findFirst()
                        .map(RedisProcess::getRedisServerAddressAndPort)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Failed to find node running at port: " + runner.getPort()
                                        + " in cluster processes"))));
    }
}
