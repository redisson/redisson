package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.api.RBlockingDequeReactive;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonBlockingDequeReactiveTest extends BaseReactiveTest {

    @Test
    public void testTakeFirstElements() {
        RBlockingDequeReactive<Integer> queue = redisson.getBlockingDeque("test");
        List<Integer> elements = new ArrayList<>();
        queue.takeFirstElements().subscribe(new Subscriber<Integer>() {

            @Override
            public void onSubscribe(Subscription s) {
                s.request(4);
            }

            @Override
            public void onNext(Integer t) {
                elements.add(t);
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });
        
        for (int i = 0; i < 10; i++) {
            sync(queue.add(i));
        }
        
        assertThat(elements).containsExactly(0, 1, 2, 3);
    }
    
    @Test
    public void testPollLastAndOfferFirstTo() {
        RBlockingDequeReactive<String> blockingDeque = redisson.getBlockingDeque("blocking_deque");
        Awaitility.await().between(Duration.ofMillis(950), Duration.ofMillis(1350)).untilAsserted(() -> {
            String redisTask = sync(blockingDeque.pollLastAndOfferFirstTo("deque", 1, TimeUnit.SECONDS));
            assertThat(redisTask).isNull();
        });
    }
    
    @Test
    @Timeout(3)
    public void testShortPoll() {
        RBlockingDequeReactive<Integer> queue = redisson.getBlockingDeque("queue:pollany");
        sync(queue.pollLast(500, TimeUnit.MILLISECONDS));
        sync(queue.pollFirst(10, TimeUnit.MICROSECONDS));
    }
    
    @Test
    public void testPollLastFromAny() {
        final RBlockingDequeReactive<Integer> queue1 = redisson.getBlockingDeque("deque:pollany");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingDequeReactive<Integer> queue2 = redisson.getBlockingDeque("deque:pollany1");
            RBlockingDequeReactive<Integer> queue3 = redisson.getBlockingDeque("deque:pollany2");
            sync(queue3.put(2));
            sync(queue1.put(1));
            sync(queue2.put(3));
        }, 3, TimeUnit.SECONDS);

        Awaitility.await().atLeast(Duration.ofSeconds(2)).untilAsserted(() -> {
            int l = sync(queue1.pollLastFromAny(4, TimeUnit.SECONDS, "deque:pollany1", "deque:pollany2"));
            assertThat(l).isEqualTo(2);
        });
    }

    @Test
    public void testFirstLast() {
        RBlockingDequeReactive<Integer> deque = redisson.getBlockingDeque("deque");
        sync(deque.putFirst(1));
        sync(deque.putFirst(2));
        sync(deque.putLast(3));
        sync(deque.putLast(4));

        assertThat(sync(deque)).containsExactly(2, 1, 3, 4);
    }

    @Test
    public void testOfferFirstLast() {
        RBlockingDequeReactive<Integer> deque = redisson.getBlockingDeque("deque");
        sync(deque.offerFirst(1));
        sync(deque.offerFirst(2));
        sync(deque.offerLast(3));
        sync(deque.offerLast(4));

        assertThat(sync(deque)).containsExactly(2, 1, 3, 4);
    }

    @Test
    public void testTakeFirst() {
        RBlockingDequeReactive<Integer> deque = redisson.getBlockingDeque("queue:take");

        sync(deque.offerFirst(1));
        sync(deque.offerFirst(2));
        sync(deque.offerLast(3));
        sync(deque.offerLast(4));

        assertThat(sync(deque.takeFirst())).isEqualTo(2);
        assertThat(sync(deque.takeFirst())).isEqualTo(1);
        assertThat(sync(deque.takeFirst())).isEqualTo(3);
        assertThat(sync(deque.takeFirst())).isEqualTo(4);
        assertThat(sync(deque.size())).isZero();
    }

    @Test
    public void testTakeLast() {
        RBlockingDequeReactive<Integer> deque = redisson.getBlockingDeque("queue:take");

        sync(deque.offerFirst(1));
        sync(deque.offerFirst(2));
        sync(deque.offerLast(3));
        sync(deque.offerLast(4));

        assertThat(sync(deque.takeLast())).isEqualTo(4);
        assertThat(sync(deque.takeLast())).isEqualTo(3);
        assertThat(sync(deque.takeLast())).isEqualTo(1);
        assertThat(sync(deque.takeLast())).isEqualTo(2);
        assertThat(sync(deque.size())).isZero();
    }


    @Test
    public void testTakeFirstAwait() throws InterruptedException {
        RBlockingDequeReactive<Integer> deque = redisson.getBlockingDeque("queue:take");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingDequeReactive<Integer> deque1 = redisson.getBlockingDeque("queue:take");
            sync(deque1.putFirst(1));
            sync(deque1.putFirst(2));
            sync(deque1.putLast(3));
            sync(deque1.putLast(4));
        }, 10, TimeUnit.SECONDS);

        Awaitility.await().between(Duration.ofSeconds(9), Duration.ofSeconds(11)).untilAsserted(() -> {
            assertThat(sync(deque.takeFirst())).isEqualTo(1);
        });

        Thread.sleep(50);
        assertThat(sync(deque.takeFirst())).isEqualTo(2);
        assertThat(sync(deque.takeFirst())).isEqualTo(3);
        assertThat(sync(deque.takeFirst())).isEqualTo(4);
    }

    @Test
    public void testTakeLastAwait() throws InterruptedException {
        RBlockingDequeReactive<Integer> deque = redisson.getBlockingDeque("queue:take");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingDequeReactive<Integer> deque1 = redisson.getBlockingDeque("queue:take");
            sync(deque1.putFirst(1));
            sync(deque1.putFirst(2));
            sync(deque1.putLast(3));
            sync(deque1.putLast(4));
        }, 10, TimeUnit.SECONDS);

        Awaitility.await().between(Duration.ofSeconds(9), Duration.ofSeconds(11)).untilAsserted(() -> {
            assertThat(sync(deque.takeLast())).isEqualTo(1);
        });

        Thread.sleep(50);
        assertThat(sync(deque.takeLast())).isEqualTo(4);
        assertThat(sync(deque.takeLast())).isEqualTo(3);
        assertThat(sync(deque.takeLast())).isEqualTo(2);
    }

    @Test
    public void testPollFirst() {
        RBlockingDequeReactive<Integer> queue1 = redisson.getBlockingDeque("queue1");
        sync(queue1.put(1));
        sync(queue1.put(2));
        sync(queue1.put(3));

        assertThat(sync(queue1.pollFirst(2, TimeUnit.SECONDS))).isEqualTo(1);
        assertThat(sync(queue1.pollFirst(2, TimeUnit.SECONDS))).isEqualTo(2);
        assertThat(sync(queue1.pollFirst(2, TimeUnit.SECONDS))).isEqualTo(3);

        Awaitility.await().between(Duration.ofSeconds(5), Duration.ofSeconds(7)).untilAsserted(() -> {
            assertThat(sync(queue1.pollFirst(5, TimeUnit.SECONDS))).isNull();
        });
    }

    @Test
    public void testPollLast() {
        RBlockingDequeReactive<Integer> queue1 = redisson.getBlockingDeque("queue1");
        sync(queue1.putLast(1));
        sync(queue1.putLast(2));
        sync(queue1.putLast(3));

        assertThat(sync(queue1.pollLast(2, TimeUnit.SECONDS))).isEqualTo(3);
        assertThat(sync(queue1.pollLast(2, TimeUnit.SECONDS))).isEqualTo(2);
        assertThat(sync(queue1.pollLast(2, TimeUnit.SECONDS))).isEqualTo(1);

        Awaitility.await().between(Duration.ofSeconds(5), Duration.ofSeconds(7)).untilAsserted(() -> {
            assertThat(sync(queue1.pollLast(5, TimeUnit.SECONDS))).isNull();
        });
    }

}
