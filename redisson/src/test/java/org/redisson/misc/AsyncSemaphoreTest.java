package org.redisson.misc;

import org.assertj.core.api.Assertions;
import org.joor.Reflect;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

public class AsyncSemaphoreTest {

    @Test
    void testCancelledWaiterDoesNotInflateCounter() {
        AsyncSemaphore semaphore = new AsyncSemaphore(1);
        Assertions.assertThat(semaphore.getCounter()).isEqualTo(1);

        // plant a waiter that already completed exceptionally (a cancelled/timed-out acquire),
        // bypassing acquire()'s auto-removal, so tryRun polls a dead waiter
        CompletableFuture<Void> dead = new CompletableFuture<>();
        dead.completeExceptionally(new RuntimeException("cancelled"));
        Object listeners = Reflect.on(semaphore).get("listeners");
        Reflect.on(listeners).call("add", dead);

        Reflect.on(semaphore).call("tryRun");

        // a dead waiter must not leave the permit counter above the configured permits
        Assertions.assertThat(semaphore.getCounter()).isEqualTo(1);
    }
}
