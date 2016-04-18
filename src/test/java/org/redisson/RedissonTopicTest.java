package org.redisson;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.BaseStatusListener;
import org.redisson.core.MessageListener;
import org.redisson.core.RSet;
import org.redisson.core.RTopic;

public class RedissonTopicTest {

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
    public void testSyncCommands() throws InterruptedException {
        RedissonClient redisson = BaseTest.createInstance();
        RTopic<String> topic = redisson.getTopic("system_bus");
        RSet<String> redissonSet = redisson.getSet("set1");
        CountDownLatch latch = new CountDownLatch(1);
        topic.addListener(new MessageListener<String>() {
            
            @Override
            public void onMessage(String channel, String msg) {
                for (int j = 0; j < 1000; j++) {
                    redissonSet.contains("" + j);
                }
                latch.countDown();
            }
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
        int listenerId = topic1.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.assertEquals(msg, new Message("test"));
                messageRecieved.countDown();
            }
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        final RTopic<Message> topic2 = redisson2.getTopic("topic2");
        topic2.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                messageRecieved.countDown();
                Message m = new Message("test");
                if (!msg.equals(m)) {
                    topic1.publish(m);
                    topic2.publish(m);
                }
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
        int listenerId = topic1.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.fail();
            }
        });
        topic1.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.assertEquals("topic1", channel);
                Assert.assertEquals(new Message("123"), msg);
                messageRecieved.countDown();
            }
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
        int listenerId = topic1.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.fail();
            }
        });
        Thread.sleep(1000);
        topic1.removeListener(listenerId);
        Thread.sleep(1000);

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic<Message> topic2 = redisson2.getTopic("topic");
        topic2.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.assertEquals(new Message("123"), msg);
                messageRecieved.countDown();
            }
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
        topic1.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.assertEquals(new Message("123"), msg);
                messageRecieved.countDown();
            }
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic<Message> topic2 = redisson2.getTopic("topic");
        topic2.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.assertEquals(new Message("123"), msg);
                messageRecieved.countDown();
            }
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
        topic1.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.assertEquals(new Message("123"), msg);
                messageRecieved.countDown();
                counter++;
            }
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic<Message> topic2 = redisson2.getTopic("topic");
        topic2.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.assertEquals(new Message("123"), msg);
                messageRecieved.countDown();
            }
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
        int id = topic1.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.fail();
            }
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic<Message> topic2 = redisson2.getTopic("topic");
        topic1.removeListener(id);
        topic2.publish(new Message("123"));

        Thread.sleep(1000);

        redisson1.shutdown();
        redisson2.shutdown();
    }


}
