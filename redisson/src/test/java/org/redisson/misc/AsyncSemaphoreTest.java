package org.redisson.misc;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
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
}