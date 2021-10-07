package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RTransferQueue;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Nikita Koksharov
 */
public class RedissonTransferQueueTest extends BaseTest {

    @Test
    public void testTryTransferWithDelay() throws InterruptedException, ExecutionException {
        RTransferQueue<Integer> queue1 = redisson.getTransferQueue("queue");
        Future<?> f = Executors.newSingleThreadExecutor().submit(() -> {
            RTransferQueue<Integer> queue = redisson.getTransferQueue("queue");
            try {
                long time = System.currentTimeMillis();
                boolean res = queue.tryTransfer(3, 2, TimeUnit.SECONDS);
                assertThat(System.currentTimeMillis() - time).isGreaterThan(1900);
                assertThat(res).isFalse();

                Thread.sleep(1000);

                boolean res2 = queue.tryTransfer(4, 1, TimeUnit.SECONDS);
                assertThat(res2).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread.sleep(2100);

        assertThat(queue1.size()).isZero();

        Thread.sleep(1100);

        assertThat(queue1.size()).isEqualTo(1);
        assertThat(queue1.peek()).isEqualTo(4);
        assertThat(queue1.poll()).isEqualTo(4);
        f.get();
        assertThat(queue1.size()).isZero();
        assertThat(queue1.peek()).isNull();
    }

    @Test
    public void testTransfer() throws InterruptedException, ExecutionException {
        RTransferQueue<Integer> queue1 = redisson.getTransferQueue("queue");
        AtomicBoolean takeExecuted = new AtomicBoolean();
        Future<?> f = Executors.newSingleThreadExecutor().submit(() -> {
            RTransferQueue<Integer> queue = redisson.getTransferQueue("queue");
            try {
                long time = System.currentTimeMillis();
                queue.transfer(3);
                assertThat(takeExecuted.get()).isTrue();
                assertThat(System.currentTimeMillis() - time).isGreaterThan(2850);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(3000);

        assertThat(queue1.size()).isEqualTo(1);
        assertThat(queue1.peek()).isEqualTo(3);
        assertThat(queue1.take()).isEqualTo(3);
        takeExecuted.set(true);
        f.get();
        assertThat(queue1.size()).isZero();
        assertThat(queue1.peek()).isNull();
    }

    @Test
    public void testTryTransfer() throws InterruptedException, ExecutionException {
        RTransferQueue<Integer> queue1 = redisson.getTransferQueue("queue");
        AtomicBoolean takeExecuted = new AtomicBoolean();
        ScheduledFuture<?> f = Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RTransferQueue<Integer> queue = redisson.getTransferQueue("queue");
            boolean res = queue.tryTransfer(3);
            assertThat(takeExecuted.get()).isTrue();
            assertThat(res).isTrue();
            boolean res2 = queue.tryTransfer(4);
            assertThat(res2).isFalse();
        }, 4, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = queue1.take();
        takeExecuted.set(true);

        Assertions.assertEquals(3, l);
        Assertions.assertTrue(System.currentTimeMillis() - s > 3900);
        f.get();
        assertThat(queue1.size()).isZero();
        assertThat(queue1.peek()).isNull();
    }

    @Test
    public void testTake() throws InterruptedException {
        RTransferQueue<Integer> queue1 = redisson.getTransferQueue("queue");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RTransferQueue<Integer> queue = redisson.getTransferQueue("queue");
            try {
                queue.put(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, 10, TimeUnit.SECONDS);

        long s = System.currentTimeMillis();
        int l = queue1.take();

        Assertions.assertEquals(3, l);
        Assertions.assertTrue(System.currentTimeMillis() - s > 9000);
    }

    @Test
    public void testPoll() throws InterruptedException {
        RTransferQueue<Integer> queue1 = redisson.getTransferQueue("queue");
        queue1.put(1);
        Assertions.assertEquals((Integer)1, queue1.poll(2, TimeUnit.SECONDS));

        long s = System.currentTimeMillis();
        Assertions.assertNull(queue1.poll(5, TimeUnit.SECONDS));
        Assertions.assertTrue(System.currentTimeMillis() - s > 4900);
    }

    @Test
    public void testPeek() {
        RTransferQueue<String> queue = redisson.getTransferQueue("queue");
        assertThat(queue.peek()).isNull();

        queue.add("1");

        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.peek()).isEqualTo("1");
    }

    @Test
    public void testReadAll() {
        RTransferQueue<String> queue = redisson.getTransferQueue("queue");

        queue.add("1");
        queue.add("2");
        queue.add("3");
        queue.add("4");

        assertThat(queue.readAll()).containsExactly("1", "2", "3", "4");
    }

    @Test
    public void testDrainTo() {
        RTransferQueue<Integer> queue = redisson.getTransferQueue("queue");
        for (int i = 0 ; i < 100; i++) {
            queue.offer(i);
        }
        Assertions.assertEquals(100, queue.size());
        Set<Integer> batch = new HashSet<>();
        int count = queue.drainTo(batch, 10);
        Assertions.assertEquals(10, count);
        Assertions.assertEquals(10, batch.size());
        Assertions.assertEquals(90, queue.size());
        queue.drainTo(batch, 10);
        queue.drainTo(batch, 20);
        queue.drainTo(batch, 60);
        Assertions.assertEquals(0, queue.size());
    }

    @Test
    public void testDrainToSingle() {
        RTransferQueue<Integer> queue = redisson.getTransferQueue("queue");
        Assertions.assertTrue(queue.add(1));
        Assertions.assertEquals(1, queue.size());
        Set<Integer> batch = new HashSet<>();
        int count = queue.drainTo(batch);
        Assertions.assertEquals(1, count);
        Assertions.assertEquals(1, batch.size());
        Assertions.assertTrue(queue.isEmpty());
    }

    @Test
    public void testClear() throws ExecutionException, InterruptedException {
        RTransferQueue<String> queue = redisson.getTransferQueue("queue");
        queue.add("1");
        queue.add("4");
        queue.add("2");
        queue.add("5");
        queue.add("3");

        ScheduledFuture<?> f = Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            RTransferQueue<Integer> queue1 = redisson.getTransferQueue("queue");
            try {
                queue1.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, 1, TimeUnit.SECONDS);

        f.get();
        queue.clear();
        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testIteratorRemove() {
        RTransferQueue<String> queue = redisson.getTransferQueue("queue");
        queue.add("1");
        queue.add("4");
        queue.add("2");
        queue.add("5");
        queue.add("3");

        queue.removeIf(value -> value.equals("2"));

        assertThat(queue).containsExactly("1", "4", "5", "3");

        int iteration = 0;
        for (Iterator<String> iterator = queue.iterator(); iterator.hasNext();) {
            iterator.next();
            iterator.remove();
            iteration++;
        }

        Assertions.assertEquals(4, iteration);

        Assertions.assertEquals(0, queue.size());
        Assertions.assertTrue(queue.isEmpty());
    }

}
