package org.redisson.misc;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class AsyncSemaphoreTest {

    @RepeatedTest(2)
    void testReleaseRacingAcquire() throws Exception {
        ExecutorService racers = Executors.newFixedThreadPool(2);
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);

        try {
            while (System.currentTimeMillis() < deadline) {
                final AsyncSemaphore sem = new AsyncSemaphore(0);
                final CyclicBarrier fireTogether = new CyclicBarrier(2);

                AtomicReference<CompletableFuture<Void>> waiter = new AtomicReference<>();

                Future<?> acquirer = racers.submit(() -> {
                    fireTogether.await();
                    waiter.set(sem.acquire());
                    return null;
                });
                Future<?> releaser = racers.submit(() -> {
                    fireTogether.await();
                    sem.release();
                    return null;
                });
                acquirer.get();
                releaser.get();

                CompletableFuture<Void> f = waiter.get();
                f.get(1, TimeUnit.MILLISECONDS);
            }
        } finally {
            racers.shutdownNow();
        }
    }

    @Test
    void testCancelledWaiter() {
        AsyncSemaphore semaphore = new AsyncSemaphore(0);

        CompletableFuture<Void> waiter = semaphore.acquire();
        waiter.complete(null);

        semaphore.release();

        assertThat(semaphore.getCounter()).isEqualTo(1);
    }

    /**
     * tryForkAndRun() raises tasksLatch by CAS and then calls executorService.submit().
     * ConnectionsHolder builds its pool semaphore over the shared event loop group, so once
     * that group starts shutting down submit() throws RejectedExecutionException, the forked
     * tryRun() never happens, and the release-cascade stops dead. Because release() is reached
     * from inside a completion callback, CompletableFuture captures the throw into a discarded
     * dependent future and the failure is silent.
     */
    @Test
    @Timeout(60)
    void testRejectedForkDoesNotStrandWaiters() throws Exception {
        int waiters = 300;
        AtomicInteger rejections = new AtomicInteger();
        ThreadPoolExecutor executor = rejectingExecutor(rejections);
        AsyncSemaphore semaphore = new AsyncSemaphore(0, executor);

        List<CompletableFuture<Void>> queued = new ArrayList<>();
        for (int i = 0; i < waiters; i++) {
            CompletableFuture<Void> f = semaphore.acquire();
            queued.add(f);
            // each waiter hands its permit straight back, as the pubsub and connection
            // pool callers do; this is what drives stackSize past the fork threshold
            f.thenRun(semaphore::release);
        }

        // the fork target dies while waiters are still queued
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        semaphore.release();

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (System.currentTimeMillis() < deadline && completedCount(queued) < waiters) {
            Thread.sleep(10);
        }

        try {
            // guards the test itself: without a rejected fork this proves nothing
            assertThat(rejections.get())
                    .as("the fork threshold was never crossed, so no rejection was exercised")
                    .isPositive();
            assertThat(semaphore.getCounter() > 0 && semaphore.queueSize() > 0)
                    .as("semaphore is wedged: %d free permit(s) with %d waiters still queued",
                            semaphore.getCounter(), semaphore.queueSize())
                    .isFalse();
            assertThat(completedCount(queued))
                    .as("waiters stranded after the fork was rejected")
                    .isEqualTo(waiters);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * The same rejection also leaks tasksLatch: it is raised before submit() and only lowered
     * inside the submitted task, so a rejected fork inflates it permanently. Since the fork
     * threshold is {@code 25 * tasksLatch}, every occurrence further weakens the stack overflow
     * guard the mechanism exists to provide.
     */
    @Test
    @Timeout(60)
    void testRejectedForkDoesNotLeakTasksLatch() throws Exception {
        AtomicInteger rejections = new AtomicInteger();
        ThreadPoolExecutor executor = rejectingExecutor(rejections);
        AsyncSemaphore semaphore = new AsyncSemaphore(0, executor);

        for (int i = 0; i < 300; i++) {
            semaphore.acquire().thenRun(semaphore::release);
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        int before = tasksLatchOf(semaphore);
        semaphore.release();
        Thread.sleep(500);
        int after = tasksLatchOf(semaphore);

        try {
            assertThat(rejections.get())
                    .as("the fork threshold was never crossed, so no rejection was exercised")
                    .isPositive();
            assertThat(after)
                    .as("tasksLatch was raised for a fork that never ran and is never lowered again")
                    .isEqualTo(before);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * tryRun() treats listeners.poll() and listeners.isEmpty() as one consistent view: a null
     * poll with a non-empty queue makes it {@code continue} with no backoff and no yield, while
     * churning the shared counter on every pass. FastRemovalQueue publishes to its index before
     * its queue, and claims a node before unindexing it, so the two reads can disagree in both
     * directions.
     * <p>
     * The retry itself cannot simply be bounded: a thread that gives up while holding the
     * speculatively decremented permit can leave a free permit next to a queued waiter. The two
     * views have to agree instead.
     */
    @Test
    @Timeout(30)
    void testStalledRemovalDoesNotStallPollers() throws Exception {
        FastRemovalQueue<Object> listeners = new FastRemovalQueue<>();
        StallingElement element = new StallingElement();
        listeners.add(element);

        // Park a thread between node.claim() and index.remove(...). The blocking hashCode() is
        // only a way to hold that window open on demand; the scheduler opens the same window
        // whenever it deschedules a thread at that point.
        element.armStall();
        Thread staller = new Thread(listeners::poll, "stalled-poller");
        staller.start();
        assertThat(element.awaitStalled()).as("stalling thread never reached the removal").isTrue();

        boolean viewsDisagree;
        try {
            // The node is out of the queue and claimed, but still indexed. tryRun() reads exactly
            // this pair and, when they disagree, spins with no yield and no backoff for as long as
            // the other thread stays parked - on a Netty event loop thread, that is blocked I/O.
            viewsDisagree = listeners.poll() == null && !listeners.isEmpty();
        } finally {
            element.releaseStall();
            staller.join(TimeUnit.SECONDS.toMillis(10));
        }

        assertThat(viewsDisagree)
                .as("poll() returned null while isEmpty() reported non-empty, so tryRun() would busy-wait")
                .isFalse();
    }

    /**
     * The publishing half of the same invariant. An element must not be counted until it is
     * reachable from the queue: with a single consumer and no removals, isEmpty() reporting
     * non-empty has to mean the very next poll() succeeds. Counting first would let tryRun()
     * see a waiter it cannot take, and spin until the adder catches up.
     */
    @Test
    @Timeout(60)
    void testPublishedElementIsImmediatelyPollable() throws Exception {
        FastRemovalQueue<Object> listeners = new FastRemovalQueue<>();
        AtomicBoolean stop = new AtomicBoolean();
        AtomicLong advertisedButAbsent = new AtomicLong();
        AtomicLong polled = new AtomicLong();

        Thread adder = new Thread(() -> {
            while (!stop.get()) {
                listeners.add(new Object());
            }
        }, "adder");
        adder.start();
        try {
            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(3);
            while (System.currentTimeMillis() < deadline) {
                // sole consumer, and nothing is ever removed, so nothing can take the element
                // out from between these two calls
                if (!listeners.isEmpty()) {
                    if (listeners.poll() == null) {
                        advertisedButAbsent.incrementAndGet();
                    } else {
                        polled.incrementAndGet();
                    }
                }
            }
        } finally {
            stop.set(true);
            adder.join(TimeUnit.SECONDS.toMillis(10));
        }

        // guards the test itself: the poller has to have seen a non-empty queue at least once
        assertThat(polled.get()).as("the adder never got ahead of the poller").isPositive();
        assertThat(advertisedButAbsent.get())
                .as("isEmpty() advertised work that the immediately following poll() could not return")
                .isZero();
    }

    /**
     * FastRemovalQueue.remove() drops the index entry and nulls the node but leaves the node in
     * the backing queue, where only a later successful poll() discards it. A pool held at zero
     * permits through an outage polls nothing, so timed-out waiters pile up invisibly:
     * queueSize() keeps reporting 0. Recovery then pays for the whole walk on the first poll.
     */
    @Test
    @Timeout(60)
    void testCancelledWaitersDoNotRetainQueueNodes() throws Exception {
        AsyncSemaphore semaphore = new AsyncSemaphore(0);
        int waiters = 100_000;

        List<CompletableFuture<Void>> queued = new ArrayList<>();
        for (int i = 0; i < waiters; i++) {
            queued.add(semaphore.acquire());
        }
        // guards the test itself: retention cannot be measured if nothing was ever queued
        assertThat(semaphore.queueSize()).isEqualTo(waiters);
        assertThat(backingNodeCountOf(semaphore)).isEqualTo(waiters);

        queued.forEach(f -> f.completeExceptionally(new TimeoutException()));

        assertThat(semaphore.queueSize()).isZero();
        assertThat(backingNodeCountOf(semaphore))
                .as("dead nodes retained in the backing queue while queueSize() reports 0")
                .isLessThan(1000);
    }

    /**
     * removeListeners() discards queued waiters without completing them, so acquire()'s caller
     * never observes success or failure and simply waits forever - while the semaphore reports a
     * free permit and an empty queue. ClientConnectionsEntry calls this on the live pool
     * semaphore from nodeDown() and reattachPubSub().
     * <p>
     * The rewritten FastRemovalQueue.clear() also widens the race: add() captures the state on
     * entry and writes to it later, so a concurrent acquire() can land in the discarded state.
     */
    @Test
    @Timeout(30)
    void testRemoveListenersCompletesWaiters() throws Exception {
        AsyncSemaphore semaphore = new AsyncSemaphore(0);

        CompletableFuture<Void> waiter = semaphore.acquire();
        // guards the test itself: with no permit the waiter must still be pending here
        assertThat(waiter).isNotDone();

        semaphore.removeListeners();

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(2);
        while (System.currentTimeMillis() < deadline && !waiter.isDone()) {
            Thread.sleep(10);
        }

        assertThat(waiter)
                .as("waiter abandoned by removeListeners(): counter=%d queueSize=%d, "
                                + "so the semaphore looks idle while its caller blocks forever",
                        semaphore.getCounter(), semaphore.queueSize())
                .isDone();
        // completing it normally would hand out a permit the semaphore never had
        assertThat(waiter)
                .as("removeListeners() must fail its waiters, not satisfy them")
                .isCompletedExceptionally();
        assertThat(semaphore.getCounter())
                .as("removeListeners() must not invent permits")
                .isZero();
    }

    /**
     * tryRun() decrements the counter speculatively before testing it, so getCounter() exposes
     * values that never correspond to a real permit count. IdleConnectionWatcher feeds it into
     * {@code maximumAmount - getCounter() + connections.size() > minimumAmount}, where a low
     * read biases the decision toward closing idle connections.
     */
    @Test
    @Timeout(60)
    void testCounterNeverReadsBelowZero() throws Exception {
        int permits = 4;
        int workers = 8;
        AsyncSemaphore semaphore = new AsyncSemaphore(permits);
        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger lowest = new AtomicInteger(Integer.MAX_VALUE);
        AtomicInteger acquisitions = new AtomicInteger();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            threads.add(new Thread(() -> {
                while (!stop.get()) {
                    semaphore.acquire().join();
                    acquisitions.incrementAndGet();
                    semaphore.release();
                }
            }));
        }
        threads.add(new Thread(() -> {
            while (!stop.get()) {
                lowest.accumulateAndGet(semaphore.getCounter(), Math::min);
            }
        }));

        threads.forEach(Thread::start);
        Thread.sleep(3000);
        stop.set(true);
        for (Thread t : threads) {
            t.join();
        }

        // guards the test itself: an idle semaphore never dips below zero
        assertThat(acquisitions.get())
                .as("workload never ran, so the counter was never under contention")
                .isGreaterThan(workers * 100);
        assertThat(lowest.get())
                .as("getCounter() observed a value below zero on a semaphore with %d permits", permits)
                .isGreaterThanOrEqualTo(0);
    }

    private static long completedCount(List<CompletableFuture<Void>> futures) {
        return futures.stream().filter(CompletableFuture::isDone).count();
    }

    /** Behaves like a shutting-down event loop group, but counts what it turned away. */
    private static ThreadPoolExecutor rejectingExecutor(AtomicInteger rejections) {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                (task, executor) -> {
                    rejections.incrementAndGet();
                    throw new RejectedExecutionException("executor is shut down");
                });
    }

    /**
     * Element whose hashCode() can be made to block once, on demand. FastRemovalQueue.poll()
     * hashes the value inside index.remove(value, node), i.e. after the node has already been
     * taken out of the queue and claimed, which is precisely the window under test.
     */
    private static final class StallingElement {

        private final CountDownLatch stalled = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);
        private volatile boolean armed;

        void armStall() {
            armed = true;
        }

        boolean awaitStalled() throws InterruptedException {
            return stalled.await(10, TimeUnit.SECONDS);
        }

        void releaseStall() {
            resume.countDown();
        }

        @Override
        public int hashCode() {
            if (armed) {
                armed = false;
                stalled.countDown();
                try {
                    resume.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return System.identityHashCode(this);
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }
    }

    // The two helpers below reach into private state because AsyncSemaphore exposes no other
    // way to observe the fork bookkeeping or the retained nodes behind queueSize().

    private static int tasksLatchOf(AsyncSemaphore semaphore) throws Exception {
        Field field = AsyncSemaphore.class.getDeclaredField("tasksLatch");
        field.setAccessible(true);
        return ((AtomicInteger) field.get(semaphore)).get();
    }

    private static int backingNodeCountOf(AsyncSemaphore semaphore) throws Exception {
        Field listenersField = AsyncSemaphore.class.getDeclaredField("listeners");
        listenersField.setAccessible(true);
        Object listeners = listenersField.get(semaphore);

        Field stateField = listeners.getClass().getDeclaredField("state");
        stateField.setAccessible(true);
        Object state = stateField.get(listeners);

        Field nodesField = state.getClass().getDeclaredField("queue");
        nodesField.setAccessible(true);
        return ((Collection<?>) nodesField.get(state)).size();
    }
}