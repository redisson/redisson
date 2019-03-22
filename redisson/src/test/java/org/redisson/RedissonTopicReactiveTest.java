package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.api.RTopicReactive;
import org.redisson.api.listener.MessageListener;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RedissonTopicReactiveTest extends BaseReactiveTest {

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
    public void testRemoveListenerById() throws InterruptedException {
        RTopicReactive topic1 = redisson.getTopic("topic1");
        MessageListener listener = new MessageListener() {
            @Override
            public void onMessage(CharSequence channel, Object msg) {
                Assert.fail();
            }
        };
        
        Mono<Integer> res = topic1.addListener(Message.class, listener);
        Integer listenerId = res.block();

        topic1 = redisson.getTopic("topic1");
        topic1.removeListener(listenerId);
        topic1.publish(new Message("123"));
    }
    
    @Test
    public void testRemoveListenerByInstance() throws InterruptedException {
        RTopicReactive topic1 = redisson.getTopic("topic1");
        MessageListener listener = new MessageListener() {
            @Override
            public void onMessage(CharSequence channel, Object msg) {
                Assert.fail();
            }
        };
        
        topic1.addListener(Message.class, listener).block();

        topic1 = redisson.getTopic("topic1");
        topic1.removeListener(listener);
        topic1.publish(new Message("123"));
    }
    
    @Test
    public void testLong() throws InterruptedException {
        RTopicReactive topic = redisson.getTopic("test");
        Flux<String> messages = topic.getMessages(String.class);
        List<String> list = new ArrayList<>();
        messages.subscribe(new Subscriber<String>() {

            @Override
            public void onSubscribe(Subscription s) {
                s.request(10);
            }

            @Override
            public void onNext(String t) {
                list.add(t);
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });
        
        for (int i = 0; i < 15; i++) {
            sync(topic.publish("" + i));
        }
        
        assertThat(list).containsExactly("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
    }
}
