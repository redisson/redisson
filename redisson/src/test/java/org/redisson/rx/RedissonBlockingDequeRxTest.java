package org.redisson.rx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.api.RBlockingDequeRx;

public class RedissonBlockingDequeRxTest extends BaseRxTest {

    @Test
    public void testTakeFirstElements() {
        RBlockingDequeRx<Integer> queue = redisson.getBlockingDeque("test");
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
    public void testPollLastAndOfferFirstTo() throws InterruptedException {
        RBlockingDequeRx<String> blockingDeque = redisson.getBlockingDeque("blocking_deque");
        long start = System.currentTimeMillis();
        String redisTask = sync(blockingDeque.pollLastAndOfferFirstTo("deque", 1, TimeUnit.SECONDS));
        assertThat(System.currentTimeMillis() - start).isBetween(950L, 1100L);
        assertThat(redisTask).isNull();
    }
    
    @Test(timeout = 3000)
    public void testShortPoll() throws InterruptedException {
        RBlockingDequeRx<Integer> queue = redisson.getBlockingDeque("queue:pollany");
        sync(queue.pollLast(500, TimeUnit.MILLISECONDS));
        sync(queue.pollFirst(10, TimeUnit.MICROSECONDS));
    }
    
    @Test
    public void testPollLastFromAny() throws InterruptedException {
        final RBlockingDequeRx<Integer> queue1 = redisson.getBlockingDeque("deque:pollany");
        Executors.newSingleThreadScheduledExecutor().schedule(new Runnable() {
            @Override
            public void run() {
                RBlockingDequeRx<Integer> queue2 = redisson.getBlockingDeque("deque:pollany1");
                RBlockingDequeRx<Integer> queue3 = redisson.getBlockingDeque("deque:pollany2");
                sync(queue3.put(2));
                sync(queue1.put(1));
                sync(queue2.put(3));
            }
        }, 3, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = sync(queue1.pollLastFromAny(4, TimeUnit.SECONDS, "deque:pollany1", "deque:pollany2"));

        assertThat(l).isEqualTo(2);
        assertThat(System.currentTimeMillis() - s).isGreaterThan(2000);
    }

    @Test
    public void testFirstLast() throws InterruptedException {
        RBlockingDequeRx<Integer> deque = redisson.getBlockingDeque("deque");
        sync(deque.putFirst(1));
        sync(deque.putFirst(2));
        sync(deque.putLast(3));
        sync(deque.putLast(4));

        assertThat(sync(deque)).containsExactly(2, 1, 3, 4);
    }

    @Test
    public void testOfferFirstLast() throws InterruptedException {
        RBlockingDequeRx<Integer> deque = redisson.getBlockingDeque("deque");
        sync(deque.offerFirst(1));
        sync(deque.offerFirst(2));
        sync(deque.offerLast(3));
        sync(deque.offerLast(4));

        assertThat(sync(deque)).containsExactly(2, 1, 3, 4);
    }

    @Test
    public void testTakeFirst() throws InterruptedException {
        RBlockingDequeRx<Integer> deque = redisson.getBlockingDeque("queue:take");

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
    public void testTakeLast() throws InterruptedException {
        RBlockingDequeRx<Integer> deque = redisson.getBlockingDeque("queue:take");

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
        RBlockingDequeRx<Integer> deque = redisson.getBlockingDeque("queue:take");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingDequeRx<Integer> deque1 = redisson.getBlockingDeque("queue:take");
            sync(deque1.putFirst(1));
            sync(deque1.putFirst(2));
            sync(deque1.putLast(3));
            sync(deque1.putLast(4));
        }, 10, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        assertThat(sync(deque.takeFirst())).isEqualTo(1);
        assertThat(System.currentTimeMillis() - s).isGreaterThan(9000);
        Thread.sleep(50);
        assertThat(sync(deque.takeFirst())).isEqualTo(2);
        assertThat(sync(deque.takeFirst())).isEqualTo(3);
        assertThat(sync(deque.takeFirst())).isEqualTo(4);
    }

    @Test
    public void testTakeLastAwait() throws InterruptedException {
        RBlockingDequeRx<Integer> deque = redisson.getBlockingDeque("queue:take");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingDequeRx<Integer> deque1 = redisson.getBlockingDeque("queue:take");
            sync(deque1.putFirst(1));
            sync(deque1.putFirst(2));
            sync(deque1.putLast(3));
            sync(deque1.putLast(4));
        }, 10, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        assertThat(sync(deque.takeLast())).isEqualTo(1);
        assertThat(System.currentTimeMillis() - s).isGreaterThan(9000);
        Thread.sleep(50);
        assertThat(sync(deque.takeLast())).isEqualTo(4);
        assertThat(sync(deque.takeLast())).isEqualTo(3);
        assertThat(sync(deque.takeLast())).isEqualTo(2);
    }

    @Test
    public void testPollFirst() throws InterruptedException {
        RBlockingDequeRx<Integer> queue1 = redisson.getBlockingDeque("queue1");
        sync(queue1.put(1));
        sync(queue1.put(2));
        sync(queue1.put(3));

        assertThat(sync(queue1.pollFirst(2, TimeUnit.SECONDS))).isEqualTo(1);
        assertThat(sync(queue1.pollFirst(2, TimeUnit.SECONDS))).isEqualTo(2);
        assertThat(sync(queue1.pollFirst(2, TimeUnit.SECONDS))).isEqualTo(3);

        long s = System.currentTimeMillis();
        assertThat(sync(queue1.pollFirst(5, TimeUnit.SECONDS))).isNull();
        assertThat(System.currentTimeMillis() - s).isGreaterThan(5000);
    }

    @Test
    public void testPollLast() throws InterruptedException {
        RBlockingDequeRx<Integer> queue1 = redisson.getBlockingDeque("queue1");
        sync(queue1.putLast(1));
        sync(queue1.putLast(2));
        sync(queue1.putLast(3));

        assertThat(sync(queue1.pollLast(2, TimeUnit.SECONDS))).isEqualTo(3);
        assertThat(sync(queue1.pollLast(2, TimeUnit.SECONDS))).isEqualTo(2);
        assertThat(sync(queue1.pollLast(2, TimeUnit.SECONDS))).isEqualTo(1);

        long s = System.currentTimeMillis();
        assertThat(sync(queue1.pollLast(5, TimeUnit.SECONDS))).isNull();
        assertThat(System.currentTimeMillis() - s).isGreaterThan(5000);
    }

}
