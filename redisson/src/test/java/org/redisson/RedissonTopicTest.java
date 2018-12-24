package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.awaitility.Duration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.redisson.ClusterRunner.ClusterProcesses;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.api.RFuture;
import org.redisson.api.RPatternTopic;
import org.redisson.api.RSet;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.BaseStatusListener;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.listener.PatternMessageListener;
import org.redisson.api.listener.StatusListener;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SubscriptionMode;
import org.redisson.connection.balancer.RandomLoadBalancer;

public class RedissonTopicTest {

    @BeforeClass
    public static void beforeClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.startDefaultRedisServerInstance();
        }
    }

    @AfterClass
    public static void afterClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.shutDownDefaultRedisServerInstance();
        }
    }

    @Before
    public void before() throws IOException, InterruptedException {
        if (RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.startDefaultRedisServerInstance();
        }
    }

    @After
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
        topic1.removeListener(id);
        assertThat(topic1.countListeners()).isZero();

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
        Assert.assertTrue(executor.awaitTermination(100, TimeUnit.SECONDS));

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

        await().atMost(Duration.ONE_SECOND).untilTrue(stringMessageReceived);

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
            
            await().atMost(Duration.ONE_SECOND).until(() -> stringMessageReceived.get() == 2);
            
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
        
        await().atMost(Duration.ONE_SECOND).untilTrue(stringMessageReceived);
        await().atMost(Duration.ONE_SECOND).untilTrue(longMessageReceived);
    }
    
    @Test
    public void testSyncCommands() throws InterruptedException {
        RedissonClient redisson = BaseTest.createInstance();
        RTopic topic = redisson.getTopic("system_bus");
        RSet<String> redissonSet = redisson.getSet("set1");
        CountDownLatch latch = new CountDownLatch(1);
        topic.addListener(String.class, (channel, msg) -> {
            for (int j = 0; j < 1000; j++) {
                System.out.println("start: " + j);
                redissonSet.contains("" + j);
                System.out.println("end: " + j);
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
            Assert.assertEquals(msg, new Message("test"));
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

        Assert.assertTrue(messageRecieved.await(5, TimeUnit.SECONDS));

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
                Assert.assertEquals("topic1", channel);
                l.countDown();
            }
        });

        Thread.sleep(500);

        int listenerId2 = topic1.addListener(new BaseStatusListener() {
            @Override
            public void onUnsubscribe(String channel) {
                Assert.assertEquals("topic1", channel);
                l.countDown();
            }
        });
        topic1.removeListener(listenerId);
        topic1.removeListener(listenerId2);
        
        Assert.assertTrue(l.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testUnsubscribe() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1);

        RedissonClient redisson = BaseTest.createInstance();
        RTopic topic1 = redisson.getTopic("topic1");
        int listenerId = topic1.addListener(Message.class, (channel, msg) -> {
            Assert.fail();
        });
        topic1.addListener(Message.class, (channel, msg) -> {
            Assert.assertEquals("topic1", channel.toString());
            Assert.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });
        topic1.removeListener(listenerId);

        topic1 = redisson.getTopic("topic1");
        topic1.publish(new Message("123"));

        Assert.assertTrue(messageRecieved.await(5, TimeUnit.SECONDS));

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

        Thread.sleep(1000);
        assertThat(counter.get()).isZero();
        
        redisson.shutdown();
    }
    
    @Test
    public void testRemoveByInstance() throws InterruptedException {
        RedissonClient redisson = BaseTest.createInstance();
        RTopic topic1 = redisson.getTopic("topic1");
        MessageListener listener = new MessageListener() {
            @Override
            public void onMessage(CharSequence channel, Object msg) {
                Assert.fail();
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
            Assert.fail();
        });
        Thread.sleep(1000);
        topic1.removeListener(listenerId);
        Thread.sleep(1000);

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic topic2 = redisson2.getTopic("topic");
        topic2.addListener(Message.class, (channel, msg) -> {
            Assert.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });
        topic2.publish(new Message("123"));

        Assert.assertTrue(messageRecieved.await(5, TimeUnit.SECONDS));

        redisson1.shutdown();
        redisson2.shutdown();
    }

    @Test
    public void test() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(2);

        RedissonClient redisson1 = BaseTest.createInstance();
        RTopic topic1 = redisson1.getTopic("topic");
        topic1.addListener(Message.class, (channel, msg) -> {
            Assert.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic topic2 = redisson2.getTopic("topic");
        topic2.addListener(Message.class, (channel, msg) -> {
            Assert.assertEquals(new Message("123"), msg);
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
            Assert.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
            counter.incrementAndGet();
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic topic2 = redisson2.getTopic("topic");
        topic2.addListener(Message.class, (channel, msg) -> {
            Assert.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });

        int count = 10000;
        for (int i = 0; i < count; i++) {
            topic2.publish(new Message("123"));
        }

        messageRecieved.await();

        Thread.sleep(1000);

        Assert.assertEquals(count, counter.get());

        redisson1.shutdown();
        redisson2.shutdown();
    }
    
    @Test
    public void testListenerRemove() throws InterruptedException {
        RedissonClient redisson1 = BaseTest.createInstance();
        RTopic topic1 = redisson1.getTopic("topic");
        int id = topic1.addListener(Message.class, (channel, msg) -> {
            Assert.fail();
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
    
//    @Test
    public void testReattachInSentinelLong() throws Exception {
        for (int i = 0; i < 25; i++) {
            testReattachInSentinel();
        }
    }
    
//    @Test
    public void testReattachInClusterLong() throws Exception {
        for (int i = 0; i < 25; i++) {
            testReattachInCluster();
        }
    }
    
    @Test
    public void testReattachInSentinel() throws Exception {
        RedisRunner.RedisProcess master = new RedisRunner()
                .nosave()
                .randomDir()
                .run();
        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess sentinel1 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26379)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();
        RedisRunner.RedisProcess sentinel2 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26380)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();
        RedisRunner.RedisProcess sentinel3 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26381)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
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
        
        sendCommands(redisson);
        
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
        Assert.assertTrue(executed.get());
        
        redisson.shutdown();
        sentinel1.stop();
        sentinel2.stop();
        sentinel3.stop();
        master.stop();
        slave1.stop();
        slave2.stop();
    }

    protected void sendCommands(RedissonClient redisson) {
        Thread t = new Thread() {
            public void run() {
                List<RFuture<?>> futures = new ArrayList<RFuture<?>>();
                
                for (int i = 0; i < 100; i++) {
                    RFuture<?> f1 = redisson.getBucket("i" + i).getAsync();
                    RFuture<?> f2 = redisson.getBucket("i" + i).setAsync("");
                    RFuture<?> f3 = redisson.getTopic("topic").publishAsync(1);
                    futures.add(f1);
                    futures.add(f2);
                    futures.add(f3);
                }
                
                for (RFuture<?> rFuture : futures) {
                    rFuture.awaitUninterruptibly();
                }
            };
        };
        t.start();
    }
    
    @Test
    public void testReattachInCluster() throws Exception {
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
        
        sendCommands(redisson);
        
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
        Assert.assertTrue(executed.get());
        
        redisson.shutdown();
        process.shutdown();
    }


}
