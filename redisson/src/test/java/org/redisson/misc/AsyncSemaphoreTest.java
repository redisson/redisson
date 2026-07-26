package org.redisson.misc;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class AsyncSemaphoreTest {

    @Test
    void testReleaseWithExecutorCompletesWaiterAsynchronously() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch unblock = new CountDownLatch(1);

        try {
            executor.execute(() -> {
                taskStarted.countDown();
                try {
                    unblock.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(taskStarted.await(1, TimeUnit.SECONDS)).isTrue();

            AsyncSemaphore semaphore = new AsyncSemaphore(0, executor);

            CompletableFuture<Void> waiter = semaphore.acquire();
            semaphore.release();

            assertThat(waiter).isNotDone();

            unblock.countDown();
            waiter.get(1, TimeUnit.SECONDS);

            assertThat(waiter).isCompleted();
        } finally {
            unblock.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void testReleaseFallsBackIfExecutorRejectsWakeup() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.shutdown();
        AsyncSemaphore semaphore = new AsyncSemaphore(0, executor);

        CompletableFuture<Void> waiter = semaphore.acquire();

        assertThatCode(semaphore::release).doesNotThrowAnyException();
        assertThat(waiter).isCompleted();
        assertThat(semaphore.getCounter()).isZero();
    }

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
}