package org.redisson.misc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class FastRemovalQueueTest {

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

    private static final int ROUNDS = 200;
    private static final int ITERATIONS = 1000;
    private static final int PARKED = 20;

    @Test
    @Timeout(1)
    public void testConcurrentAddAndRemoveOfSameElement() throws Exception {
        int corruptedRounds = 0;
        Throwable firstError = null;

        for (int round = 0; round < ROUNDS; round++) {
            FastRemovalQueue<String> queue = new FastRemovalQueue<>();
            List<String> parked = new ArrayList<>();
            for (int i = 0; i < PARKED; i++) {
                queue.add("parked-" + i);
                parked.add("parked-" + i);
            }

            // both threads contend on one element; the parked ones are bystanders
            Runnable job = () -> {
                for (int i = 0; i < ITERATIONS; i++) {
                    queue.add("hot");
                    queue.remove("hot");
                }
            };
            Thread first = new Thread(job);
            Thread second = new Thread(job);
            first.start();
            second.start();
            first.join();
            second.join();

            try {
                if (!drain(queue).containsAll(parked)) {
                    corruptedRounds++;
                }
            } catch (Throwable t) {
                // corruption also leaves head != tail with head.next == null,
                // so removeFirst() throws NPE on "head.prev = null"
                corruptedRounds++;
                if (firstError == null) {
                    firstError = t;
                }
            }
        }

        assertThat(corruptedRounds)
                .withFailMessage("%d of %d rounds lost elements that were parked before the race%s",
                        corruptedRounds, ROUNDS,
                        firstError == null ? "" : ", and poll() threw " + firstError)
                .isZero();
    }

    private static List<String> drain(FastRemovalQueue<String> queue) {
        List<String> drained = new ArrayList<>();
        String element;
        // bounded because a corrupted list can contain a cycle
        while (drained.size() < 1000 && (element = queue.poll()) != null) {
            drained.add(element);
        }
        return drained;
    }

    @Test
    public void testClear() {
        FastRemovalQueue<Integer> queue = new FastRemovalQueue<>();
        queue.add(1);
        queue.add(2);
        queue.add(3);

        queue.clear();

        assertThat(queue.size()).isZero();
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.poll()).isNull();
        assertThat(queue).isEmpty();

        queue.add(4);
        queue.add(5);
        assertThat(queue).containsExactly(4, 5);
        assertThat(queue.poll()).isEqualTo(4);
        assertThat(queue.poll()).isEqualTo(5);
        assertThat(queue.poll()).isNull();
    }

    @Test
    @Timeout(1)
    public void testConcurrentClearAndAdd() throws Exception {
        int rounds = 200;
        int stranded = 0;
        int worstRound = 0;

        for (int round = 0; round < rounds; round++) {
            FastRemovalQueue<Integer> queue = new FastRemovalQueue<>();
            AtomicBoolean clearing = new AtomicBoolean(true);

            Thread adder = new Thread(() -> {
                int i = 0;
                while (clearing.get()) {
                    queue.add(i++);
                }
                for (int j = 0; j < 20; j++) {
                    queue.add(-1 - j);
                }
            });
            Thread clearer = new Thread(() -> {
                for (int i = 0; i < 500; i++) {
                    queue.clear();
                }
                clearing.set(false);
            });
            adder.start();
            clearer.start();
            adder.join();
            clearer.join();

            int size = queue.size();
            int drained = 0;
            while (queue.poll() != null && drained < 100000) {
                drained++;
            }
            if (size != drained) {
                stranded++;
                worstRound = Math.max(worstRound, Math.abs(size - drained));
            }
        }

        assertThat(stranded)
                .withFailMessage("%d of %d rounds ended with size() disagreeing with what poll() "
                                + "yields, worst by %d element(s) -- an add() that landed inside "
                                + "clear() is indexed but not listed",
                        stranded, rounds, worstRound)
                .isZero();
    }
}
