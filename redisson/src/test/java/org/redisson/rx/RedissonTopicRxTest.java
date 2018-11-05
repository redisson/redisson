package org.redisson.rx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.api.RTopicRx;

import io.reactivex.Flowable;

public class RedissonTopicRxTest extends BaseRxTest {

    @Test
    public void testLong() throws InterruptedException {
        RTopicRx topic = redisson.getTopic("test");
        Flowable<String> messages = topic.getMessages(String.class);
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
