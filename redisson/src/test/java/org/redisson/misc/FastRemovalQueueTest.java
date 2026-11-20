package org.redisson.misc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.assertj.core.api.Assertions.assertThat;

public class FastRemovalQueueTest {

    @Test
    public void testDuplication() {
        FastRemovalQueue<Integer> queue = new FastRemovalQueue<>();
        queue.add(1);
        queue.add(2);
        queue.add(2);
        queue.add(3);
        queue.add(1);

        assertThat(queue).containsExactly(1, 2, 3);
    }

    @Test
    public void testIterator() {
        FastRemovalQueue<Integer> queue = new FastRemovalQueue<>();
        queue.add(1);
        queue.add(2);
        queue.add(3);

        assertThat(queue).containsExactly(1, 2, 3);

        // iteration skips an element that was removed from the middle
        queue.remove(2);
        assertThat(queue).containsExactly(1, 3);
    }

    /**
     * The queue holds an element one time only. add() of an element that is equal to a queued
     * one changes nothing, and one remove() takes the element out.
     */
    @Test
    public void testAddOfQueuedElementIsIgnored() {
        FastRemovalQueue<String> queue = new FastRemovalQueue<>();
        queue.add("a");
        queue.add("b");
        // equal to the first element, but another object
        queue.add(new String("a"));

        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue).containsExactly("a", "b");

        assertThat(queue.remove("a")).isTrue();
        assertThat(queue.remove("a")).isFalse();
        assertThat(queue).containsExactly("b");

        // a removed element can be added again and goes to the tail
        queue.add("a");
        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.poll()).isEqualTo("b");
        assertThat(queue.poll()).isEqualTo("a");
        assertThat(queue.poll()).isNull();
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
    public void testConcurrentAddAndRemove() throws Exception {
        FastRemovalQueue<Integer> queue = new FastRemovalQueue<>();
        int numThreads = 16;
        int numElements = 60000;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger removed = new AtomicInteger();
        AtomicInteger pooled = new AtomicInteger();

        // Submit tasks to add and remove elements concurrently
        List<Future<?>> workers = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;

            workers.add(executor.submit(() -> {
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
            }));
        }

        executor.shutdown();
        // get() throws what the worker threw. The totals below fail too when a worker stops
        // early, but they do not tell which call failed
        for (Future<?> worker : workers) {
            worker.get(10000, TimeUnit.SECONDS);
        }

        int totalPolled = 0;
        while (queue.poll() != null) {
            totalPolled++;
        }

        assertThat(totalPolled).isGreaterThan(1);
        assertThat(removed.get()).isGreaterThan(1);
        assertThat(pooled.get()).isGreaterThan(1);

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

    /**
     * Elements are removed while an older element stays queued, not in the order of add(). Then
     * all elements are polled. The elements that were not removed must come out in the order
     * of add(), each one time. All rounds use the same queue.
     */
    @Test
    public void testRemoveBehindElementThatStays() {
        FastRemovalQueue<Integer> queue = new FastRemovalQueue<>();
        Random random = new Random(1);
        int next = 0;
        for (int round = 0; round < 100; round++) {
            List<Integer> added = new ArrayList<>();
            for (int i = 0; i < 201; i++) {
                queue.add(next);
                added.add(next++);
            }
            // the oldest element stays
            List<Integer> removed = new ArrayList<>(added.subList(1, added.size()));
            Collections.shuffle(removed, random);
            removed = removed.subList(0, 150);
            for (Integer element : removed) {
                assertThat(queue.remove(element)).isTrue();
            }
            List<Integer> expected = new ArrayList<>(added);
            expected.removeAll(removed);

            assertThat(queue.size()).isEqualTo(expected.size());
            assertThat(queue).containsExactlyElementsOf(expected);
            for (Integer element : expected) {
                assertThat(queue.poll()).isEqualTo(element);
            }
            assertThat(queue.poll()).isNull();
            assertThat(queue.isEmpty()).isTrue();
            assertThat(queue.size()).isZero();
        }
    }

    /**
     * Many threads add elements and remove most of them a little later, not in the order of
     * add(). Some elements stay, so removed elements are in front of, between and behind
     * queued ones. At the end the elements that stayed are polled. Each element must leave the
     * queue one time, and the elements of one thread must come out in the order of add().
     */
    @Test
    @Timeout(120)
    public void testConcurrentRemoveBetweenElementsThatStay() throws Exception {
        FastRemovalQueue<Integer> queue = new FastRemovalQueue<>();
        int threads = 8;
        int inFlight = 8;
        int iterations = 100_000;
        int stayEvery = 1000;
        AtomicIntegerArray left = new AtomicIntegerArray(threads * iterations);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> workers = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                int firstElement = t * iterations;
                workers.add(executor.submit(() -> {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    Integer[] pending = new Integer[inFlight];
                    for (int i = 0; i < iterations; i++) {
                        Integer element = firstElement + i;
                        queue.add(element);
                        if (i % stayEvery == 0) {
                            continue;
                        }
                        int slot = random.nextInt(inFlight);
                        if (pending[slot] != null && queue.remove(pending[slot])) {
                            left.incrementAndGet(pending[slot]);
                        }
                        pending[slot] = element;
                    }
                    for (Integer element : pending) {
                        if (element != null && queue.remove(element)) {
                            left.incrementAndGet(element);
                        }
                    }
                }));
            }
            for (Future<?> worker : workers) {
                worker.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(queue.size()).isEqualTo(threads * iterations / stayEvery);
        int[] lastOfThread = new int[threads];
        Arrays.fill(lastOfThread, -1);
        int outOfOrder = 0;
        Integer polled;
        while ((polled = queue.poll()) != null) {
            left.incrementAndGet(polled);
            int thread = polled / iterations;
            if (polled < lastOfThread[thread]) {
                outOfOrder++;
            }
            lastOfThread[thread] = polled;
        }
        assertThat(outOfOrder).as("elements that were polled after a newer element of the same thread").isZero();
        assertEachElementLeftOnce(queue, left, threads * iterations);
    }

    /**
     * One thread adds an element and removes it at once, another thread polls all the time, so
     * remove() and poll() often want the same element. Each element must leave the queue one
     * time: its remove() returns true, or one poll() returns it, and not both.
     */
    @Test
    @Timeout(120)
    public void testPollAndRemoveOfSameElement() throws Exception {
        FastRemovalQueue<Integer> queue = new FastRemovalQueue<>();
        int elements = 500_000;
        AtomicIntegerArray left = new AtomicIntegerArray(elements);
        AtomicBoolean running = new AtomicBoolean(true);

        Thread poller = new Thread(() -> {
            while (running.get()) {
                Integer polled = queue.poll();
                if (polled != null) {
                    left.incrementAndGet(polled);
                }
            }
        });
        poller.start();
        try {
            for (int i = 0; i < elements; i++) {
                Integer element = i;
                queue.add(element);
                if (queue.remove(element)) {
                    left.incrementAndGet(element);
                }
            }
        } finally {
            running.set(false);
            poller.join();
        }

        assertEachElementLeftOnce(queue, left, elements);
    }

    @Test
    public void testIsEmptyLooksBehindRemovedHead() {
        FastRemovalQueue<Integer> queue = new FastRemovalQueue<>();
        assertThat(queue.isEmpty()).isTrue();

        queue.add(1);
        queue.add(2);
        queue.add(3);
        assertThat(queue.isEmpty()).isFalse();

        // 2 is removed while 1 is in front of it, then 1 is polled: 3 is the only element left
        assertThat(queue.remove(2)).isTrue();
        assertThat(queue.poll()).isEqualTo(1);
        assertThat(queue.isEmpty()).isFalse();

        assertThat(queue.remove(3)).isTrue();
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.poll()).isNull();
    }

    /**
     * A queue that holds one element must be as cheap to iterate after long use as a new one.
     * The use: one element stays in front while more and more elements are added and removed
     * behind it, then all elements are polled, 3000 times. After that, in the used queue and
     * in a new one, one element stays while 20,000 others are added and removed behind it.
     * <p>
     * An element that was removed and that the queue still holds can be seen from outside only
     * by what it costs, so this test compares times. It takes the best of 20 measurements for
     * each queue, in turn. Before the fix the used queue held one node more for each time it
     * was used, and an iteration cost 55 times that of the new queue. Now the factor is 1 to 2.
     */
    @Test
    public void testIterationCostDoesNotGrowWithEarlierUse() {
        FastRemovalQueue<Integer> used = new FastRemovalQueue<>();
        int next = 0;
        for (int use = 0; use < 3000; use++) {
            used.add(next++);
            int first = next;
            int others = 200 + use;
            for (int i = 0; i < others; i++) {
                used.add(next++);
            }
            for (int i = 0; i < others; i++) {
                used.remove(first + i);
            }
            while (used.poll() != null) {
                // empty the queue
            }
        }

        List<FastRemovalQueue<Integer>> queues = Arrays.asList(used, new FastRemovalQueue<>());
        for (FastRemovalQueue<Integer> queue : queues) {
            queue.add(-1);
            for (int i = 0; i < 20_000; i++) {
                queue.add(-2 - i);
                queue.remove(-2 - i);
            }
            assertThat(queue).containsExactly(-1);
        }

        long[] best = {Long.MAX_VALUE, Long.MAX_VALUE};
        for (int round = 0; round < 20; round++) {
            for (int q = 0; q < best.length; q++) {
                int seen = 0;
                long start = System.nanoTime();
                for (int i = 0; i < 200; i++) {
                    for (Integer element : queues.get(q)) {
                        seen += element;
                    }
                }
                best[q] = Math.min(best[q], System.nanoTime() - start);
                // guards the measurement: each iteration returned the one element
                assertThat(seen).isEqualTo(-200);
            }
        }

        assertThat(best[0])
                .as("time to iterate the used queue 200 times; the new queue took %d ns", best[1])
                .isLessThan(10 * best[1]);
    }

    /**
     * One element is queued. A second thread takes it out while this thread polls, and no
     * add() runs in that time. If the poll() of this thread returns null, the element is gone
     * and no other can come, so isEmpty() must be true.
     * <p>
     * AsyncSemaphore.tryRun() tries again without a pause while poll() returns null and
     * isEmpty() is false. Before the fix that was so in one round of three on 2 CPUs. With
     * 1 CPU the two threads do not overlap, and this test cannot fail.
     */
    @Test
    @Timeout(120)
    public void testIsEmptyAfterPollReturnedNull() throws Exception {
        FastRemovalQueue<Integer> queue = new FastRemovalQueue<>();
        int rounds = 100_000;
        AtomicInteger go = new AtomicInteger();
        AtomicInteger started = new AtomicInteger();
        AtomicInteger done = new AtomicInteger();

        Thread taker = new Thread(() -> {
            for (int round = 1; round <= rounds; round++) {
                while (go.get() != round) {
                    Thread.yield();
                }
                started.set(round);
                queue.poll();
                done.set(round);
            }
        });
        taker.start();

        int polledNull = 0;
        int disagreed = 0;
        for (int round = 1; round <= rounds; round++) {
            queue.add(round);
            go.set(round);
            while (started.get() != round) {
                Thread.yield();
            }
            if (queue.poll() == null) {
                polledNull++;
                if (!queue.isEmpty()) {
                    disagreed++;
                }
            }
            while (done.get() != round) {
                Thread.yield();
            }
        }
        taker.join();

        // guards the test itself: the other thread must have taken the element in many rounds
        assertThat(polledNull).isGreaterThan(rounds / 100);
        assertThat(disagreed)
                .as("rounds of %d in which poll() returned null and isEmpty() was false after it", polledNull)
                .isZero();
    }

    /** Polls what is left, then checks that each of the first {@code added} elements left one time. */
    private static void assertEachElementLeftOnce(FastRemovalQueue<Integer> queue, AtomicIntegerArray left, int added) {
        Integer remaining;
        while ((remaining = queue.poll()) != null) {
            left.incrementAndGet(remaining);
        }
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.size()).isZero();

        int lost = 0;
        int twice = 0;
        for (int element = 0; element < added; element++) {
            int count = left.get(element);
            if (count == 0) {
                lost++;
            } else if (count > 1) {
                twice++;
            }
        }
        assertThat(lost).as("elements that did not leave the queue").isZero();
        assertThat(twice).as("elements that left the queue more than one time").isZero();
    }
}
