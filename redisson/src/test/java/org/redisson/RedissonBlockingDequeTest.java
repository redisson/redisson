package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.queue.DequeMoveArgs;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonBlockingDequeTest extends BaseTest {

    @Test
    public void testMove() {
        RBlockingDeque<Integer> deque1 = redisson.getBlockingDeque("deque1");
        RBlockingDeque<Integer> deque2 = redisson.getBlockingDeque("deque2");

        deque2.add(4);
        deque2.add(5);
        deque2.add(6);

        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            deque1.add(1);
            deque1.add(2);
            deque1.add(3);
        }, 3, TimeUnit.SECONDS);

        Awaitility.await().atLeast(Duration.ofSeconds(1)).untilAsserted(() -> {
            Integer r = deque1.move(Duration.ofSeconds(1), DequeMoveArgs.pollFirst().addLastTo(deque2.getName()));
            assertThat(r).isNull();
        });

        Awaitility.await().between(Duration.ofMillis(1700), Duration.ofSeconds(2)).untilAsserted(() -> {
            Integer r = deque1.move(Duration.ofSeconds(2), DequeMoveArgs.pollFirst().addLastTo(deque2.getName()));
            assertThat(r).isEqualTo(1);
        });

        assertThat(deque1).containsExactly(2, 3);
        assertThat(deque2).containsExactly(4, 5, 6, 1);

        deque2.clear();

        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            deque2.addAll(Arrays.asList(4, 5, 6, 1));
        }, 3, TimeUnit.SECONDS);

        Awaitility.await().atLeast(Duration.ofSeconds(1)).untilAsserted(() -> {
            Integer r = deque2.move(Duration.ofSeconds(1), DequeMoveArgs.pollFirst().addLastTo(deque1.getName()));
            assertThat(r).isNull();
        });

        Awaitility.await().between(Duration.ofMillis(1700), Duration.ofSeconds(2)).untilAsserted(() -> {
            Integer r = deque2.move(Duration.ofSeconds(2), DequeMoveArgs.pollLast().addFirstTo(deque1.getName()));
            assertThat(r).isEqualTo(1);
        });

        assertThat(deque1).containsExactly(1, 2, 3);
        assertThat(deque2).containsExactly(4, 5, 6);
    }

    @Test
    public void testPollLastAndOfferFirstTo() throws InterruptedException {
        RBlockingDeque<String> blockingDeque = redisson.getBlockingDeque("blocking_deque");
        Awaitility.await().between(Duration.ofMillis(1000), Duration.ofMillis(1200)).untilAsserted(() -> {
            String redisTask = blockingDeque.pollLastAndOfferFirstTo("deque", 1, TimeUnit.SECONDS);
            assertThat(redisTask).isNull();
        });
    }
    
    @Test
    public void testShortPoll() {
        Assertions.assertTimeout(Duration.ofSeconds(3), () -> {
            RBlockingDeque<Integer> queue = redisson.getBlockingDeque("queue:pollany");
            queue.pollLastAsync(500, TimeUnit.MILLISECONDS);
            queue.pollFirstAsync(10, TimeUnit.MICROSECONDS);
        });
    }
    
    @Test
    public void testPollLastFromAny() throws InterruptedException {
        final RBlockingDeque<Integer> queue1 = redisson.getBlockingDeque("deque:pollany");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingDeque<Integer> queue2 = redisson.getBlockingDeque("deque:pollany1");
            RBlockingDeque<Integer> queue3 = redisson.getBlockingDeque("deque:pollany2");
            try {
                queue3.put(2);
                queue1.put(1);
                queue2.put(3);
            } catch (InterruptedException e) {
                Assertions.fail();
            }
        }, 3, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = queue1.pollLastFromAny(4, TimeUnit.SECONDS, "deque:pollany1", "deque:pollany2");

        assertThat(l).isEqualTo(2);
        assertThat(System.currentTimeMillis() - s).isGreaterThan(2000);
    }

    @Test
    public void testFirstLast() throws InterruptedException {
        RBlockingDeque<Integer> deque = redisson.getBlockingDeque("deque");
        deque.putFirst(1);
        deque.putFirst(2);
        deque.putLast(3);
        deque.putLast(4);

        assertThat(deque).containsExactly(2, 1, 3, 4);
    }

    @Test
    public void testOfferFirstLast() {
        RBlockingDeque<Integer> deque = redisson.getBlockingDeque("deque");
        deque.offerFirst(1);
        deque.offerFirst(2);
        deque.offerLast(3);
        deque.offerLast(4);

        assertThat(deque).containsExactly(2, 1, 3, 4);
    }

    @Test
    public void testTakeFirst() throws InterruptedException {
        RBlockingDeque<Integer> deque = redisson.getBlockingDeque("queue:take");

        deque.offerFirst(1);
        deque.offerFirst(2);
        deque.offerLast(3);
        deque.offerLast(4);

        assertThat(deque.takeFirst()).isEqualTo(2);
        assertThat(deque.takeFirst()).isEqualTo(1);
        assertThat(deque.takeFirst()).isEqualTo(3);
        assertThat(deque.takeFirst()).isEqualTo(4);
        assertThat(deque.size()).isZero();
    }

    @Test
    public void testTakeLast() throws InterruptedException {
        RBlockingDeque<Integer> deque = redisson.getBlockingDeque("queue:take");

        deque.offerFirst(1);
        deque.offerFirst(2);
        deque.offerLast(3);
        deque.offerLast(4);

        assertThat(deque.takeLast()).isEqualTo(4);
        assertThat(deque.takeLast()).isEqualTo(3);
        assertThat(deque.takeLast()).isEqualTo(1);
        assertThat(deque.takeLast()).isEqualTo(2);
        assertThat(deque.size()).isZero();
    }


    @Test
    public void testTakeFirstAwait() throws InterruptedException {
        RBlockingDeque<Integer> deque = redisson.getBlockingDeque("queue:take");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingDeque<Integer> deque1 = redisson.getBlockingDeque("queue:take");
            try {
                deque1.putFirst(1);
                deque1.putFirst(2);
                deque1.putLast(3);
                deque1.putLast(4);
            }catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }, 10, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        assertThat(deque.takeFirst()).isEqualTo(1);
        assertThat(System.currentTimeMillis() - s).isGreaterThan(9000);
        Thread.sleep(50);
        assertThat(deque.takeFirst()).isEqualTo(2);
        assertThat(deque.takeFirst()).isEqualTo(3);
        assertThat(deque.takeFirst()).isEqualTo(4);
    }

    @Test
    public void testTakeLastAwait() throws InterruptedException {
        RBlockingDeque<Integer> deque = redisson.getBlockingDeque("queue:take");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingDeque<Integer> deque1 = redisson.getBlockingDeque("queue:take");
            try {
                deque1.putFirst(1);
                deque1.putFirst(2);
                deque1.putLast(3);
                deque1.putLast(4);
            }catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }, 10, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        assertThat(deque.takeLast()).isEqualTo(1);
        assertThat(System.currentTimeMillis() - s).isGreaterThan(9000);
        Thread.sleep(50);
        assertThat(deque.takeLast()).isEqualTo(4);
        assertThat(deque.takeLast()).isEqualTo(3);
        assertThat(deque.takeLast()).isEqualTo(2);
    }

    @Test
    public void testPollFirst() throws InterruptedException {
        RBlockingDeque<Integer> queue1 = redisson.getBlockingDeque("queue1");
        queue1.put(1);
        queue1.put(2);
        queue1.put(3);

        assertThat(queue1.pollFirst(2, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(queue1.pollFirst(2, TimeUnit.SECONDS)).isEqualTo(2);
        assertThat(queue1.pollFirst(2, TimeUnit.SECONDS)).isEqualTo(3);

        long s = System.currentTimeMillis();
        assertThat(queue1.pollFirst(5, TimeUnit.SECONDS)).isNull();
        assertThat(System.currentTimeMillis() - s).isGreaterThan(5000);
    }

    @Test
    public void testPollLast() throws InterruptedException {
        RBlockingDeque<Integer> queue1 = redisson.getBlockingDeque("queue1");
        queue1.putLast(1);
        queue1.putLast(2);
        queue1.putLast(3);

        assertThat(queue1.pollLast(2, TimeUnit.SECONDS)).isEqualTo(3);
        assertThat(queue1.pollLast(2, TimeUnit.SECONDS)).isEqualTo(2);
        assertThat(queue1.pollLast(2, TimeUnit.SECONDS)).isEqualTo(1);

        long s = System.currentTimeMillis();
        assertThat(queue1.pollLast(5, TimeUnit.SECONDS)).isNull();
        assertThat(System.currentTimeMillis() - s).isGreaterThan(5000);
    }

}
