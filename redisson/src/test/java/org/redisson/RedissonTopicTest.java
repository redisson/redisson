package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.redisson.ClusterRunner.ClusterProcesses;
import org.redisson.RedisRunner.KEYSPACE_EVENTS_OPTIONS;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.api.*;
import org.redisson.api.listener.*;
import org.redisson.client.*;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.cluster.ClusterNodeInfo;
import org.redisson.config.Config;
import org.redisson.config.SubscriptionMode;
import org.redisson.connection.balancer.RandomLoadBalancer;

import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class RedissonTopicTest {

    @BeforeAll
    public static void beforeClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.startDefaultRedisServerInstance();
        }
    }

    @AfterAll
    public static void afterClass() throws InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.shutDownDefaultRedisServerInstance();
        }
    }

    @BeforeEach
    public void before() throws IOException, InterruptedException {
        if (RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.startDefaultRedisServerInstance();
        }
    }

    @AfterEach
    public void after() throws InterruptedException {
        if (RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.shutDownDefaultRedisServerInstance();
        }
    }

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
    public void testCountSubscribers() {
        RedissonClient redisson = BaseTest.createInstance();
        RTopic topic1 = redisson.getTopic("topic", LongCodec.INSTANCE);
        assertThat(topic1.countSubscribers()).isZero();
        int id = topic1.addListener(Long.class, (channel, msg) -> {
        });
        assertThat(topic1.countSubscribers()).isOne();
        topic1.removeListener(id);
        assertThat(topic1.countSubscribers()).isZero();

        redisson.shutdown();
    }
    
    @Test
    public void testCountListeners() {
        RedissonClient redisson = BaseTest.createInstance();
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

        redisson.shutdown();
    }
    
    @Test
    public void testPing() throws InterruptedException {
        Config config = BaseTest.createConfig();
        config.useSingleServer()
            .setPingConnectionInterval(50)
            .setConnectTimeout(20_000)
            .setTimeout(25_000_000)
            .setRetryInterval(750)
            .setConnectionMinimumIdleSize(4)
            .setConnectionPoolSize(16);        
        RedissonClient redisson = Redisson.create(config);

        int count = 6000;
        CountDownLatch latch = new CountDownLatch(count);
        
        RTopic eventsTopic = redisson.getTopic("eventsTopic");
        AtomicInteger co = new AtomicInteger();
        eventsTopic.addListener(String.class, (channel, msg) -> {
            co.incrementAndGet();
            latch.countDown();
        });

        for(int i = 0; i<count; i++){
            final String message = UUID.randomUUID().toString();
            eventsTopic.publish(message);
            Thread.sleep(10);
        }
        
        assertThat(latch.await(40, TimeUnit.SECONDS)).isTrue();
        
        redisson.shutdown();
    }
    
    @Test
    public void testConcurrentTopic() throws Exception {
        RedissonClient redisson = BaseTest.createInstance();
        
        int threads = 30;
        int loops = 50000;
        
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
        
        redisson.shutdown();
    }


    @Test
    public void testCommandsOrdering() throws InterruptedException {
        RedissonClient redisson1 = BaseTest.createInstance();
        RTopic topic1 = redisson1.getTopic("topic", LongCodec.INSTANCE);
        AtomicBoolean stringMessageReceived = new AtomicBoolean();
        topic1.addListener(Long.class, (channel, msg) -> {
            assertThat(msg).isEqualTo(123);
            stringMessageReceived.set(true);
        });
        topic1.publish(123L);

        await().atMost(Duration.ofSeconds(1)).untilTrue(stringMessageReceived);

        redisson1.shutdown();
    }

    @Test
    public void testTopicState() throws InterruptedException {
        RedissonClient redisson = BaseTest.createInstance();
        
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
        
        redisson.shutdown();
    }
    
    @Test
    public void testMultiTypeConnection() throws InterruptedException {
        RedissonClient redisson = BaseTest.createInstance();
        
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
        redisson.shutdown();
    }
    
    @Test
    public void testSyncCommands() throws InterruptedException {
        RedissonClient redisson = BaseTest.createInstance();
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
        redisson.shutdown();
    }
    
    @Test
    public void testInnerPublish() throws InterruptedException {

        RedissonClient redisson1 = BaseTest.createInstance();
        final RTopic topic1 = redisson1.getTopic("topic1");
        final CountDownLatch messageRecieved = new CountDownLatch(3);
        int listenerId = topic1.addListener(Message.class, (channel, msg) -> {
            assertThat(msg).isEqualTo(new Message("test"));
            messageRecieved.countDown();
        });

        RedissonClient redisson2 = BaseTest.createInstance();
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

        redisson1.shutdown();
        redisson2.shutdown();
    }

    @Test
    public void testStatus() throws InterruptedException {
        RedissonClient redisson = BaseTest.createInstance();
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
        redisson.shutdown();
    }

    @Test
    public void testSlotMigrationInCluster() throws Exception {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master3 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slot1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slot2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slot3 = new RedisRunner().randomPort().randomDir().nosave();

        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slot1)
                .addNode(master2, slot2)
                .addNode(master3, slot3);
        ClusterProcesses process = clusterRunner.run();

        Config config = new Config();
        config.useClusterServers()
        .setScanInterval(1000)
        .setSubscriptionMode(SubscriptionMode.MASTER)
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        RedisClientConfig cfg = new RedisClientConfig();
        cfg.setAddress(process.getNodes().iterator().next().getRedisServerAddressAndPort());
        RedisClient c = RedisClient.create(cfg);
        RedisConnection cc = c.connect();
        List<ClusterNodeInfo> mastersList = cc.sync(RedisCommands.CLUSTER_NODES);
        mastersList = mastersList.stream().filter(i -> i.containsFlag(ClusterNodeInfo.Flag.MASTER)).collect(Collectors.toList());
        c.shutdown();

        ClusterNodeInfo destination = mastersList.stream().filter(i -> i.getSlotRanges().iterator().next().getStartSlot() != 10922).findAny().get();
        ClusterNodeInfo source = mastersList.stream().filter(i -> i.getSlotRanges().iterator().next().getStartSlot() == 10922).findAny().get();

        RedisClientConfig sourceCfg = new RedisClientConfig();
        sourceCfg.setAddress(source.getAddress());
        RedisClient sourceClient = RedisClient.create(sourceCfg);
        RedisConnection sourceConnection = sourceClient.connect();

        RedisClientConfig destinationCfg = new RedisClientConfig();
        destinationCfg.setAddress(destination.getAddress());
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

        destinationConnection.sync(RedisCommands.CLUSTER_SETSLOT, source.getSlotRanges().iterator().next().getStartSlot(), "IMPORTING", source.getNodeId());
        sourceConnection.sync(RedisCommands.CLUSTER_SETSLOT, source.getSlotRanges().iterator().next().getStartSlot(), "MIGRATING", destination.getNodeId());

        List<String> keys = sourceConnection.sync(RedisCommands.CLUSTER_GETKEYSINSLOT, source.getSlotRanges().iterator().next().getStartSlot(), 100);
        List<Object> params = new ArrayList<Object>();
        params.add(destination.getAddress().getHost());
        params.add(destination.getAddress().getPort());
        params.add("");
        params.add(0);
        params.add(2000);
        params.add("KEYS");
        params.addAll(keys);
        sourceConnection.async(RedisCommands.MIGRATE, params.toArray());

        for (ClusterNodeInfo node : mastersList) {
            RedisClientConfig cc1 = new RedisClientConfig();
            cc1.setAddress(node.getAddress());
            RedisClient ccc = RedisClient.create(cc1);
            RedisConnection connection = ccc.connect();
            connection.sync(RedisCommands.CLUSTER_SETSLOT, source.getSlotRanges().iterator().next().getStartSlot(), "NODE", destination.getNodeId());
            ccc.shutdownAsync();
        }

        Thread.sleep(2000);

        topic.publish("mymessage");
        Awaitility.waitAtMost(Duration.ofSeconds(1)).until(() -> reference.get().equals("mymessage"));

        List<String> destList2 = destinationConnection.sync(RedisCommands.PUBSUB_CHANNELS);
        assertThat(destList2).containsOnly(channelName);
        List<String> sourceList2 = sourceConnection.sync(RedisCommands.PUBSUB_CHANNELS);
        assertThat(sourceList2).isEmpty();

        sourceClient.shutdown();
        destinationClient.shutdown();
        redisson.shutdown();
        process.shutdown();
    }

    @Test
    public void testUnsubscribe() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1);

        RedissonClient redisson = BaseTest.createInstance();
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

        redisson.shutdown();
    }

    @Test
    public void testRemoveAllListeners() throws InterruptedException {
        RedissonClient redisson = BaseTest.createInstance();
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
        
        redisson.shutdown();
    }

    @Test
    public void testSubscribeLimit() throws Exception {
        RedisProcess runner = new RedisRunner()
                .port(RedisRunner.findFreePort())
                .nosave()
                .randomDir()
                .run();
        
        int connection = 10;
        int subscription = 5;
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://localhost:" + runner.getRedisServerPort())
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
        runner.stop();
    }
    
    @Test
    public void testRemoveAllListeners2() throws InterruptedException {
        RedissonClient redisson = BaseTest.createInstance();
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

        redisson.shutdown();
    }
    
    @Test
    public void testRemoveByInstance() throws InterruptedException {
        RedissonClient redisson = BaseTest.createInstance();
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

        redisson.shutdown();
    }


    @Test
    public void testLazyUnsubscribe() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1);

        RedissonClient redisson1 = BaseTest.createInstance();
        RTopic topic1 = redisson1.getTopic("topic");
        int listenerId = topic1.addListener(Message.class, (channel, msg) -> {
            Assertions.fail();
        });
        Thread.sleep(1000);
        topic1.removeListener(listenerId);
        Thread.sleep(1000);

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic topic2 = redisson2.getTopic("topic");
        topic2.addListener(Message.class, (channel, msg) -> {
            assertThat(msg).isEqualTo(new Message("123"));
            messageRecieved.countDown();
        });
        topic2.publish(new Message("123"));

        assertThat(messageRecieved.await(5, TimeUnit.SECONDS)).isTrue();

        redisson1.shutdown();
        redisson2.shutdown();
    }

    @Test
    public void test() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(2);

        RedissonClient redisson1 = BaseTest.createInstance();
        RTopic topic1 = redisson1.getTopic("topic");
        topic1.addListener(Message.class, (channel, msg) -> {
            assertThat(msg).isEqualTo(new Message("123"));
            messageRecieved.countDown();
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic topic2 = redisson2.getTopic("topic");
        topic2.addListener(Message.class, (channel, msg) -> {
            assertThat(msg).isEqualTo(new Message("123"));
            messageRecieved.countDown();
        });
        topic2.publish(new Message("123"));

        messageRecieved.await();

        redisson1.shutdown();
        redisson2.shutdown();
    }

    @Test
    public void testHeavyLoad() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1000);

        AtomicLong counter = new AtomicLong();
        RedissonClient redisson1 = BaseTest.createInstance();
        RTopic topic1 = redisson1.getTopic("topic");
        topic1.addListener(Message.class, (channel, msg) -> {
            assertThat(msg).isEqualTo(new Message("123"));
            messageRecieved.countDown();
            counter.incrementAndGet();
        });

        RedissonClient redisson2 = BaseTest.createInstance();
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

        redisson1.shutdown();
        redisson2.shutdown();
    }
    
    @Test
    public void testListenerRemove() throws InterruptedException {
        RedissonClient redisson1 = BaseTest.createInstance();
        RTopic topic1 = redisson1.getTopic("topic");
        int id = topic1.addListener(Message.class, (channel, msg) -> {
            Assertions.fail();
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic topic2 = redisson2.getTopic("topic");
        topic1.removeListener(id);
        topic2.publish(new Message("123"));

        Thread.sleep(1000);

        redisson1.shutdown();
        redisson2.shutdown();
    }

    @Test
    public void testReattach() throws Exception {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .run();
        
        Config config = new Config();
        config.useSingleServer().setAddress(runner.getRedisServerAddressAndPort());
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
        
        runner.stop();

        runner = new RedisRunner()
                .port(runner.getRedisServerPort())
                .nosave()
                .randomDir()
                .run();
        
        Thread.sleep(1000);

        redisson.getTopic("topic").publish(1);
        
        await().atMost(2, TimeUnit.SECONDS).untilTrue(executed);
        await().atMost(2, TimeUnit.SECONDS).until(() -> subscriptions.get() == 2);
        
        redisson.shutdown();
        runner.stop();
    }

    @Test
    public void testAddListenerFailover() throws Exception {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .run();

        Config config = new Config();
        config.useSingleServer()
                .setAddress(runner.getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        runner.stop();

        RTopic topic = redisson.getTopic("topic");
        Assertions.assertThrows(RedisException.class, () -> {
            topic.addListener(Integer.class, (channel, msg) -> {
            });
        });

        runner = new RedisRunner()
                .port(runner.getRedisServerPort())
                .nosave()
                .randomDir()
                .run();

        AtomicBoolean executed = new AtomicBoolean();
        topic.addListener(Integer.class, (channel, msg) -> {
            assertThat(msg).isEqualTo(1);
            executed.set(true);
        });

        redisson.getTopic("topic").publish(1);

        await().atMost(1, TimeUnit.SECONDS).untilTrue(executed);

        redisson.shutdown();
        runner.stop();
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
        RedisRunner.RedisProcess master = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6400)
                .run();

        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6400)
                .run();

        RedisRunner.RedisProcess sentinel1 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26379)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6400, 2)
                .sentinelDownAfterMilliseconds("myMaster", 750)
                .sentinelFailoverTimeout("myMaster", 1250)
                .run();

        RedisRunner.RedisProcess sentinel2 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26380)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6400, 2)
                .sentinelDownAfterMilliseconds("myMaster", 750)
                .sentinelFailoverTimeout("myMaster", 1250)
                .run();

        RedisRunner.RedisProcess sentinel3 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26381)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6400, 2)
                .sentinelDownAfterMilliseconds("myMaster", 750)
                .sentinelFailoverTimeout("myMaster", 1250)
                .run();

        Thread.sleep(1000);

        Config config = new Config();
        config.useSentinelServers()
                .addSentinelAddress(sentinel3.getRedisServerAddressAndPort()).setMasterName("myMaster")
                .setSubscriptionsPerConnection(20)
                .setSubscriptionConnectionPoolSize(200);

        RedissonClient redisson = Redisson.create(config);

        ScheduledExecutorService executor1 = Executors.newScheduledThreadPool(5);

        AtomicBoolean exceptionDetected = new AtomicBoolean(false);

        Deque<String> status = new ConcurrentLinkedDeque<>();
        Runnable rLockPayload =
                () -> {
                    try {
                        Integer randomLock = ThreadLocalRandom.current().nextInt(100);
                        RLock lock = redisson.getLock(randomLock.toString());
                        lock.lock(10, TimeUnit.SECONDS);
                        lock.unlock();
                        status.add("ok");
                    } catch (Exception e) {
                        status.add("failed");
                        if (e.getCause().getMessage().contains("slaves were synced")) {
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

        Thread.sleep(java.time.Duration.ofSeconds(10).toMillis());

        RedisClientConfig masterConfig = new RedisClientConfig().setAddress(master.getRedisServerAddressAndPort());

        //Failover from master to slave
        try {
            RedisClient.create(masterConfig).connect().sync(new RedisStrictCommand<Void>("DEBUG", "SEGFAULT"));
        } catch (RedisTimeoutException e) {
            // node goes down, so this command times out waiting for the response
        }

        //Re-introduce master as slave like kubernetes would do
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6380)
                .run();

        System.out.println("Failover Finished, start to see Subscribe timeouts now. Can't recover this without a refresh of redison client ");
        Thread.sleep(java.time.Duration.ofSeconds(10).toMillis());

        assertThat(exceptionDetected.get()).isFalse();
        assertThat(status.peekLast()).isEqualTo("ok");

        executor1.shutdown();

        redisson.shutdown();
        sentinel1.stop();
        sentinel2.stop();
        sentinel3.stop();
        master.stop();
        slave1.stop();
        slave2.stop();
    }

    
    @Test
    public void testReattachInSentinel() throws Exception {
        RedisRunner.RedisProcess master = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6399)
                .run();
        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6399)
                .run();
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6399)
                .run();
        RedisRunner.RedisProcess sentinel1 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26379)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6399, 2)
                .run();
        RedisRunner.RedisProcess sentinel2 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26380)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6399, 2)
                .run();
        RedisRunner.RedisProcess sentinel3 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26381)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6399, 2)
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
                System.out.println("onSubscribe " + (System.currentTimeMillis() - t));
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

        Config config = new Config();
        config.useClusterServers()
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

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

        redisson.shutdown();
        process.shutdown();
    }

    @Test
    public void testReattachInClusterSlave() throws Exception {
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

        Thread.sleep(1000);

        Config config = new Config();
        config.useClusterServers()
        .setSubscriptionMode(SubscriptionMode.SLAVE)
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
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
        
        process.getNodes().stream().filter(x -> Arrays.asList(slave1.getPort(), slave2.getPort(), slave3.getPort()).contains(x.getRedisServerPort()))
                        .forEach(x -> {
                            try {
                                x.stop();
                                Thread.sleep(18000);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }); 

        Thread.sleep(15000);

        redisson.getTopic("topic").publish(1);
        
        await().atMost(75, TimeUnit.SECONDS).until(() -> subscriptions.get() == 2);
        assertThat(topic.countListeners()).isEqualTo(2);
        assertThat(executed.get()).isTrue();
        
        redisson.shutdown();
        process.shutdown();
    }

    @Test
    public void testReattachInSentinel3() throws Exception {
        RedisRunner.RedisProcess master = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6400)
                .run();

        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6400)
                .run();

        RedisRunner.RedisProcess sentinel1 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26379)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6400, 2)
                .sentinelDownAfterMilliseconds("myMaster", 750)
                .sentinelFailoverTimeout("myMaster", 1250)
                .run();

        RedisRunner.RedisProcess sentinel2 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26380)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6400, 2)
                .sentinelDownAfterMilliseconds("myMaster", 750)
                .sentinelFailoverTimeout("myMaster", 1250)
                .run();

        RedisRunner.RedisProcess sentinel3 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26381)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6400, 2)
                .sentinelDownAfterMilliseconds("myMaster", 750)
                .sentinelFailoverTimeout("myMaster", 1250)
                .run();

        Thread.sleep(1000);

        Config config = new Config();
        config.useSentinelServers()
                .addSentinelAddress(sentinel3.getRedisServerAddressAndPort()).setMasterName("myMaster")
                .setSubscriptionsPerConnection(20)
                .setSubscriptionConnectionPoolSize(200);
        RedissonClient redisson = Redisson.create(config);

        ScheduledExecutorService executor1 = Executors.newScheduledThreadPool(5);

        AtomicBoolean exceptionDetected = new AtomicBoolean(false);

        Deque<String> status = new ConcurrentLinkedDeque<>();
        Runnable rLockPayload =
                () -> {
                    try {
                        Integer randomLock = ThreadLocalRandom.current().nextInt(100);
                        RLock lock = redisson.getLock(randomLock.toString());
                        lock.lock(10, TimeUnit.SECONDS);
                        lock.unlock();

                        RTopic t = redisson.getTopic("topic_" + randomLock);
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
                        if (e.getCause().getMessage().contains("slaves were synced")) {
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

        Thread.sleep(java.time.Duration.ofSeconds(10).toMillis());

        master.stop();

        Thread.sleep(TimeUnit.SECONDS.toMillis(30));

        assertThat(exceptionDetected.get()).isFalse();
        assertThat(status.peekLast()).isEqualTo("ok");

        executor1.shutdown();

        redisson.shutdown();
        sentinel1.stop();
        sentinel2.stop();
        sentinel3.stop();
        master.stop();
        slave1.stop();
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

        Thread.sleep(TimeUnit.SECONDS.toMillis(30));

        assertThat(subscriptions).hasSize(140);

        for (int i = 0; i < topicsAmount; i++) {
            RTopic topic = redisson.getTopic("topic" + i);
            topic.publish("topic" + i);
        }

        Thread.sleep(100);
        assertThat(messages).hasSize(topicsAmount);

        redisson.shutdown();
        process.shutdown();
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
