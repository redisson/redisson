package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.redisson.api.RBlockingDeque;

public class RedissonPriorityBlockingDequeTest extends BaseTest {

    @Test(timeout = 3000)
    public void testShortPoll() throws InterruptedException {
        RBlockingDeque<Integer> queue = redisson.getPriorityBlockingDeque("queue:pollany");
        queue.pollLastAsync(500, TimeUnit.MILLISECONDS);
        queue.pollFirstAsync(10, TimeUnit.MICROSECONDS);
    }
    
    @Test
    public void testTakeFirst() throws InterruptedException {
        RBlockingDeque<Integer> deque = redisson.getPriorityBlockingDeque("queue:take");

        deque.add(1);
        deque.add(2);
        deque.add(3);
        deque.add(4);

        assertThat(deque.takeFirst()).isEqualTo(1);
        assertThat(deque.takeFirst()).isEqualTo(2);
        assertThat(deque.takeFirst()).isEqualTo(3);
        assertThat(deque.takeFirst()).isEqualTo(4);
        assertThat(deque.size()).isZero();
    }

    @Test
    public void testTakeLast() throws InterruptedException {
        RBlockingDeque<Integer> deque = redisson.getPriorityBlockingDeque("queue:take");

        deque.add(1);
        deque.add(2);
        deque.add(3);
        deque.add(4);

        assertThat(deque.takeLast()).isEqualTo(4);
        assertThat(deque.takeLast()).isEqualTo(3);
        assertThat(deque.takeLast()).isEqualTo(2);
        assertThat(deque.takeLast()).isEqualTo(1);
        assertThat(deque.size()).isZero();
    }

    @Test
    public void testTakeFirstAwait() throws InterruptedException {
        RBlockingDeque<Integer> deque = redisson.getPriorityBlockingDeque("queue:take");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingDeque<Integer> deque1 = redisson.getBlockingDeque("queue:take");
            deque1.add(1);
            deque1.add(2);
            deque1.add(3);
            deque1.add(4);
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
        RBlockingDeque<Integer> deque = redisson.getPriorityBlockingDeque("queue:take");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingDeque<Integer> deque1 = redisson.getBlockingDeque("queue:take");
            deque1.add(1);
            deque1.add(2);
            deque1.add(3);
            deque1.add(4);
        }, 10, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        assertThat(deque.takeLast()).isEqualTo(4);
        assertThat(System.currentTimeMillis() - s).isGreaterThan(9000);
        Thread.sleep(50);
        assertThat(deque.takeLast()).isEqualTo(3);
        assertThat(deque.takeLast()).isEqualTo(2);
        assertThat(deque.takeLast()).isEqualTo(1);
    }

    @Test
    public void testPollFirst() throws InterruptedException {
        RBlockingDeque<Integer> queue1 = redisson.getPriorityBlockingDeque("queue1");
        queue1.put(1);
        queue1.put(2);
        queue1.put(3);

        assertThat(queue1.pollFirst(2, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(queue1.pollFirst(2, TimeUnit.SECONDS)).isEqualTo(2);
        assertThat(queue1.pollFirst(2, TimeUnit.SECONDS)).isEqualTo(3);

        long s = System.currentTimeMillis();
        assertThat(queue1.pollFirst(5, TimeUnit.SECONDS)).isNull();
        assertThat(System.currentTimeMillis() - s).isGreaterThan(4900);
    }

    @Test
    public void testPollLast() throws InterruptedException {
        RBlockingDeque<Integer> queue1 = redisson.getPriorityBlockingDeque("queue1");
        queue1.add(3);
        queue1.add(1);
        queue1.add(2);

        assertThat(queue1.pollLast(2, TimeUnit.SECONDS)).isEqualTo(3);
        assertThat(queue1.pollLast(2, TimeUnit.SECONDS)).isEqualTo(2);
        assertThat(queue1.pollLast(2, TimeUnit.SECONDS)).isEqualTo(1);

        long s = System.currentTimeMillis();
        assertThat(queue1.pollLast(5, TimeUnit.SECONDS)).isNull();
        assertThat(System.currentTimeMillis() - s).isGreaterThanOrEqualTo(5000);
    }

}
