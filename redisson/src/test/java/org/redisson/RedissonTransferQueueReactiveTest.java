package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.api.RBlockingQueueReactive;
import org.redisson.api.RTransferQueueReactive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonTransferQueueReactiveTest extends BaseReactiveTest {

    @Test
    public void testTakeElements() {
        RBlockingQueueReactive<Integer> queue = redisson.getTransferQueue("test");
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
    public void testTake() throws InterruptedException {
        RBlockingQueueReactive<Integer> queue1 = redisson.getTransferQueue("queue:take");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RBlockingQueueReactive<Integer> queue = redisson.getTransferQueue("queue:take");
            sync(queue.put(3));
        }, 10, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = sync(queue1.take());

        Assertions.assertEquals(3, l);
        Assertions.assertTrue(System.currentTimeMillis() - s > 9000);
    }

    @Test
    public void testPoll() throws InterruptedException {
        RBlockingQueueReactive<Integer> queue1 = redisson.getTransferQueue("queue1");
        sync(queue1.put(1));
        Assertions.assertEquals((Integer)1, sync(queue1.poll(2, TimeUnit.SECONDS)));

        long s = System.currentTimeMillis();
        Assertions.assertNull(sync(queue1.poll(5, TimeUnit.SECONDS)));
        Assertions.assertTrue(System.currentTimeMillis() - s > 5000);
    }
    @Test
    public void testAwait() throws InterruptedException {
        RBlockingQueueReactive<Integer> queue1 = redisson.getTransferQueue("queue1");
        sync(queue1.put(1));

        Assertions.assertEquals((Integer)1, sync(queue1.poll(10, TimeUnit.SECONDS)));
    }

    @Test
    public void testDrainTo() {
        RBlockingQueueReactive<Integer> queue = redisson.getTransferQueue("queue");
        for (int i = 0 ; i < 100; i++) {
            sync(queue.offer(i));
        }
        Assertions.assertEquals(100, sync(queue.size()).intValue());
        Set<Integer> batch = new HashSet<Integer>();
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

        RTransferQueueReactive<Integer> queue = redisson.getTransferQueue("test_:blocking:queue:");

        ExecutorService executor = Executors.newFixedThreadPool(10);

        final AtomicInteger counter = new AtomicInteger();
        int total = 100;
        for (int i = 0; i < total; i++) {
            // runnable won't be executed in any particular order, and hence, int value as well.
            executor.submit(() -> {
                sync(redisson.getTransferQueue("test_:blocking:queue:").add(counter.incrementAndGet()));
            });
        }
        
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        int count = 0;
        while (count < total) {
            int item = sync(queue.take());
            Assertions.assertTrue(item > 0 && item <= total);
            count++;
        }

        assertThat(counter.get()).isEqualTo(total);
    }

    @Test
    public void testDrainToCollection() throws Exception {
        RBlockingQueueReactive<Object> queue1 = redisson.getTransferQueue("queue1");
        sync(queue1.put(1));
        sync(queue1.put(2L));
        sync(queue1.put("e"));

        ArrayList<Object> dst = new ArrayList<Object>();
        sync(queue1.drainTo(dst));
        assertThat(dst).containsExactly(1, 2L, "e");
        Assertions.assertEquals(0, sync(queue1.size()).intValue());
    }

    @Test
    public void testDrainToCollectionLimited() throws Exception {
        RBlockingQueueReactive<Object> queue1 = redisson.getTransferQueue("queue1");
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
