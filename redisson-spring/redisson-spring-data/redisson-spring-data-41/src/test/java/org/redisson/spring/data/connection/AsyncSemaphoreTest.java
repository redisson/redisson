package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.redisson.misc.AsyncSemaphore;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

public class AsyncSemaphoreTest {

    @Test
    void testCancelledWaiter() {
        AsyncSemaphore semaphore = new AsyncSemaphore(0);

        CompletableFuture<Void> waiter = semaphore.acquire();
        waiter.complete(null);

        semaphore.release();

        assertThat(semaphore.getCounter()).isEqualTo(1);
    }
}