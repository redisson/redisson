package org.redisson;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RBlockingQueue;

import io.netty.util.concurrent.Future;

public class RedissonBlockingQueueTest extends BaseTest {

    @Test
    public void testTakeAsyncCancel() {
        Config config = createConfig();
        config.useSingleServer().setConnectionMinimumIdleSize(1).setConnectionPoolSize(1);

        RedissonClient redisson = Redisson.create(config);
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("queue:pollany");
        for (int i = 0; i < 10; i++) {
            Future<Integer> f = queue1.takeAsync();
            f.cancel(true);
        }
        assertThat(queue1.add(1)).isTrue();
        assertThat(queue1.add(2)).isTrue();
        assertThat(queue1.size()).isEqualTo(2);
        
        redisson.shutdown();
    }
    
    @Test
    public void testPollAsyncCancel() {
        Config config = createConfig();
        config.useSingleServer().setConnectionMinimumIdleSize(1).setConnectionPoolSize(1);

        RedissonClient redisson = Redisson.create(config);
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("queue:pollany");
        for (int i = 0; i < 10; i++) {
            Future<Integer> f = queue1.pollAsync(1, TimeUnit.SECONDS);
            f.cancel(true);
        }
        assertThat(queue1.add(1)).isTrue();
        assertThat(queue1.add(2)).isTrue();
        assertThat(queue1.size()).isEqualTo(2);
        
        redisson.shutdown();
    }

    
    @Test
    public void testPollFromAny() throws InterruptedException {
        final RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("queue:pollany");
        Executors.newSingleThreadScheduledExecutor().schedule(new Runnable() {
            @Override
            public void run() {
                RBlockingQueue<Integer> queue2 = redisson.getBlockingQueue("queue:pollany1");
                RBlockingQueue<Integer> queue3 = redisson.getBlockingQueue("queue:pollany2");
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
        int l = queue1.pollFromAny(4, TimeUnit.SECONDS, "queue:pollany1", "queue:pollany2");

        Assert.assertEquals(2, l);
        Assert.assertTrue(System.currentTimeMillis() - s > 2000);
    }

    @Test
    public void testTake() throws InterruptedException {
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("queue:take");
        Executors.newSingleThreadScheduledExecutor().schedule(new Runnable() {
            @Override
            public void run() {
                RBlockingQueue<Integer> queue = redisson.getBlockingQueue("queue:take");
                try {
                    queue.put(3);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }, 10, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = queue1.take();

        Assert.assertEquals(3, l);
        Assert.assertTrue(System.currentTimeMillis() - s > 9000);
    }

    @Test
    public void testPoll() throws InterruptedException {
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("queue1");
        queue1.put(1);
        Assert.assertEquals((Integer)1, queue1.poll(2, TimeUnit.SECONDS));

        long s = System.currentTimeMillis();
        Assert.assertNull(queue1.poll(5, TimeUnit.SECONDS));
        Assert.assertTrue(System.currentTimeMillis() - s > 5000);
    }
    @Test
    public void testAwait() throws InterruptedException {
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("queue1");
        queue1.put(1);

        Assert.assertEquals((Integer)1, queue1.poll(10, TimeUnit.SECONDS));
    }

    @Test
    public void testPollLastAndOfferFirstTo() throws InterruptedException {
        final RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("{queue}1");
        Executors.newSingleThreadScheduledExecutor().schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    queue1.put(3);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }, 10, TimeUnit.SECONDS);

        RBlockingQueue<Integer> queue2 = redisson.getBlockingQueue("{queue}2");
        queue2.put(4);
        queue2.put(5);
        queue2.put(6);

        queue1.pollLastAndOfferFirstTo(queue2.getName(), 10, TimeUnit.SECONDS);
        MatcherAssert.assertThat(queue2, Matchers.contains(3, 4, 5, 6));
    }

    @Test
    public void testAddOfferOrigin() {
        Queue<Integer> queue = new LinkedList<Integer>();
        queue.add(1);
        queue.offer(2);
        queue.add(3);
        queue.offer(4);

        MatcherAssert.assertThat(queue, Matchers.contains(1, 2, 3, 4));
        Assert.assertEquals((Integer) 1, queue.poll());
        MatcherAssert.assertThat(queue, Matchers.contains(2, 3, 4));
        Assert.assertEquals((Integer) 2, queue.element());
    }

    @Test
    public void testAddOffer() {
        RBlockingQueue<Integer> queue = redisson.getBlockingQueue("blocking:queue");
        queue.add(1);
        queue.offer(2);
        queue.add(3);
        queue.offer(4);

        MatcherAssert.assertThat(queue, Matchers.contains(1, 2, 3, 4));
        Assert.assertEquals((Integer) 1, queue.poll());
        MatcherAssert.assertThat(queue, Matchers.contains(2, 3, 4));
        Assert.assertEquals((Integer) 2, queue.element());
    }

    @Test
    public void testRemoveOrigin() {
        Queue<Integer> queue = new LinkedList<Integer>();
        queue.add(1);
        queue.add(2);
        queue.add(3);
        queue.add(4);

        queue.remove();
        queue.remove();

        MatcherAssert.assertThat(queue, Matchers.contains(3, 4));
        queue.remove();
        queue.remove();

        Assert.assertTrue(queue.isEmpty());
    }

    @Test
    public void testRemove() {
        RBlockingQueue<Integer> queue = redisson.getBlockingQueue("blocking:queue");
        queue.add(1);
        queue.add(2);
        queue.add(3);
        queue.add(4);

        queue.remove();
        queue.remove();

        MatcherAssert.assertThat(queue, Matchers.contains(3, 4));
        queue.remove();
        queue.remove();

        Assert.assertTrue(queue.isEmpty());
    }

    @Test(expected = NoSuchElementException.class)
    public void testRemoveEmpty() {
        RBlockingQueue<Integer> queue = redisson.getBlockingQueue("blocking:queue");
        queue.remove();
    }

    @Test
    public void testDrainTo() {
        RBlockingQueue<Integer> queue = redisson.getBlockingQueue("queue");
        for (int i = 0 ; i < 100; i++) {
            queue.offer(i);
        }
        Assert.assertEquals(100, queue.size());
        Set<Integer> batch = new HashSet<Integer>();
        int count = queue.drainTo(batch, 10);
        Assert.assertEquals(10, count);
        Assert.assertEquals(10, batch.size());
        Assert.assertEquals(90, queue.size());
        queue.drainTo(batch, 10);
        queue.drainTo(batch, 20);
        queue.drainTo(batch, 60);
        Assert.assertEquals(0, queue.size());
    }

    @Test
    public void testBlockingQueue() {

        RBlockingQueue<Integer> queue = redisson.getBlockingQueue("test_:blocking:queue:");

        ExecutorService executor = Executors.newFixedThreadPool(10);

        final AtomicInteger counter = new AtomicInteger();
        int total = 100;
        for (int i = 0; i < total; i++) {
            // runnable won't be executed in any particular order, and hence, int value as well.
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    redisson.getQueue("test_:blocking:queue:").add(counter.incrementAndGet());
                }
            });
        }
        int count = 0;
        while (count < total) {
            try {
                // blocking
                int item = queue.take();
                assertThat(item > 0 && item <= total).isTrue();
            } catch (InterruptedException exception) {
                Assert.fail();
            }
            count++;
        }

        assertThat(counter.get()).isEqualTo(total);
        queue.delete();
    }

    @Test
    public void testDrainToCollection() throws Exception {
        RBlockingQueue<Object> queue1 = redisson.getBlockingQueue("queue1");
        queue1.put(1);
        queue1.put(2L);
        queue1.put("e");

        ArrayList<Object> dst = new ArrayList<Object>();
        queue1.drainTo(dst);
        MatcherAssert.assertThat(dst, Matchers.<Object>contains(1, 2L, "e"));
        Assert.assertEquals(0, queue1.size());
    }

    @Test
    public void testDrainToCollectionLimited() throws Exception {
        RBlockingQueue<Object> queue1 = redisson.getBlockingQueue("queue1");
        queue1.put(1);
        queue1.put(2L);
        queue1.put("e");

        ArrayList<Object> dst = new ArrayList<Object>();
        queue1.drainTo(dst, 2);
        MatcherAssert.assertThat(dst, Matchers.<Object>contains(1, 2L));
        Assert.assertEquals(1, queue1.size());

        dst.clear();
        queue1.drainTo(dst, 2);
        MatcherAssert.assertThat(dst, Matchers.<Object>contains("e"));


    }
}
