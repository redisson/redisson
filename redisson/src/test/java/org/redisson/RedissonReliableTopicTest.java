package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.redisson.api.RReliableTopic;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.stream.StreamGroup;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonReliableTopicTest extends RedisDockerTest {

    @Test
    public void testRemoveAllListenersOnNotSubscribedTopic() {
        RReliableTopic topic = redisson.getReliableTopic("testRemoveAllFresh");

        // no listener was ever added, so no subscription exists - removing
        // listeners must be a no-op instead of throwing NullPointerException
        assertThatNoException().isThrownBy(topic::removeAllListeners);
    }

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
        rt.addListener(Integer.class, (channel, msg) -> ii.incrementAndGet());


        ee.shutdown();
        assertThat(ee.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        Thread.sleep(1000);
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

        Config config =  createConfig();
        config.setReliableTopicWatchdogTimeout(1000);
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
        Thread.sleep(2000);
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
    }

    @Test
    public void testListenerOldMessages() throws InterruptedException {
        RReliableTopic rt = redisson.getReliableTopic("test2");

        rt.publish("1");

        Queue<String> messages = new ArrayDeque<>();
        String id = rt.addListener(String.class, (ch, m) -> {
            messages.add(m);
        });

        Thread.sleep(50);
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
        Thread.sleep(50);
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
    public void testListenerFailureDoesNotStopPolling() {
        RReliableTopic topic = redisson.getReliableTopic("testListenerFailure");
        AtomicInteger attempts = new AtomicInteger();
        Queue<String> messages = new ConcurrentLinkedQueue<>();
        topic.addListener(String.class, (channel, message) -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("listener failure");
            }
            messages.add(message);
        });

        topic.publish("first");
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(messages).containsExactly("first"));

        assertThat(topic.countSubscribers()).isOne();
        assertThat(topic.publish("second")).isOne();
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(messages).containsExactly("first", "second"));
        assertThat(attempts).hasValue(3);
    }

    @Test
    public void testTopicRemovalWhileSubscribed() throws InterruptedException {
        RReliableTopic rt = redisson.getReliableTopic("testTopicRemovalWhileSubscribed");
        Queue<Integer> messages = new ConcurrentLinkedQueue<>();
        rt.addListener(Integer.class, (channel, msg) -> messages.add(msg));

        assertThat(rt.publish(1)).isEqualTo(1);
        Awaitility.waitAtMost(Duration.ofSeconds(2)).until(() -> messages.size() == 1);

        // topic key is removed while the subscriber is live, e.g. expired by TTL
        redisson.getKeys().delete("testTopicRemovalWhileSubscribed");
        // no group exists on the recreated stream, so the message is undeliverable
        assertThat(rt.publish(9)).isEqualTo(0);

        // the dead subscriber is cleaned up: its timeout ZSET entry is removed
        RScoredSortedSet<String> timeout = redisson.getScoredSortedSet("{testTopicRemovalWhileSubscribed}:timeout");
        Awaitility.waitAtMost(Duration.ofSeconds(5)).until(() -> timeout.size() == 0);

        // a new listener can subscribe through the same topic object and receives new messages
        rt.addListener(Integer.class, (channel, msg) -> messages.add(msg));

        assertThat(rt.publish(2)).isEqualTo(1);
        Awaitility.waitAtMost(Duration.ofSeconds(2)).until(() -> messages.contains(2));
    }

    @Test
    public void testGroupRemovalWhileSubscribed() throws InterruptedException {
        RReliableTopic rt = redisson.getReliableTopic("testGroupRemovalWhileSubscribed");
        Queue<Integer> messages = new ConcurrentLinkedQueue<>();
        rt.addListener(Integer.class, (channel, msg) -> messages.add(msg));

        assertThat(rt.publish(1)).isEqualTo(1);
        Awaitility.waitAtMost(Duration.ofSeconds(2)).until(() -> messages.size() == 1);

        // the consumer group is removed while the subscriber is live,
        // e.g. by the expired-subscriber sweep of another instance
        RStream<String, Integer> stream = redisson.getStream("testGroupRemovalWhileSubscribed");
        String groupName = stream.listGroups().get(0).getName();
        stream.removeGroup(groupName);
        assertThat(rt.publish(9)).isEqualTo(0);

        RScoredSortedSet<String> timeout = redisson.getScoredSortedSet("{testGroupRemovalWhileSubscribed}:timeout");
        Awaitility.waitAtMost(Duration.ofSeconds(5)).until(() -> timeout.size() == 0);

        rt.addListener(Integer.class, (channel, msg) -> messages.add(msg));

        assertThat(rt.publish(2)).isEqualTo(1);
        Awaitility.waitAtMost(Duration.ofSeconds(2)).until(() -> messages.contains(2));
    }

    @Test
    public void testTransientUpdateFailureDoesNotStopPolling() throws InterruptedException {
        RReliableTopic rt = redisson.getReliableTopic("testTransientUpdateFailure");
        Queue<Integer> received = new ConcurrentLinkedQueue<>();
        rt.addListener(Integer.class, (channel, msg) -> received.add(msg));

        // capture the registration entry, then break the status update once
        RScoredSortedSet<String> timeout = redisson.getScoredSortedSet("{testTransientUpdateFailure}:timeout", StringCodec.INSTANCE);
        Awaitility.waitAtMost(Duration.ofSeconds(2)).until(() -> timeout.size() == 1);
        String member = timeout.readAll().iterator().next();
        double score = timeout.getScore(member);

        redisson.getKeys().delete("{testTransientUpdateFailure}:timeout");
        redisson.getBucket("{testTransientUpdateFailure}:timeout", StringCodec.INSTANCE).set("x");

        assertThat(rt.publish(1)).isEqualTo(1);
        Awaitility.waitAtMost(Duration.ofSeconds(2)).until(() -> received.size() == 1);

        // registration restored, so a retrying subscriber would continue
        redisson.getKeys().delete("{testTransientUpdateFailure}:timeout");
        timeout.add(score, member);

        assertThat(rt.publish(2)).isEqualTo(1);
        Awaitility.waitAtMost(Duration.ofSeconds(5)).until(() -> received.contains(2));
    }

    @Test
    public void testRenewalFailureDoesNotStopWatchdog() throws InterruptedException {
        Config config = createConfig();
        config.setReliableTopicWatchdogTimeout(1000);
        RedissonClient secondInstance = Redisson.create(config);
        try {
            RReliableTopic rt = secondInstance.getReliableTopic("testRenewalFailure");
            rt.addListener(Integer.class, (channel, msg) -> { });

            RScoredSortedSet<String> timeout = secondInstance.getScoredSortedSet("{testRenewalFailure}:timeout", StringCodec.INSTANCE);
            Awaitility.waitAtMost(Duration.ofSeconds(2)).until(() -> timeout.size() == 1);
            String member = timeout.readAll().iterator().next();
            double score = timeout.getScore(member);

            // renewal fails for at least one tick: wrong-type key
            secondInstance.getKeys().delete("{testRenewalFailure}:timeout");
            secondInstance.getBucket("{testRenewalFailure}:timeout", StringCodec.INSTANCE).set("x");
            Thread.sleep(1000);

            // registration restored, so a retrying watchdog renews it again
            secondInstance.getKeys().delete("{testRenewalFailure}:timeout");
            timeout.add(score, member);

            Awaitility.waitAtMost(Duration.ofSeconds(5)).until(() -> timeout.getScore(member) > score);
            } finally {
                secondInstance.shutdown();
            }
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
