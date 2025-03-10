package org.redisson.misc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class FastRmovalQueueTest {

    @Test
    public void testIterator() {
        FastRemovalQueue<Integer> queue = new FastRemovalQueue<>();
        queue.add(1);
        queue.add(2);
        queue.add(3);

        List<Integer> list = new ArrayList<>();
        for (Integer i : queue) {
            list.add(i);
        }

        assertThat(queue).containsExactly(list.toArray(new Integer[0]));
    }

    @Test
    public void testMoveToTail() {
        FastRemovalQueue<Integer> queue = new FastRemovalQueue<>();
        queue.add(1);
        queue.add(2);
        queue.add(3);

        queue.moveToTail(1);

        assertThat(queue.poll()).isEqualTo(2);
        assertThat(queue.poll()).isEqualTo(3);
        assertThat(queue.poll()).isEqualTo(1);

        queue.add(4);
        queue.add(5);
        queue.add(6);

        assertThat(queue.moveToTail(11)).isFalse();
        assertThat(queue.moveToTail(5)).isTrue();
        assertThat(queue.moveToTail(5)).isTrue();

        assertThat(queue.poll()).isEqualTo(4);
        assertThat(queue.poll()).isEqualTo(6);
        assertThat(queue.poll()).isEqualTo(5);
    }

    @Test
    public void testRemovePoll() throws InterruptedException {
        FastRemovalQueue<Integer> queue = new FastRemovalQueue<>();
        queue.add(1);
        queue.add(2);
        queue.add(3);
        assertThat(queue.remove(1)).isTrue();
        assertThat(queue.remove(2)).isTrue();
        assertThat(queue.poll()).isEqualTo(3);
        assertThat(queue.poll()).isNull();
        queue.add(4);
        queue.add(5);
        queue.add(6);
        assertThat(queue.poll()).isEqualTo(4);
        assertThat(queue.remove(5)).isTrue();
        assertThat(queue.poll()).isEqualTo(6);
        assertThat(queue.poll()).isNull();
    }

    @Test
    public void testConcurrentAddAndRemove() throws InterruptedException {
        FastRemovalQueue<Integer> queue = new FastRemovalQueue<>();
        int numThreads = 16;
        int numElements = 60000;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger removed = new AtomicInteger();
        AtomicInteger pooled = new AtomicInteger();

        // Submit tasks to add and remove elements concurrently
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;

            executor.submit(() -> {
                try {
                    for (int j = 0; j < numElements; j++) {
                        queue.add(threadId * numElements + j);
                        if (Math.random() > 0.5) {
                            Integer elementToRemove = threadId * numElements + j - 1;
                            if (elementToRemove >= 0) {
                                if (queue.remove(elementToRemove)) {
                                    removed.incrementAndGet();
                                }
                            }
                        }
                        if (Math.random() > 0.5) {
                            if (queue.poll() != null) {
                                pooled.incrementAndGet();
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10000, TimeUnit.SECONDS);

        int totalPolled = 0;
        while (queue.poll() != null) {
            totalPolled++;
        }

        assertThat(totalPolled).isGreaterThan(1);
        assertThat(removed.get()).isGreaterThan(1);
        assertThat(pooled.get()).isGreaterThan(1);

        assertThat(totalPolled + removed.get() + pooled.get()).isEqualTo(numThreads * numElements);
    }

//    @Test
    public void testConcurrentAddAndRemoveOld() throws InterruptedException {
        ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();
        int numThreads = 16;
        int numElements = 60000;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger removed = new AtomicInteger();
        AtomicInteger pooled = new AtomicInteger();

        // Submit tasks to add and remove elements concurrently
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < numElements; j++) {
                    queue.add(threadId * numElements + j);  // Add elements
                    if (Math.random() > 0.5) {  // Randomly remove an element
                        Integer elementToRemove = threadId * numElements + j - 1;
                        if (elementToRemove >= 0) {
                            if (queue.remove(elementToRemove)) {
                                removed.incrementAndGet();
                            }
                        }
                    }
//                    if (Math.random() > 0.5) {
//                        if (queue.poll() != null) {
//                            pooled.incrementAndGet();
//                        }
//                    }
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10000, TimeUnit.SECONDS);

        int totalPolled = 0;
        while (queue.poll() != null) {
            totalPolled++;
        }
        System.out.println("totalPolled " + totalPolled);
        System.out.println("removed " + removed.get());

        assertThat(totalPolled + removed.get() + pooled.get()).isEqualTo(numThreads * numElements);
    }

}
