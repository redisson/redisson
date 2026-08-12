package org.redisson.misc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AsyncSemaphore guards subscriptions and connection pools, so a permit it invents or loses
 * shows up as an over-sized pool or as a caller which waits forever.
 */
public class AsyncSemaphoreTest {

    /**
     * A waiter completed by somebody else is skipped by tryRun(), which handed its reservation
     * back and then fell through to the trailing increment, counting the same permit twice.
     */
    @Test
    public void testSkippedWaiterDoesNotDuplicatePermit() {
        AsyncSemaphore semaphore = new AsyncSemaphore(0);

        // stays queued, since acquire() only unlinks a waiter that fails
        CompletableFuture<Void> alreadyDone = semaphore.acquire();
        assertTrue(alreadyDone.complete(null));

        semaphore.release();

        assertEquals(1, semaphore.getCounter(), "a single release() produced more than one permit");

        assertTrue(semaphore.acquire().isDone());
        assertFalse(semaphore.acquire().isDone(), "semaphore handed out a permit it never had");
    }

    /**
     * removeListeners() dropped the queued waiters without completing them, so every caller
     * of acquire() kept waiting for a permit nobody would ever grant.
     */
    @Test
    @Timeout(30)
    public void testRemoveListenersCompletesWaiters() {
        AsyncSemaphore semaphore = new AsyncSemaphore(1);
        semaphore.acquire().join();

        CompletableFuture<Void> first = semaphore.acquire();
        CompletableFuture<Void> second = semaphore.acquire();
        assertFalse(first.isDone());
        assertFalse(second.isDone());

        semaphore.removeListeners();

        // a future completed with a CancellationException rethrows it unwrapped
        assertThrows(CancellationException.class, () -> first.get(5, TimeUnit.SECONDS));
        assertThrows(CancellationException.class, () -> second.get(5, TimeUnit.SECONDS));
        assertEquals(0, semaphore.queueSize());
    }

    /**
     * getCounter() is read as the number of free permits, so it must not report the negative
     * bookkeeping value the counter holds while waiters are queued.
     */
    @Test
    public void testCounterNeverReadsBelowZero() {
        AsyncSemaphore semaphore = new AsyncSemaphore(1);
        semaphore.acquire().join();
        assertEquals(0, semaphore.getCounter());

        semaphore.acquire();
        semaphore.acquire();
        assertEquals(0, semaphore.getCounter(), "queued waiters are reported as negative permits");

        semaphore.release();
        assertEquals(0, semaphore.getCounter());
    }

    /**
     * Liveness check under contention - not a discriminating regression test, it only fails if
     * permits get stranded often enough to starve a caller.
     */
    @Test
    @Timeout(60)
    public void testNoWaiterIsStarvedUnderContention() throws Exception {
        int permits = 2;
        int tasks = 2000;
        AsyncSemaphore semaphore = new AsyncSemaphore(permits);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(tasks);

        try {
            for (int i = 0; i < tasks; i++) {
                pool.submit(() -> {
                    semaphore.acquire().thenAccept(r -> {
                        semaphore.release();
                        done.countDown();
                    });
                });
            }

            assertTrue(done.await(30, TimeUnit.SECONDS),
                    "waiters were starved, " + done.getCount() + " of " + tasks + " never acquired");
        } finally {
            pool.shutdownNow();
        }

        assertThat(semaphore.getCounter()).isEqualTo(permits);
        assertThat(semaphore.queueSize()).isZero();
    }
}
