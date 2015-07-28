package org.redisson;

import java.io.Serializable;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.core.BaseStatusListener;
import org.redisson.core.MessageListener;
import org.redisson.core.RTopic;
import org.redisson.core.StatusListener;

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
    public void testInnerPublish() throws InterruptedException {

        Redisson redisson1 = BaseTest.createInstance();
        final RTopic<Message> topic1 = redisson1.getTopic("topic1");
        final CountDownLatch messageRecieved = new CountDownLatch(3);
        int listenerId = topic1.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.assertEquals(msg, new Message("test"));
                messageRecieved.countDown();
            }
        });

        Redisson redisson2 = BaseTest.createInstance();
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

        messageRecieved.await();

        redisson1.shutdown();
        redisson2.shutdown();
    }

    @Test
    public void testStatus() throws InterruptedException {
        Redisson redisson = BaseTest.createInstance();
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
        l.await();
    }

    @Test
    public void testUnsubscribe() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1);

        Redisson redisson = BaseTest.createInstance();
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

        messageRecieved.await();

        redisson.shutdown();
    }


    @Test
    public void testLazyUnsubscribe() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1);

        Redisson redisson1 = BaseTest.createInstance();
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

        Redisson redisson2 = BaseTest.createInstance();
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


    @Test
    public void test() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(2);

        Redisson redisson1 = BaseTest.createInstance();
        RTopic<Message> topic1 = redisson1.getTopic("topic");
        topic1.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.assertEquals(new Message("123"), msg);
                messageRecieved.countDown();
            }
        });

        Redisson redisson2 = BaseTest.createInstance();
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

    @Test
    public void testHeavyLoad() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1000);

        Redisson redisson1 = BaseTest.createInstance();
        RTopic<Message> topic1 = redisson1.getTopic("topic");
        topic1.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.assertEquals(new Message("123"), msg);
                messageRecieved.countDown();
            }
        });

        Redisson redisson2 = BaseTest.createInstance();
        RTopic<Message> topic2 = redisson2.getTopic("topic");
        topic2.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.assertEquals(new Message("123"), msg);
                messageRecieved.countDown();
            }
        });

        for (int i = 0; i < 500; i++) {
            topic2.publish(new Message("123"));
        }

        messageRecieved.await();

        redisson1.shutdown();
        redisson2.shutdown();
    }


    @Test
    public void testListenerRemove() throws InterruptedException {
        Redisson redisson1 = BaseTest.createInstance();
        RTopic<Message> topic1 = redisson1.getTopic("topic");
        int id = topic1.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(String channel, Message msg) {
                Assert.fail();
            }
        });

        Redisson redisson2 = BaseTest.createInstance();
        RTopic<Message> topic2 = redisson2.getTopic("topic");
        topic1.removeListener(id);
        topic2.publish(new Message("123"));

        Thread.sleep(1000);

        redisson1.shutdown();
        redisson2.shutdown();
    }


}
