package org.redisson;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.MessageListener;
import org.redisson.core.RTopic;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
      return "Message{" +
        "name='" + name + '\'' +
        '}';
    }
  }

  @Test
  public void testUnsubscribe() throws InterruptedException {
    final CountDownLatch messageRecieved = new CountDownLatch(1);

    Redisson redisson = Redisson.create();
    RTopic<Message> topic1 = redisson.getTopic("topic1.*");
    int listenerId = topic1.addPListener(new MessageListener<Message>() {
      @Override
      public void onMessage(Message msg) {
        Assert.fail();
      }
    });
    topic1.addPListener(new MessageListener<Message>() {
      @Override
      public void onMessage(Message msg) {
        Assert.assertEquals(new Message("123"), msg);
        messageRecieved.countDown();
      }
    });
    topic1.removePListener(listenerId);

    topic1 = redisson.getTopic("topic1.*");
    topic1.publish(new Message("123"));

    messageRecieved.await();

    redisson.shutdown();
  }


  @Test
  public void testLazyUnsubscribe() throws InterruptedException {
    final CountDownLatch messageRecieved = new CountDownLatch(1);

    Redisson redisson1 = Redisson.create();
    RTopic<Message> topic1 = redisson1.getTopic("topic.*");
    int listenerId = topic1.addPListener(new MessageListener<Message>() {
      @Override
      public void onMessage(Message msg) {
        Assert.fail();
      }
    });

    Thread.sleep(1000);
    topic1.removePListener(listenerId);
    Thread.sleep(1000);

    Redisson redisson2 = Redisson.create();
    RTopic<Message> topic2 = redisson2.getTopic("topic.*");
    topic2.addPListener(new MessageListener<Message>() {
      @Override
      public void onMessage(Message msg) {
        Assert.assertEquals(new Message("123"), msg);
        messageRecieved.countDown();
      }
    });

    RTopic<Message> topic3 = redisson2.getTopic("topic.t3");

    topic3.publish(new Message("123"));

    messageRecieved.await();

    redisson1.shutdown();
    redisson2.shutdown();
  }


  @Test
  public void test() throws InterruptedException {
    final CountDownLatch messageRecieved = new CountDownLatch(5);

    Redisson redisson1 = Redisson.create();
    RTopic<Message> topic1 = redisson1.getTopic("topic.*");
    topic1.addPListener(new MessageListener<Message>() {
      @Override
      public void onMessage(Message msg) {
        Assert.assertEquals(new Message("123"), msg);
        messageRecieved.countDown();
      }
    });

    Redisson redisson2 = Redisson.create();
    RTopic<Message> topic2 = redisson2.getTopic("topic.t1");
    topic2.addListener(new MessageListener<Message>() {
      @Override
      public void onMessage(Message msg) {
        Assert.assertEquals(new Message("123"), msg);
        messageRecieved.countDown();
      }
    });
    topic2.publish(new Message("123"));
    topic2.publish(new Message("123"));

    RTopic<Message> topicz = redisson2.getTopic("topicz.t1");
    topicz.publish(new Message("789")); // this message doesn't get delivered, and would fail the assertion

    RTopic<Message> topict2 = redisson2.getTopic("topic.t2");
    topict2.publish(new Message("123"));

    Assert.assertTrue(messageRecieved.await(5, TimeUnit.SECONDS));

    redisson1.shutdown();
    redisson2.shutdown();
  }

  @Test
  public void testListenerRemove() throws InterruptedException {
    Redisson redisson1 = Redisson.create();
    RTopic<Message> topic1 = redisson1.getTopic("topic.*");
    int id = topic1.addPListener(new MessageListener<Message>() {
      @Override
      public void onMessage(Message msg) {
        Assert.fail();
      }
    });

    Redisson redisson2 = Redisson.create();
    RTopic<Message> topic2 = redisson2.getTopic("topic.t1");
    topic1.removePListener(id);
    topic2.publish(new Message("123"));

    Thread.sleep(1000);

    redisson1.shutdown();
    redisson2.shutdown();
  }


}
