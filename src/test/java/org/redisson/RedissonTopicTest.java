package org.redisson;

import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.MessageListener;
import org.redisson.core.RTopic;

public class RedissonTopicTest {

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

    }

    @Test
    public void test() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(2);

        Redisson redisson1 = Redisson.create();
        RTopic<Message> topic1 = redisson1.getTopic("topic");
        topic1.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(Message msg) {
                Assert.assertEquals(new Message("123"), msg);
                messageRecieved.countDown();
            }
        });

        Redisson redisson2 = Redisson.create();
        RTopic<Message> topic2 = redisson2.getTopic("topic");
        topic2.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(Message msg) {
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
    public void testListenerRemove() throws InterruptedException {
        Redisson redisson1 = Redisson.create();
        RTopic<Message> topic1 = redisson1.getTopic("topic");
        int id = topic1.addListener(new MessageListener<Message>() {
            @Override
            public void onMessage(Message msg) {
                Assert.fail();
            }
        });

        Redisson redisson2 = Redisson.create();
        RTopic<Message> topic2 = redisson2.getTopic("topic");
        topic1.removeListener(id);
        topic2.publish(new Message("123"));

        Thread.sleep(1000);

        redisson1.shutdown();
        redisson2.shutdown();
    }


}
