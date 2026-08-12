package org.redisson.connection;

import mockit.Mocked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.redisson.client.RedisConnection;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionsHolderCancellationTest {

    /**
     * A node going down cancels everybody queued for a connection. The waiter's future was
     * consumed with thenAccept(), so the cancellation went nowhere and the caller waited for a
     * connection which was never coming.
     */
    @Test
    @Timeout(30)
    public void testCancelledWaiterFailsAcquireConnection(@Mocked ServiceManager serviceManager) {
        ConnectionsHolder<RedisConnection> holder = new ConnectionsHolder<>(
                null, 1, client -> new CompletableFuture<>(), serviceManager, false);

        // the only permit is taken, so the next caller has to queue up
        holder.getFreeConnectionsCounter().acquire().join();
        CompletableFuture<RedisConnection> result = holder.acquireConnection(null);
        assertFalse(result.isDone());

        holder.getFreeConnectionsCounter().removeListeners();

        assertThrows(CancellationException.class, () -> result.get(5, TimeUnit.SECONDS),
                "acquireConnection() never completes once its wait is cancelled");
    }
}
