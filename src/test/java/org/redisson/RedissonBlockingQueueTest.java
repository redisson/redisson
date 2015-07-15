package org.redisson;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.hamcrest.*;
import org.junit.*;
import org.redisson.core.*;

public class RedissonBlockingQueueTest extends BaseTest {

    @Test
    public void testPoll() throws InterruptedException {
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("queue1");
        queue1.put(1);
        Assert.assertEquals((Integer)1, queue1.poll(2, TimeUnit.SECONDS));
        Assert.assertNull(queue1.poll(2, TimeUnit.SECONDS));
    }
    @Test
    public void testAwait() throws InterruptedException {
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("queue1");
        queue1.put(1);

        Assert.assertEquals((Integer)1, queue1.poll(10, TimeUnit.SECONDS));
    }

    @Test    public void testPollLastAndOfferFirstTo() throws InterruptedException {
        RBlockingQueue<Integer> queue1 = redisson.getBlockingQueue("queue1");
        queue1.put(1);
        queue1.put(2);
        queue1.put(3);

        RBlockingQueue<Integer> queue2 = redisson.getBlockingQueue("queue2");
        queue2.put(4);
        queue2.put(5);
        queue2.put(6);

        queue1.pollLastAndOfferFirstTo(queue2, 10, TimeUnit.SECONDS);
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

        //MatcherAssert.assertThat(queue, Matchers.contains(1, 2, 3, 4));
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
                assertTrue(item > 0 && item <= total);
            } catch (InterruptedException exception) {
                fail();
            }
            count++;
        }

        assertThat(counter.get(), equalTo(total));
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
