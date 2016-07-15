package org.redisson;

import static com.jayway.awaitility.Awaitility.await;

import java.io.IOException;
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
import org.redisson.core.BasePatternStatusListener;
import org.redisson.core.MessageListener;
import org.redisson.core.PatternMessageListener;
import org.redisson.core.PatternStatusListener;
import org.redisson.core.RPatternTopic;
import org.redisson.core.RTopic;
import org.redisson.core.StatusListener;

public class RedissonTopicPatternTest {

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

    public static class Message {

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
    public void testUnsubscribe() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1);

        RedissonClient redisson = BaseTest.createInstance();
        RPatternTopic<Message> topic1 = redisson.getPatternTopic("topic1.*");
        int listenerId = topic1.addListener((pattern, channel, msg) -> {
            Assert.fail();
        });
        topic1.addListener((pattern, channel, msg) -> {
            Assert.assertEquals("topic1.*", pattern);
            Assert.assertEquals("topic1.t3", channel);
            Assert.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });
        topic1.removeListener(listenerId);

//        topic1 = redisson.getPatternTopic("topic1.*");
        redisson.getTopic("topic1.t3").publish(new Message("123"));

        Assert.assertTrue(messageRecieved.await(5, TimeUnit.SECONDS));

        redisson.shutdown();
    }

    @Test
    public void testLazyUnsubscribe() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1);

        RedissonClient redisson1 = BaseTest.createInstance();
        RPatternTopic<Message> topic1 = redisson1.getPatternTopic("topic.*");
        int listenerId = topic1.addListener((pattern, channel, msg) -> {
            Assert.fail();
        });

        Thread.sleep(1000);
        topic1.removeListener(listenerId);
        Thread.sleep(1000);

        RedissonClient redisson2 = BaseTest.createInstance();
        RPatternTopic<Message> topic2 = redisson2.getPatternTopic("topic.*");
        topic2.addListener((pattern, channel, msg) -> {
            Assert.assertEquals("topic.*", pattern);
            Assert.assertEquals("topic.t1", channel);
            Assert.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });

        RTopic<Message> topic3 = redisson2.getTopic("topic.t1");
        topic3.publish(new Message("123"));

        messageRecieved.await();

        redisson1.shutdown();
        redisson2.shutdown();
    }

    @Test
    public void test() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(5);

        final CountDownLatch statusRecieved = new CountDownLatch(1);
        RedissonClient redisson1 = BaseTest.createInstance();
        RPatternTopic<Message> topic1 = redisson1.getPatternTopic("topic.*");
        topic1.addListener(new BasePatternStatusListener() {
            @Override
            public void onPSubscribe(String pattern) {
                Assert.assertEquals("topic.*", pattern);
                statusRecieved.countDown();
            }
        });
        topic1.addListener((pattern, channel, msg) -> {
            Assert.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic<Message> topic2 = redisson2.getTopic("topic.t1");
        topic2.addListener((channel, msg) -> {
            Assert.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });
        topic2.publish(new Message("123"));
        topic2.publish(new Message("123"));

        RTopic<Message> topicz = redisson2.getTopic("topicz.t1");
        topicz.publish(new Message("789")); // this message doesn't get
                                            // delivered, and would fail the
                                            // assertion

        RTopic<Message> topict2 = redisson2.getTopic("topic.t2");
        topict2.publish(new Message("123"));

        statusRecieved.await();
        Assert.assertTrue(messageRecieved.await(5, TimeUnit.SECONDS));

        redisson1.shutdown();
        redisson2.shutdown();
    }

    @Test
    public void testListenerRemove() throws InterruptedException {
        RedissonClient redisson1 = BaseTest.createInstance();
        RPatternTopic<Message> topic1 = redisson1.getPatternTopic("topic.*");
        final CountDownLatch l = new CountDownLatch(1);
        topic1.addListener(new BasePatternStatusListener() {
            @Override
            public void onPUnsubscribe(String pattern) {
                Assert.assertEquals("topic.*", pattern);
                l.countDown();
            }
        });
        int id = topic1.addListener((pattern, channel, msg) -> {
            Assert.fail();
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic<Message> topic2 = redisson2.getTopic("topic.t1");
        topic1.removeListener(id);
        topic2.publish(new Message("123"));

        redisson1.shutdown();
        redisson2.shutdown();
    }

    @Test
    public void testConcurrentTopic() throws Exception {
        Config config = BaseTest.createConfig();
        RedissonClient redisson = Redisson.create(config);
        
        int threads = 30;
        int loops = 50000;
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>(); 
        for (int i = 0; i < threads; i++) {

            Runnable worker = new Runnable() {

                @Override
                public void run() {
                    for (int j = 0; j < loops; j++) {
                        RPatternTopic<String> t = redisson.getPatternTopic("PUBSUB*");
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
        Assert.assertTrue(executor.awaitTermination(threads * loops * 1000, TimeUnit.SECONDS));

        for (Future<?> future : futures) {
            future.get();
        }
        
        redisson.shutdown();
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
        
        RPatternTopic<Integer> topic = redisson.getPatternTopic("topic*");
        topic.addListener(new PatternMessageListener<Integer>() {
            @Override
            public void onMessage(String pattern, String channel, Integer msg) {
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

        redisson.getTopic("topic1").publish(1);
        
        await().atMost(5, TimeUnit.SECONDS).untilTrue(executed);
        
        runner.stop();
    }
    
}
