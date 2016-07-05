package org.redisson;

import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.core.BaseStatusListener;
import org.redisson.core.MessageListener;
import org.redisson.core.RBlockingQueue;
import org.redisson.core.RSet;
import org.redisson.core.RTopic;
import org.redisson.core.StatusListener;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;

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
                        RTopic<String> t = redisson.getTopic("PUBSUB_" + j);
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
        Assert.assertTrue(executor.awaitTermination(threads * loops * 1000, TimeUnit.SECONDS));

        for (Future<?> future : futures) {
            future.get();
        }
        
        redisson.shutdown();
    }


    @Test
    public void testCommandsOrdering() throws InterruptedException {
        RedissonClient redisson1 = BaseTest.createInstance();
        RTopic<Long> topic1 = redisson1.getTopic("topic", LongCodec.INSTANCE);
        AtomicBoolean stringMessageReceived = new AtomicBoolean();
        topic1.addListener((channel, msg) -> {
            assertThat(msg).isEqualTo(123);
            stringMessageReceived.set(true);
        });
        topic1.publish(123L);

        Awaitility.await().atMost(Duration.ONE_SECOND).untilTrue(stringMessageReceived);

        redisson1.shutdown();
    }

    @Test
    public void testTopicState() throws InterruptedException {
        RedissonClient redisson = BaseTest.createInstance();
        
        RTopic<String> stringTopic = redisson.getTopic("test1", StringCodec.INSTANCE);
        for (int i = 0; i < 3; i++) {
            AtomicBoolean stringMessageReceived = new AtomicBoolean();
            int listenerId = stringTopic.addListener(new MessageListener<String>() {
                @Override
                public void onMessage(String channel, String msg) {
                    assertThat(msg).isEqualTo("testmsg");
                    stringMessageReceived.set(true);
                }
            });
            stringTopic.publish("testmsg");
            
            Awaitility.await().atMost(Duration.ONE_SECOND).untilTrue(stringMessageReceived);
            
            stringTopic.removeListener(listenerId);
        }
        
        redisson.shutdown();
    }
    
    @Test
    public void testMultiTypeConnection() throws InterruptedException {
        RedissonClient redisson = BaseTest.createInstance();
        
        RTopic<String> stringTopic = redisson.getTopic("test1", StringCodec.INSTANCE);
        AtomicBoolean stringMessageReceived = new AtomicBoolean();
        stringTopic.addListener(new MessageListener<String>() {
            @Override
            public void onMessage(String channel, String msg) {
                assertThat(msg).isEqualTo("testmsg");
                stringMessageReceived.set(true);
            }
        });
        stringTopic.publish("testmsg");
        
        RTopic<Long> longTopic = redisson.getTopic("test2", LongCodec.INSTANCE);
        AtomicBoolean longMessageReceived = new AtomicBoolean();
        longTopic.addListener(new MessageListener<Long>() {

            @Override
            public void onMessage(String channel, Long msg) {
                assertThat(msg).isEqualTo(1L);
                longMessageReceived.set(true);
            }
        });
        longTopic.publish(1L);
        
        Awaitility.await().atMost(Duration.ONE_SECOND).untilTrue(stringMessageReceived);
        Awaitility.await().atMost(Duration.ONE_SECOND).untilTrue(longMessageReceived);
    }
    
    @Test
    public void testSyncCommands() throws InterruptedException {
        RedissonClient redisson = BaseTest.createInstance();
        RTopic<String> topic = redisson.getTopic("system_bus");
        RSet<String> redissonSet = redisson.getSet("set1");
        CountDownLatch latch = new CountDownLatch(1);
        topic.addListener((channel, msg) -> {
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
        final RTopic<Message> topic1 = redisson1.getTopic("topic1");
        final CountDownLatch messageRecieved = new CountDownLatch(3);
        int listenerId = topic1.addListener((channel, msg) -> {
            Assert.assertEquals(msg, new Message("test"));
            messageRecieved.countDown();
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        final RTopic<Message> topic2 = redisson2.getTopic("topic2");
        topic2.addListener((channel, msg) -> {
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
        final RTopic<Message> topic1 = redisson.getTopic("topic1");
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
        RTopic<Message> topic1 = redisson.getTopic("topic1");
        int listenerId = topic1.addListener((channel, msg) -> {
            Assert.fail();
        });
        topic1.addListener((channel, msg) -> {
            Assert.assertEquals("topic1", channel);
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
    public void testLazyUnsubscribe() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1);

        RedissonClient redisson1 = BaseTest.createInstance();
        RTopic<Message> topic1 = redisson1.getTopic("topic");
        int listenerId = topic1.addListener((channel, msg) -> {
            Assert.fail();
        });
        Thread.sleep(1000);
        topic1.removeListener(listenerId);
        Thread.sleep(1000);

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic<Message> topic2 = redisson2.getTopic("topic");
        topic2.addListener((channel, msg) -> {
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
        RTopic<Message> topic1 = redisson1.getTopic("topic");
        topic1.addListener((channel, msg) -> {
            Assert.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic<Message> topic2 = redisson2.getTopic("topic");
        topic2.addListener((channel, msg) -> {
            Assert.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });
        topic2.publish(new Message("123"));

        messageRecieved.await();

        redisson1.shutdown();
        redisson2.shutdown();
    }

    volatile long counter;

    @Test
    public void testHeavyLoad() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1000);

        RedissonClient redisson1 = BaseTest.createInstance();
        RTopic<Message> topic1 = redisson1.getTopic("topic");
        topic1.addListener((channel, msg) -> {
            Assert.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
            counter++;
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic<Message> topic2 = redisson2.getTopic("topic");
        topic2.addListener((channel, msg) -> {
            Assert.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });

        for (int i = 0; i < 5000; i++) {
            topic2.publish(new Message("123"));
        }

        messageRecieved.await();

        Thread.sleep(1000);

        Assert.assertEquals(5000, counter);

        redisson1.shutdown();
        redisson2.shutdown();
    }
    
    @Test
    public void testListenerRemove() throws InterruptedException {
        RedissonClient redisson1 = BaseTest.createInstance();
        RTopic<Message> topic1 = redisson1.getTopic("topic");
        int id = topic1.addListener((channel, msg) -> {
            Assert.fail();
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic<Message> topic2 = redisson2.getTopic("topic");
        topic1.removeListener(id);
        topic2.publish(new Message("123"));

        Thread.sleep(1000);

        redisson1.shutdown();
        redisson2.shutdown();
    }

    @Test
    public void testReattach() throws InterruptedException, IOException, ExecutionException, TimeoutException {
        RedisProcess runner = new RedisRunner()
                .port(6319)
                .nosave()
                .randomDir()
                .run();
        
        Config config = new Config();
        config.useSingleServer().setAddress("127.0.0.1:6319");
        RedissonClient redisson = Redisson.create(config);
        
        final AtomicBoolean executed = new AtomicBoolean();
        
        RTopic<Integer> topic = redisson.getTopic("topic");
        topic.addListener(new MessageListener<Integer>() {
            @Override
            public void onMessage(String channel, Integer msg) {
                if (msg == 1) {
                    executed.set(true);
                }
            }
        });
        
        runner.stop();

        runner = new RedisRunner()
                .port(6319)
                .nosave()
                .randomDir()
                .run();
        
        Thread.sleep(1000);

        redisson.getTopic("topic").publish(1);
        
        await().atMost(5, TimeUnit.SECONDS).untilTrue(executed);
        
        runner.stop();
    }

}
