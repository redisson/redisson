package org.redisson;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.BasePatternStatusListener;
import org.redisson.core.MessageListener;
import org.redisson.core.PatternMessageListener;
import org.redisson.core.RPatternTopic;
import org.redisson.core.RTopic;

public class RedissonTopicPatternTest {

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

        Redisson redisson = BaseTest.createInstance();
        RPatternTopic<Message> topic1 = redisson.getPatternTopic("topic1.*");
        int listenerId = topic1.addListener(new PatternMessageListener<Message>() {
            @Override
            public void onMessage(String pattern, String channel, Message msg) {
                Assert.fail();
            }
        });
        topic1.addListener(new PatternMessageListener<Message>() {
            @Override
            public void onMessage(String pattern, String channel, Message msg) {
                Assert.assertEquals("topic1.*", pattern);
                Assert.assertEquals("topic1.t3", channel);
                Assert.assertEquals(new Message("123"), msg);
                messageRecieved.countDown();
            }
        });
        topic1.removeListener(listenerId);

//        topic1 = redisson.getPatternTopic("topic1.*");
        redisson.getTopic("topic1.t3").publish(new Message("123"));

        messageRecieved.await();

        redisson.shutdown();
    }

    @Test
    public void testLazyUnsubscribe() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1);

        Redisson redisson1 = BaseTest.createInstance();
        RPatternTopic<Message> topic1 = redisson1.getPatternTopic("topic.*");
        int listenerId = topic1.addListener(new PatternMessageListener<Message>() {
            @Override
            public void onMessage(String pattern, String channel, Message msg) {
                Assert.fail();
            }
        });

        Thread.sleep(1000);
        topic1.removeListener(listenerId);
        Thread.sleep(1000);

        Redisson redisson2 = BaseTest.createInstance();
        RPatternTopic<Message> topic2 = redisson2.getPatternTopic("topic.*");
        topic2.addListener(new PatternMessageListener<Message>() {
            @Override
            public void onMessage(String pattern, String channel, Message msg) {
                Assert.assertEquals("topic.*", pattern);
                Assert.assertEquals("topic.t1", channel);
                Assert.assertEquals(new Message("123"), msg);
                messageRecieved.countDown();
            }
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
        Redisson redisson1 = BaseTest.createInstance();
        RPatternTopic<Message> topic1 = redisson1.getPatternTopic("topic.*");
        topic1.addListener(new BasePatternStatusListener() {
            @Override
            public void onPSubscribe(String pattern) {
                Assert.assertEquals("topic.*", pattern);
                statusRecieved.countDown();
            }
        });
        topic1.addListener(new PatternMessageListener<Message>() {
            @Override
            public void onMessage(String pattern, String channel, Message msg) {
                Assert.assertEquals(new Message("123"), msg);
                messageRecieved.countDown();
            }
        });

        Redisson redisson2 = BaseTest.createInstance();
        RTopic<Message> topic2 = redisson2.getTopic("topic.t1");
        topic2.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.assertEquals(new Message("123"), msg);
                messageRecieved.countDown();
            }
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
        Redisson redisson1 = BaseTest.createInstance();
        RPatternTopic<Message> topic1 = redisson1.getPatternTopic("topic.*");
        final CountDownLatch l = new CountDownLatch(1);
        topic1.addListener(new BasePatternStatusListener() {
            @Override
            public void onPUnsubscribe(String pattern) {
                Assert.assertEquals("topic.*", pattern);
                l.countDown();
            }
        });
        int id = topic1.addListener(new PatternMessageListener<Message>() {
            @Override
            public void onMessage(String pattern, String channel, Message msg) {
                Assert.fail();
            }
        });

        Redisson redisson2 = BaseTest.createInstance();
        RTopic<Message> topic2 = redisson2.getTopic("topic.t1");
        topic1.removeListener(id);
        topic2.publish(new Message("123"));

        Thread.sleep(1000);

        redisson1.shutdown();
        redisson2.shutdown();
    }

}
