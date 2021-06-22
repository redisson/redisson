package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.api.RBlockingDequeReactive;
import org.redisson.api.RBlockingQueueReactive;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonBlockingQueueReactiveTest extends BaseReactiveTest {

    @Test
    public void testTakeElements2() throws InterruptedException {
        RBlockingDequeReactive<Long> queue = redisson.getBlockingDeque("test");

        Mono<Void> mono = Flux.range(1, 100)
                            .flatMap(s -> queue.add(Long.valueOf(s)))
                            .then();

        StepVerifier.create(mono).verifyComplete();

        AtomicInteger counter = new AtomicInteger();
        queue.takeElements()
                .doOnNext(e -> {
                    counter.incrementAndGet();
                })
                .delayElements(Duration.ofMillis(2))
                .repeat()
                .subscribe();

        Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
            assertThat(counter.get()).isEqualTo(100);
        });
    }

    @Test
    public void testTakeElements() {
        RBlockingQueueReactive<Integer> queue = redisson.getBlockingQueue("test");
        List<Integer> elements = new ArrayList<>();
        queue.takeElements().subscribe(new Subscriber<Integer>() {

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
    public void testPollFromAny() {
        final RBlockingQueueReactive<Integer> queue1 = redisson.getBlockingQueue("queue:pollany");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingQueueReactive<Integer> queue2 = redisson.getBlockingQueue("queue:pollany1");
            RBlockingQueueReactive<Integer> queue3 = redisson.getBlockingQueue("queue:pollany2");
            sync(queue3.put(2));
            sync(queue1.put(1));
            sync(queue2.put(3));
        }, 3, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = sync(queue1.pollFromAny(4, TimeUnit.SECONDS, "queue:pollany1", "queue:pollany2"));

        Assertions.assertEquals(2, l);
        Assertions.assertTrue(System.currentTimeMillis() - s > 2000);
    }

    @Test
    public void testTake() {
        RBlockingQueueReactive<Integer> queue1 = redisson.getBlockingQueue("queue:take");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingQueueReactive<Integer> queue = redisson.getBlockingQueue("queue:take");
            sync(queue.put(3));
        }, 10, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = sync(queue1.take());

        Assertions.assertEquals(3, l);
        Assertions.assertTrue(System.currentTimeMillis() - s > 9000);
    }

    @Test
    public void testPoll() {
        RBlockingQueueReactive<Integer> queue1 = redisson.getBlockingQueue("queue1");
        sync(queue1.put(1));
        Assertions.assertEquals((Integer)1, sync(queue1.poll(2, TimeUnit.SECONDS)));

        long s = System.currentTimeMillis();
        Assertions.assertNull(sync(queue1.poll(5, TimeUnit.SECONDS)));
        Assertions.assertTrue(System.currentTimeMillis() - s > 5000);
    }
    @Test
    public void testAwait() {
        RBlockingQueueReactive<Integer> queue1 = redisson.getBlockingQueue("queue1");
        sync(queue1.put(1));

        Assertions.assertEquals((Integer)1, sync(queue1.poll(10, TimeUnit.SECONDS)));
    }

    @Test
    public void testPollLastAndOfferFirstTo() {
        RBlockingQueueReactive<Integer> queue1 = redisson.getBlockingQueue("queue1");
        sync(queue1.put(1));
        sync(queue1.put(2));
        sync(queue1.put(3));

        RBlockingQueueReactive<Integer> queue2 = redisson.getBlockingQueue("queue2");
        sync(queue2.put(4));
        sync(queue2.put(5));
        sync(queue2.put(6));

        sync(queue1.pollLastAndOfferFirstTo(queue2.getName(), 10, TimeUnit.SECONDS));
        assertThat(sync(queue2)).containsExactly(3, 4, 5, 6);
    }

    @Test
    public void testAddOffer() {
        RBlockingQueueReactive<Integer> queue = redisson.getBlockingQueue("blocking:queue");
        sync(queue.add(1));
        sync(queue.offer(2));
        sync(queue.add(3));
        sync(queue.offer(4));

        //MatcherAssert.assertThat(queue, Matchers.contains(1, 2, 3, 4));
        Assertions.assertEquals((Integer) 1, sync(queue.poll()));
        assertThat(sync(queue)).containsExactly(2, 3, 4);
        Assertions.assertEquals((Integer) 2, sync(queue.peek()));
    }

    @Test
    public void testRemove() {
        RBlockingQueueReactive<Integer> queue = redisson.getBlockingQueue("blocking:queue");
        sync(queue.add(1));
        sync(queue.add(2));
        sync(queue.add(3));
        sync(queue.add(4));

        sync(queue.poll());
        sync(queue.poll());

        assertThat(sync(queue)).containsExactly(3, 4);
        sync(queue.poll());
        sync(queue.poll());

        Assertions.assertEquals(0, sync(queue.size()).intValue());
    }

    @Test
    public void testRemoveEmpty() {
        RBlockingQueueReactive<Integer> queue = redisson.getBlockingQueue("blocking:queue");
        Assertions.assertNull(sync(queue.poll()));
    }

    @Test
    public void testDrainTo() {
        RBlockingQueueReactive<Integer> queue = redisson.getBlockingQueue("queue");
        for (int i = 0 ; i < 100; i++) {
            sync(queue.offer(i));
        }
        Assertions.assertEquals(100, sync(queue.size()).intValue());
        Set<Integer> batch = new HashSet<>();
        int count = sync(queue.drainTo(batch, 10));
        Assertions.assertEquals(10, count);
        Assertions.assertEquals(10, batch.size());
        Assertions.assertEquals(90, sync(queue.size()).intValue());
        sync(queue.drainTo(batch, 10));
        sync(queue.drainTo(batch, 20));
        sync(queue.drainTo(batch, 60));
        Assertions.assertEquals(0, sync(queue.size()).intValue());
    }

    @Test
    public void testBlockingQueue() throws InterruptedException {

        RBlockingQueueReactive<Integer> queue = redisson.getBlockingQueue("test_:blocking:queue:");

        ExecutorService executor = Executors.newFixedThreadPool(10);

        final AtomicInteger counter = new AtomicInteger();
        int total = 100;
        for (int i = 0; i < total; i++) {
            // runnable won't be executed in any particular order, and hence, int value as well.
            executor.submit(() -> {
                sync(redisson.getQueue("test_:blocking:queue:").add(counter.incrementAndGet()));
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        int count = 0;
        while (count < total) {
            int item = sync(queue.take());
            assertThat(item > 0 && item <= total).isTrue();
            count++;
        }

        assertThat(counter.get()).isEqualTo(total);
    }

    @Test
    public void testDrainToCollection() {
        RBlockingQueueReactive<Object> queue1 = redisson.getBlockingQueue("queue1");
        sync(queue1.put(1));
        sync(queue1.put(2L));
        sync(queue1.put("e"));

        ArrayList<Object> dst = new ArrayList<Object>();
        sync(queue1.drainTo(dst));
        assertThat(dst).containsExactly(1, 2L, "e");
        Assertions.assertEquals(0, sync(queue1.size()).intValue());
    }

    @Test
    public void testDrainToCollectionLimited() {
        RBlockingQueueReactive<Object> queue1 = redisson.getBlockingQueue("queue1");
        sync(queue1.put(1));
        sync(queue1.put(2L));
        sync(queue1.put("e"));

        ArrayList<Object> dst = new ArrayList<Object>();
        sync(queue1.drainTo(dst, 2));
        assertThat(dst).containsExactly(1, 2L);
        Assertions.assertEquals(1, sync(queue1.size()).intValue());

        dst.clear();
        sync(queue1.drainTo(dst, 2));
        assertThat(dst).containsExactly("e");
    }
}
