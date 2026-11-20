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
     * Reproducer for https://github.com/redisson/redisson/issues/5971: 1000 parallel requests
     * over a pool of one connection, after which every command fails with "Unable to acquire
     * connection!" for good, although the connection itself is healthy.
     */
    @Test
    @Timeout(60)
    void testReleaseCascadeDoesNotOverflowStack() throws Exception {
        int waiters = 100_000;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AsyncSemaphore semaphore = new AsyncSemaphore(1, executor);
        AtomicInteger nested = new AtomicInteger();
        AtomicInteger deepest = new AtomicInteger();
        CountDownLatch handedOn = new CountDownLatch(waiters);

        try {
            assertThat(semaphore.acquire()).isCompleted();

            for (int i = 0; i < waiters; i++) {
                semaphore.acquire().thenAccept(r -> {
                    deepest.accumulateAndGet(nested.incrementAndGet(), Math::max);
                    semaphore.release();
                    nested.decrementAndGet();
                    handedOn.countDown();
                });
            }

            assertThat(semaphore.queueSize()).isEqualTo(waiters);

            semaphore.release();

            boolean drained = handedOn.await(10, TimeUnit.SECONDS);

            assertThat(drained).isTrue();

            assertThat(semaphore.acquire()).isCompleted();

            assertThat(deepest.get())
                    .as("hand-overs nested at once, which the fork is there to keep near 25")
                    .isLessThan(100);
        } finally {
            executor.shutdownNow();
        }
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

    /**
     * redisson#7447. Each waiter's callback ends with release(), as the pub/sub callers do on a
     * channel that is already subscribed. One release() then passes the permit down the whole
     * queue. If each hand-off starts inside the callback of the previous one, the releasing
     * thread overflows. CompletableFuture catches the StackOverflowError in a stage that nobody
     * reads, and the release() of that waiter never runs. The counter then stays at zero with
     * waiters in the queue, for good.
     */
    @Test
    @Timeout(60)
    void testLongQueueDrainsOnSmallStack() throws Exception {
        int waiters = 20_000;
        AsyncSemaphore semaphore = new AsyncSemaphore(1);
        semaphore.acquire().join();

        AtomicInteger ran = new AtomicInteger();
        for (int i = 0; i < waiters; i++) {
            semaphore.acquire().thenRun(() -> {
                ran.incrementAndGet();
                semaphore.release();
            });
        }

        // 256 KB, as in the reproducer of the issue. A cold JVM with a default 1 MB stack fails
        // the same way at about 1,000 waiters.
        Thread releaser = new Thread(null, semaphore::release, "releaser", 256 * 1024);
        releaser.start();
        releaser.join();

        assertThat(ran.get()).as("waiters that got the permit, of %d", waiters).isEqualTo(waiters);
        assertThat(semaphore.getCounter()).isEqualTo(1);
        assertThat(semaphore.queueSize()).isZero();
    }

    /**
     * The same queue in the shape that PublishSubscribeService gives it. Each subscribe waits
     * for the stripe semaphore and then uses a semaphore of its own, which has two waiters: the
     * subscribe work, and the release of the stripe. So the release() that continues the queue
     * of the stripe comes from a callback of a different semaphore, which is itself inside a
     * callback of the stripe.
     */
    @Test
    @Timeout(60)
    void testLongQueueOfTwoSemaphoresDrainsOnSmallStack() throws Exception {
        int subscribes = 20_000;
        AsyncSemaphore stripe = new AsyncSemaphore(1);
        stripe.acquire().join();

        AtomicInteger ran = new AtomicInteger();
        for (int i = 0; i < subscribes; i++) {
            AsyncSemaphore call = new AsyncSemaphore(0);
            stripe.acquire().thenRun(call::release);
            call.acquire().thenRun(() -> {
                ran.incrementAndGet();
                call.release();
            });
            call.acquire().thenRun(stripe::release);
        }

        Thread releaser = new Thread(null, stripe::release, "releaser", 256 * 1024);
        releaser.start();
        releaser.join();

        assertThat(ran.get()).as("subscribes that ran, of %d", subscribes).isEqualTo(subscribes);
        assertThat(stripe.getCounter()).isEqualTo(1);
        assertThat(stripe.queueSize()).isZero();
    }

    /**
     * redisson#7447, the second way to grow the stack. A callback can take the same semaphore
     * again after its release(): RedissonLock.tryLockAsync() calls unsubscribe() when a
     * subscribe completes after the wait time is over, and unsubscribe() starts with acquire().
     * The permit is free at that moment, so this acquire() hands it to the next waiter of the
     * queue. If that hand-off starts inside the callback, each waiter adds a level to the stack
     * again, although no release() does.
     */
    @Test
    @Timeout(60)
    void testLongQueueOfCallbacksThatAcquireAgainDrainsOnSmallStack() throws Exception {
        int waiters = 20_000;
        AsyncSemaphore semaphore = new AsyncSemaphore(1);
        semaphore.acquire().join();

        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        for (int i = 0; i < waiters; i++) {
            semaphore.acquire().thenRun(() -> {
                first.incrementAndGet();
                semaphore.release();
                // the unsubscribe: the same semaphore again, given back in its own callback
                semaphore.acquire().thenRun(() -> {
                    second.incrementAndGet();
                    semaphore.release();
                });
            });
        }

        Thread releaser = new Thread(null, semaphore::release, "releaser", 256 * 1024);
        releaser.start();
        releaser.join();

        assertThat(first.get()).as("first callbacks that ran, of %d", waiters).isEqualTo(waiters);
        assertThat(second.get()).as("second callbacks that ran, of %d", waiters).isEqualTo(waiters);
        assertThat(semaphore.getCounter()).isEqualTo(1);
        assertThat(semaphore.queueSize()).isZero();
    }

    /**
     * A release() inside a callback must not run the next waiter inside that callback. The next
     * waiter runs after the callback returns. This order keeps the stack flat.
     */
    @Test
    void testHandOffWaitsForTheReleasingCallback() {
        AsyncSemaphore semaphore = new AsyncSemaphore(0);
        List<String> events = new ArrayList<>();
        semaphore.acquire().thenRun(() -> {
            events.add("first starts");
            semaphore.release();
            events.add("first ends");
        });
        semaphore.acquire().thenRun(() -> events.add("second"));

        semaphore.release();

        assertThat(events).containsExactly("first starts", "first ends", "second");
    }

    /**
     * A callback can release more permits than it took. Each release() that waits for the
     * callback to return is one hand-off, so none of these permits must stay in the counter
     * while waiters are in the queue.
     */
    @Test
    void testEveryPermitReleasedInsideACallbackIsHandedOut() {
        AsyncSemaphore semaphore = new AsyncSemaphore(0);
        AtomicInteger ran = new AtomicInteger();
        semaphore.acquire().thenRun(() -> {
            ran.incrementAndGet();
            semaphore.release();
            semaphore.release();
            semaphore.release();
        });
        for (int i = 0; i < 3; i++) {
            semaphore.acquire().thenRun(ran::incrementAndGet);
        }

        semaphore.release();

        assertThat(ran.get()).isEqualTo(4);
        assertThat(semaphore.getCounter()).isZero();
        assertThat(semaphore.queueSize()).isZero();
    }

    /**
     * An acquire() inside a callback, with no waiter in the queue before it, still gets a free
     * permit before it returns. Nothing is left to the loop in this case: the permit can only go
     * to the new request, which has no callbacks yet. A callback that takes a permit and waits
     * for it would block for good otherwise.
     */
    @Test
    void testAcquireInsideACallbackGetsAFreePermitAtOnce() {
        AsyncSemaphore semaphore = new AsyncSemaphore(0);
        AtomicBoolean doneOnReturn = new AtomicBoolean();
        semaphore.acquire().thenRun(() -> {
            semaphore.release();
            doneOnReturn.set(semaphore.acquire().isDone());
        });

        semaphore.release();

        assertThat(doneOnReturn).isTrue();
        assertThat(semaphore.getCounter()).isZero();
        assertThat(semaphore.queueSize()).isZero();
    }

    /**
     * An acquire() inside a callback, with a waiter in the queue before it, must not run that
     * waiter inside the callback. The waiter runs after the callback returns, and the new
     * request gets its turn after the waiter.
     */
    @Test
    void testAcquireBehindAWaiterInsideACallbackWaitsForTheCallback() {
        AsyncSemaphore semaphore = new AsyncSemaphore(0);
        List<String> events = new ArrayList<>();
        semaphore.acquire().thenRun(() -> {
            events.add("first starts");
            semaphore.release();
            semaphore.acquire().thenRun(() -> events.add("third"));
            events.add("first ends");
        });
        semaphore.acquire().thenRun(() -> {
            events.add("second");
            semaphore.release();
        });

        semaphore.release();

        assertThat(events).containsExactly("first starts", "first ends", "second", "third");
        assertThat(semaphore.getCounter()).isZero();
        assertThat(semaphore.queueSize()).isZero();
    }

    /**
     * The semaphore records the waiter that a thread completes, and release() reads this record
     * to know that it is inside a callback. The record must be gone when the callback returns.
     * A release() that the same thread makes later would otherwise wait for a callback that has
     * already returned, and its permit would stay in the counter.
     */
    @Test
    void testReleaseAfterACallbackReturnedHandsThePermitOver() {
        AsyncSemaphore semaphore = new AsyncSemaphore(0);
        AtomicInteger ran = new AtomicInteger();
        semaphore.acquire().thenRun(ran::incrementAndGet);
        semaphore.release();
        // guards the test itself: the first release() must have completed a waiter on this thread
        assertThat(ran.get()).isEqualTo(1);

        CompletableFuture<Void> second = semaphore.acquire();
        semaphore.release();

        assertThat(second).isCompleted();
    }

    /**
     * Hand-offs of different threads do not wait for each other. With two permits, a callback
     * that is slow on one thread must not keep the second permit from a waiter. Each callback
     * here returns only after the other one has started.
     */
    @Test
    @Timeout(60)
    void testHandOffsOnTwoThreadsRunAtTheSameTime() throws Exception {
        AsyncSemaphore semaphore = new AsyncSemaphore(0);
        CyclicBarrier bothRun = new CyclicBarrier(2);
        AtomicInteger met = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            semaphore.acquire().thenRun(() -> {
                try {
                    bothRun.await(10, TimeUnit.SECONDS);
                    met.incrementAndGet();
                } catch (Exception e) {
                    // the other callback did not start while this one ran
                }
            });
        }

        Thread first = new Thread(semaphore::release);
        Thread second = new Thread(semaphore::release);
        first.start();
        second.start();
        first.join();
        second.join();

        assertThat(met.get()).as("callbacks that ran at the same time").isEqualTo(2);
    }

    /**
     * With an executor the hand-offs do not nest either. Before, they nested until the fork
     * threshold, which is about 25 levels, and each level is as deep as the callbacks of a
     * waiter. With the pub/sub callbacks and an executor, a stack of 256 KB overflowed at 29
     * levels.
     */
    @Test
    @Timeout(60)
    void testHandOffsDoNotNestWithExecutor() throws Exception {
        int waiters = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AsyncSemaphore semaphore = new AsyncSemaphore(0, executor);
        AtomicInteger nested = new AtomicInteger();
        AtomicInteger deepest = new AtomicInteger();
        CountDownLatch handedOn = new CountDownLatch(waiters);

        try {
            for (int i = 0; i < waiters; i++) {
                semaphore.acquire().thenRun(() -> {
                    deepest.accumulateAndGet(nested.incrementAndGet(), Math::max);
                    semaphore.release();
                    nested.decrementAndGet();
                    handedOn.countDown();
                });
            }

            semaphore.release();

            assertThat(handedOn.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(deepest.get()).as("hand-offs nested at once").isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
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