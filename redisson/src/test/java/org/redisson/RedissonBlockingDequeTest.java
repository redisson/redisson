package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RBlockingDeque;

public class RedissonBlockingDequeTest extends AbstractBaseTest {
    
    @Test
    public void testPollLastFromAny() throws InterruptedException {
        final RBlockingDeque<Integer> queue1 = redissonRule.getSharedClient().getBlockingDeque("deque:pollany");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                RBlockingDeque<Integer> queue2 = redissonRule.getSharedClient().getBlockingDeque("deque:pollany1");
                RBlockingDeque<Integer> queue3 = redissonRule.getSharedClient().getBlockingDeque("deque:pollany2");
                try {
                    queue3.put(2);
                    queue1.put(1);
                    queue2.put(3);
                } catch (InterruptedException e) {
                    Assert.fail();
                }
            }
        }, 3, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = queue1.pollLastFromAny(4, TimeUnit.SECONDS, "deque:pollany1", "deque:pollany2");

        assertThat(l).isEqualTo(2);
        assertThat(System.currentTimeMillis() - s).isGreaterThan(2000);
        
        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.MINUTES)).isTrue();
    }

    @Test
    public void testFirstLast() throws InterruptedException {
        RBlockingDeque<Integer> deque = redissonRule.getSharedClient().getBlockingDeque("deque");
        deque.putFirst(1);
        deque.putFirst(2);
        deque.putLast(3);
        deque.putLast(4);

        assertThat(deque).containsExactly(2, 1, 3, 4);
    }

    @Test
    public void testOfferFirstLast() throws InterruptedException {
        RBlockingDeque<Integer> deque = redissonRule.getSharedClient().getBlockingDeque("deque");
        deque.offerFirst(1);
        deque.offerFirst(2);
        deque.offerLast(3);
        deque.offerLast(4);

        assertThat(deque).containsExactly(2, 1, 3, 4);
    }

    @Test
    public void testTakeFirst() throws InterruptedException {
        RBlockingDeque<Integer> deque = redissonRule.getSharedClient().getBlockingDeque("queue:take");

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
        RBlockingDeque<Integer> deque = redissonRule.getSharedClient().getBlockingDeque("queue:take");

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
        RBlockingDeque<Integer> deque = redissonRule.getSharedClient().getBlockingDeque("queue:take");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            RBlockingDeque<Integer> deque1 = redissonRule.getSharedClient().getBlockingDeque("queue:take");
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
        
        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.MINUTES)).isTrue();
    }

    @Test
    public void testTakeLastAwait() throws InterruptedException {
        RBlockingDeque<Integer> deque = redissonRule.getSharedClient().getBlockingDeque("queue:take");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            RBlockingDeque<Integer> deque1 = redissonRule.getSharedClient().getBlockingDeque("queue:take");
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
        
        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.MINUTES)).isTrue();
    }

    @Test
    public void testPollFirst() throws InterruptedException {
        RBlockingDeque<Integer> queue1 = redissonRule.getSharedClient().getBlockingDeque("queue1");
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
        RBlockingDeque<Integer> queue1 = redissonRule.getSharedClient().getBlockingDeque("queue1");
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
