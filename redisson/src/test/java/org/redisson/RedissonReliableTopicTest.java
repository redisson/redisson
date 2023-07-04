package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.redisson.api.RReliableTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonReliableTopicTest extends BaseTest {

    @Test
    public void testConcurrency() throws InterruptedException {
        RReliableTopic rt = redisson.getReliableTopic("test1");

        AtomicInteger sent = new AtomicInteger();
        ExecutorService ee = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 500; i++) {
            int j = i;
            ee.submit(() -> {
                rt.publish(j);
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextInt(10));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                sent.incrementAndGet();
            });
        }

        AtomicInteger ii = new AtomicInteger();
        rt.addListener(Integer.class, new MessageListener<Integer>() {
            @Override
            public void onMessage(CharSequence channel, Integer msg) {
                ii.incrementAndGet();
            }
        });


        ee.shutdown();
        assertThat(ee.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(sent.get()).isEqualTo(500);
        assertThat(ii.get()).isEqualTo(500);
        rt.removeAllListeners();
    }

    @Test
    public void testRemoveExpiredSubscribers() throws InterruptedException {
        RReliableTopic rt = redisson.getReliableTopic("test1");
        AtomicInteger counter = new AtomicInteger();
        rt.addListener(Integer.class, (ch, m) -> {
            counter.incrementAndGet();
        });

        Config config = new Config();
        config.setReliableTopicWatchdogTimeout(1000);
        config.useSingleServer()
                .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());
        RedissonClient secondInstance = Redisson.create(config);
        RReliableTopic rt2 = secondInstance.getReliableTopic("test1");
        rt2.addListener(Integer.class, (ch, m) -> {
            counter.incrementAndGet();
        });

        assertThat(rt2.countSubscribers()).isEqualTo(2);

        secondInstance.shutdown();

        Thread.sleep(1500);

        for (int i = 0; i < 10; i++) {
            rt.publish(i);
        }

        Thread.sleep(100);

        assertThat(rt.countSubscribers()).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(10);
        Thread.sleep(1000);
        assertThat(rt.size()).isEqualTo(0);
    }

    @Test
    public void testAutoTrim() throws InterruptedException {
        RReliableTopic rt = redisson.getReliableTopic("test1");
        AtomicInteger counter = new AtomicInteger();
        rt.addListener(Integer.class, (ch, m) -> {
            counter.incrementAndGet();
        });
        RReliableTopic rt2 = redisson.getReliableTopic("test1");
        rt2.addListener(Integer.class, (ch, m) -> {
            counter.incrementAndGet();
        });

        for (int i = 0; i < 10; i++) {
            assertThat(rt.publish(i)).isEqualTo(2);
        }

        Awaitility.waitAtMost(Duration.ofSeconds(2)).until(() -> counter.get() == 20);
        Thread.sleep(1000);
        assertThat(rt.size()).isEqualTo(0);
    }

    @Test
    public void testListenerOldMessages() throws InterruptedException {
        RReliableTopic rt = redisson.getReliableTopic("test2");

        rt.publish("1");

        Queue<String> messages = new LinkedList<>();
        String id = rt.addListener(String.class, (ch, m) -> {
            messages.add(m);
        });

        assertThat(messages).containsOnly("1");
        rt.publish("2");
        Thread.sleep(50);
        assertThat(messages).containsOnly("1", "2");

        messages.clear();
        rt.removeListener(id);

        String id2 = rt.addListener(String.class, (ch, m) -> {
            messages.add(m);
        });

        Thread.sleep(50);
        assertThat(messages).isEmpty();

    }

    @Test
    public void testReattach() throws InterruptedException {
        RReliableTopic rt = redisson.getReliableTopic("test2");
        AtomicInteger i = new AtomicInteger();
        String id = rt.addListener(String.class, (ch, m) -> {
            i.incrementAndGet();
        });

        rt.publish("1");
        Thread.sleep(5);
        assertThat(i).hasValue(1);
        rt.removeListener(id);

        assertThat(rt.publish("2")).isEqualTo(0);

        String id2 = rt.addListener(String.class, (ch, m) -> {
            i.incrementAndGet();
        });

        assertThat(rt.publish("3")).isEqualTo(1);
        Thread.sleep(5);
        assertThat(i).hasValue(3);
    }

    @Test
    public void testListener() throws InterruptedException {
        RReliableTopic rt = redisson.getReliableTopic("test2");
        AtomicInteger i = new AtomicInteger();
        String id = rt.addListener(String.class, (ch, m) -> {
            i.incrementAndGet();
        });

        rt.publish("1");
        Thread.sleep(5);
        assertThat(i).hasValue(1);
        rt.removeListener(id);

        assertThat(rt.publish("2")).isEqualTo(0);
        assertThat(i).hasValue(1);
    }

    @Test
    public void test() throws InterruptedException {
        RReliableTopic rt = redisson.getReliableTopic("test3");
        CountDownLatch a = new CountDownLatch(1);
        CountDownLatch twoMessages = new CountDownLatch(3);
        rt.addListener(String.class, (ch, m) -> {
            assertThat(m).isIn("m1", "m2");
            a.countDown();
            twoMessages.countDown();
        });

        assertThat(rt.publish("m1")).isEqualTo(1);
        assertThat(a.await(1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(200);
        assertThat(rt.size()).isEqualTo(0);

        RReliableTopic rt2 = redisson.getReliableTopic("test3");
        rt2.addListener(String.class, (ch, m) -> {
            assertThat(m).isEqualTo("m2");
            twoMessages.countDown();
        });

        assertThat(rt.publish("m2")).isEqualTo(2);
        assertThat(twoMessages.await(1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(5);
        assertThat(rt.size()).isEqualTo(0);
    }

}
