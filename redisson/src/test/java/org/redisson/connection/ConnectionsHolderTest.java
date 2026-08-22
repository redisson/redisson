package org.redisson.connection;

import mockit.Expectations;
import mockit.Mocked;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.misc.AsyncSemaphore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;


public class ConnectionsHolderTest {

    private MasterSlaveConnectionManager buildManager() {
        Config config = new Config();
        config.setLazyInitialization(true);
        MasterSlaveServersConfig msConfig = config.useMasterSlaveServers();
        msConfig.setMasterAddress("redis://127.0.0.1:6379");
        msConfig.setReadMode(ReadMode.MASTER);
        return new MasterSlaveConnectionManager(msConfig, config);
    }

    private Function<RedisClient, CompletionStage<RedisConnection>> succeedingCallback(
            AtomicInteger createdConnections, RedisConnection... connections) {
        return r -> {
            int index = createdConnections.getAndIncrement();
            return CompletableFuture.completedFuture(connections[index]);
        };
    }

    @Test
    void testFailedInitConnectionReleasesPermitExactlyOnce() {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            int poolMaxSize = 2;
            Function<RedisClient, CompletionStage<RedisConnection>> failingCallback = r -> {
                CompletableFuture<RedisConnection> f = new CompletableFuture<>();
                f.completeExceptionally(new RuntimeException("connect failed"));
                return f;
            };
            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(null, poolMaxSize, failingCallback, manager.getServiceManager(), false);
            AsyncSemaphore counter = holder.getFreeConnectionsCounter();
            Assertions.assertThat(counter.getCounter()).isEqualTo(poolMaxSize);

            CompletableFuture<Void> result = holder.initConnections(poolMaxSize);
            Assertions.assertThat(result).isCompletedExceptionally();

            // a failed init acquires one permit and must release it exactly once; an extra release
            // lifts the counter above the pool max and jams idle eviction so the pool never drains
            Assertions.assertThat(counter.getCounter()).isEqualTo(poolMaxSize);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testSuccessfulInitReleasesEachPermitExactlyOnce(@Mocked RedisClient client,
                                                         @Mocked RedisConnection conn1,
                                                         @Mocked RedisConnection conn2) {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            int poolMaxSize = 2;
            AtomicInteger createdConnections = new AtomicInteger();
            Function<RedisClient, CompletionStage<RedisConnection>> succeedingCallback =
                    succeedingCallback(createdConnections, conn1, conn2);
            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(client, poolMaxSize, succeedingCallback, manager.getServiceManager(), false);
            AsyncSemaphore counter = holder.getFreeConnectionsCounter();
            Assertions.assertThat(counter.getCounter()).isEqualTo(poolMaxSize);

            CompletableFuture<Void> result = holder.initConnections(poolMaxSize);
            Assertions.assertThat(result).isCompleted();

            // each successful init acquires one permit and releases it exactly once, so the counter
            // returns to the pool max — never inflated above it, which would jam idle eviction
            Assertions.assertThat(counter.getCounter()).isEqualTo(poolMaxSize);
            Assertions.assertThat(holder.getFreeConnections()).hasSize(poolMaxSize);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testWarmUpCreatesConnectionsUpToDefinedAmount(@Mocked RedisConnection conn1,
                                                       @Mocked RedisConnection conn2,
                                                       @Mocked RedisConnection conn3) {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            AtomicInteger createdConnections = new AtomicInteger();
            Function<RedisClient, CompletionStage<RedisConnection>> succeedingCallback =
                    succeedingCallback(createdConnections, conn1, conn2, conn3);
            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(null, 4, succeedingCallback, manager.getServiceManager(), false);

            CompletableFuture<Void> result = holder.warmUp(3);
            Assertions.assertThat(result).isCompleted();

            Assertions.assertThat(createdConnections).hasValue(3);
            Assertions.assertThat(holder.getAllConnections()).hasSize(3);
            Assertions.assertThat(holder.getFreeConnections()).hasSize(3);
            Assertions.assertThat(holder.getFreeConnectionsCounter().getCounter()).isEqualTo(4);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testWarmUpDoesNotCreateConnectionsIfAmountAlreadyReached(@Mocked RedisClient client,
                                                                  @Mocked RedisConnection conn1,
                                                                  @Mocked RedisConnection conn2,
                                                                  @Mocked RedisConnection conn3) {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            AtomicInteger createdConnections = new AtomicInteger();
            Function<RedisClient, CompletionStage<RedisConnection>> succeedingCallback =
                    succeedingCallback(createdConnections, conn1, conn2, conn3);
            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(client, 4, succeedingCallback, manager.getServiceManager(), false);

            Assertions.assertThat(holder.warmUp(3)).isCompleted();
            Assertions.assertThat(holder.warmUp(2)).isCompleted();

            Assertions.assertThat(createdConnections).hasValue(3);
            Assertions.assertThat(holder.getAllConnections()).hasSize(3);
            Assertions.assertThat(holder.getFreeConnections()).hasSize(3);
            Assertions.assertThat(holder.getFreeConnectionsCounter().getCounter()).isEqualTo(4);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testWarmUpCreatesConnectionsUntilTotalAmountIsReached(@Mocked RedisConnection conn1,
                                                               @Mocked RedisConnection conn2,
                                                               @Mocked RedisConnection conn3) {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            new Expectations() {{
                conn1.isActive(); result = true; minTimes = 0;
                conn2.isActive(); result = true; minTimes = 0;
                conn3.isActive(); result = true; minTimes = 0;
            }};

            AtomicInteger createdConnections = new AtomicInteger();
            Function<RedisClient, CompletionStage<RedisConnection>> succeedingCallback =
                    succeedingCallback(createdConnections, conn1, conn2, conn3);
            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(null, 4, succeedingCallback, manager.getServiceManager(), false);

            Assertions.assertThat(holder.warmUp(3)).isCompleted();
            RedisConnection connection = holder.acquireConnection(null).join();

            CompletableFuture<Void> result = holder.warmUp(3);

            Assertions.assertThat(result).isCompleted();
            Assertions.assertThat(connection).isSameAs(conn1);
            Assertions.assertThat(createdConnections).hasValue(3);
            Assertions.assertThat(holder.getAllConnections()).hasSize(3);
            Assertions.assertThat(holder.getFreeConnections()).hasSize(2);
            Assertions.assertThat(holder.getFreeConnectionsCounter().getCounter()).isEqualTo(3);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testWarmUpCompletesWhenTotalAmountAlreadyReachedEvenIfConnectionIsBorrowed(@Mocked RedisConnection conn1,
                                                                                    @Mocked RedisConnection conn2,
                                                                                    @Mocked RedisConnection conn3,
                                                                                    @Mocked ClientConnectionsEntry entry) {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            new Expectations() {{
                conn1.isActive(); result = true; minTimes = 0;
                conn2.isActive(); result = true; minTimes = 0;
                conn3.isActive(); result = true; minTimes = 0;
                entry.isFreezed(); result = false; minTimes = 0;
            }};

            AtomicInteger createdConnections = new AtomicInteger();
            Function<RedisClient, CompletionStage<RedisConnection>> succeedingCallback =
                    succeedingCallback(createdConnections, conn1, conn2, conn3);
            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(null, 3, succeedingCallback, manager.getServiceManager(), false);

            Assertions.assertThat(holder.warmUp(3)).isCompleted();
            RedisConnection connection = holder.acquireConnection(null).join();

            CompletableFuture<Void> result = holder.warmUp(3);
            Assertions.assertThat(result).isCompleted();

            holder.releaseConnection(entry, connection);

            Assertions.assertThat(result).isCompleted();
            Assertions.assertThat(createdConnections).hasValue(3);
            Assertions.assertThat(holder.getAllConnections()).hasSize(3);
            Assertions.assertThat(holder.getFreeConnections()).hasSize(3);
            Assertions.assertThat(holder.getFreeConnectionsCounter().getCounter()).isEqualTo(3);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testWarmUpFailedConnectionReleasesPermitExactlyOnce() {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            int poolMaxSize = 2;
            Function<RedisClient, CompletionStage<RedisConnection>> failingCallback = r -> {
                CompletableFuture<RedisConnection> f = new CompletableFuture<>();
                f.completeExceptionally(new RuntimeException("connect failed"));
                return f;
            };
            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(null, poolMaxSize, failingCallback, manager.getServiceManager(), false);
            AsyncSemaphore counter = holder.getFreeConnectionsCounter();
            Assertions.assertThat(counter.getCounter()).isEqualTo(poolMaxSize);

            CompletableFuture<Void> result = holder.warmUp(poolMaxSize);
            Assertions.assertThat(result).isCompletedExceptionally();

            Assertions.assertThat(counter.getCounter()).isEqualTo(poolMaxSize);
            Assertions.assertThat(holder.getAllConnections()).isEmpty();
            Assertions.assertThat(holder.getFreeConnections()).isEmpty();
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testWarmUpFailurePreservesExistingConnectionsAndAllowsRetry(@Mocked RedisConnection conn1,
                                                                      @Mocked RedisConnection conn2) {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            AtomicInteger attempts = new AtomicInteger();
            Function<RedisClient, CompletionStage<RedisConnection>> callback = r -> {
                int attempt = attempts.getAndIncrement();
                if (attempt == 1) {
                    CompletableFuture<RedisConnection> f = new CompletableFuture<>();
                    f.completeExceptionally(new RuntimeException("connect failed"));
                    return f;
                }
                return CompletableFuture.completedFuture(attempt == 0 ? conn1 : conn2);
            };
            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(null, 2, callback, manager.getServiceManager(), false);

            Assertions.assertThat(holder.warmUp(1)).isCompleted();
            Assertions.assertThat(holder.warmUp(2)).isCompletedExceptionally();

            Assertions.assertThat(holder.getAllConnections()).containsExactly(conn1);
            Assertions.assertThat(holder.getFreeConnections()).containsExactly(conn1);
            Assertions.assertThat(holder.getFreeConnectionsCounter().getCounter()).isEqualTo(2);

            Assertions.assertThat(holder.warmUp(2)).isCompleted();
            Assertions.assertThat(holder.getAllConnections()).containsExactly(conn1, conn2);
            Assertions.assertThat(holder.getFreeConnections()).containsExactly(conn1, conn2);
            Assertions.assertThat(holder.getFreeConnectionsCounter().getCounter()).isEqualTo(2);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testWarmUpCountsAllCreatedConnections(@Mocked RedisConnection conn1,
                                               @Mocked RedisConnection conn2) {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            new Expectations() {{
                conn1.isActive(); result = false; minTimes = 0;
            }};

            AtomicInteger createdConnections = new AtomicInteger();
            Function<RedisClient, CompletionStage<RedisConnection>> succeedingCallback =
                    succeedingCallback(createdConnections, conn1, conn2);
            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(null, 3, succeedingCallback, manager.getServiceManager(), false);

            CompletableFuture<Void> result = holder.warmUp(2);

            Assertions.assertThat(result).isCompleted();
            Assertions.assertThat(createdConnections).hasValue(2);
            Assertions.assertThat(holder.getAllConnections()).hasSize(2);
            Assertions.assertThat(holder.getFreeConnections()).hasSize(2);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testConcurrentWarmUpsDoNotReserveMoreThanPoolSize(@Mocked RedisConnection conn1,
                                                           @Mocked RedisConnection conn2,
                                                           @Mocked RedisConnection conn3) {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            List<CompletableFuture<RedisConnection>> connectionFutures = new ArrayList<>();
            Function<RedisClient, CompletionStage<RedisConnection>> callback = r -> {
                CompletableFuture<RedisConnection> f = new CompletableFuture<>();
                connectionFutures.add(f);
                return f;
            };
            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(null, 3, callback, manager.getServiceManager(), false);

            CompletableFuture<Void> result1 = holder.warmUp(3);
            CompletableFuture<Void> result2 = holder.warmUp(3);
            CompletableFuture<Void> result3 = holder.warmUp(3);
            CompletableFuture<Void> result4 = holder.warmUp(3);
            Assertions.assertThat(connectionFutures).hasSize(1);

            connectionFutures.get(0).complete(conn1);
            Assertions.assertThat(connectionFutures).hasSize(2);

            connectionFutures.get(1).complete(conn2);
            Assertions.assertThat(connectionFutures).hasSize(3);

            connectionFutures.get(2).complete(conn3);

            Assertions.assertThat(result1).isCompleted();
            Assertions.assertThat(result2).isCompleted();
            Assertions.assertThat(result3).isCompleted();
            Assertions.assertThat(result4).isCompleted();
            Assertions.assertThat(holder.getAllConnections()).hasSize(3);
            Assertions.assertThat(holder.getFreeConnections()).hasSize(3);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testWarmUpRechecksTargetAfterPermitIsAcquired(@Mocked RedisConnection conn1,
                                                       @Mocked RedisConnection conn2,
                                                       @Mocked ClientConnectionsEntry entry) {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            new Expectations() {{
                conn1.isActive(); result = true; minTimes = 0;
                conn2.isActive(); result = true; minTimes = 0;
                entry.isFreezed(); result = false; minTimes = 0;
            }};

            List<CompletableFuture<RedisConnection>> connectionFutures = new ArrayList<>();
            Function<RedisClient, CompletionStage<RedisConnection>> callback = r -> {
                CompletableFuture<RedisConnection> f = new CompletableFuture<>();
                connectionFutures.add(f);
                return f;
            };
            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(null, 2, callback, manager.getServiceManager(), false);

            CompletableFuture<Void> initialWarmUp = holder.warmUp(1);
            connectionFutures.get(0).complete(conn1);
            Assertions.assertThat(initialWarmUp).isCompleted();

            RedisConnection borrowedConnection = holder.acquireConnection(null).join();
            CompletableFuture<RedisConnection> commandConnection = holder.acquireConnection(null);
            Assertions.assertThat(connectionFutures).hasSize(2);

            CompletableFuture<Void> warmUp = holder.warmUp(2);
            Assertions.assertThat(warmUp).isNotDone();

            connectionFutures.get(1).complete(conn2);
            Assertions.assertThat(commandConnection).isCompleted();
            Assertions.assertThat(holder.getAllConnections()).hasSize(2);

            holder.releaseConnection(entry, borrowedConnection);

            Assertions.assertThat(warmUp).isCompleted();
            Assertions.assertThat(connectionFutures).hasSize(2);
            Assertions.assertThat(holder.getAllConnections()).hasSize(2);

            holder.releaseConnection(entry, commandConnection.join());
            Assertions.assertThat(holder.getFreeConnectionsCounter().getCounter()).isEqualTo(2);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testFailWarmUpCancelsPendingPermit(@Mocked RedisConnection conn,
                                            @Mocked ClientConnectionsEntry entry) {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            new Expectations() {{
                conn.isActive(); result = true; minTimes = 0;
                entry.isFreezed(); result = false; minTimes = 0;
            }};

            CompletableFuture<RedisConnection> connectionFuture = new CompletableFuture<>();
            ConnectionsHolder<RedisConnection> holder = new ConnectionsHolder<>(null, 1,
                    r -> connectionFuture, manager.getServiceManager(), false);

            CompletableFuture<RedisConnection> commandConnection = holder.acquireConnection(null);
            CompletableFuture<Void> warmUp = holder.warmUp(1);
            Assertions.assertThat(holder.getFreeConnectionsCounter().queueSize()).isEqualTo(1);

            holder.failWarmUp(new RedisConnectionException("node down"));

            Assertions.assertThat(warmUp).isCompletedExceptionally();
            Assertions.assertThat(holder.getFreeConnectionsCounter().queueSize()).isZero();

            connectionFuture.complete(conn);
            holder.releaseConnection(entry, commandConnection.join());
            Assertions.assertThat(holder.getFreeConnectionsCounter().getCounter()).isEqualTo(1);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testFailWarmUpDiscardsConnectionCreatedAfterNodeDown(@Mocked RedisConnection conn) {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            CompletableFuture<RedisConnection> connectionFuture = new CompletableFuture<>();
            ConnectionsHolder<RedisConnection> holder = new ConnectionsHolder<>(null, 1,
                    r -> connectionFuture, manager.getServiceManager(), false);

            CompletableFuture<Void> warmUp = holder.warmUp(1);
            holder.failWarmUp(new RedisConnectionException("node down"));

            Assertions.assertThat(warmUp).isCompletedExceptionally();

            connectionFuture.complete(conn);

            Assertions.assertThat(holder.getAllConnections()).isEmpty();
            Assertions.assertThat(holder.getFreeConnections()).isEmpty();
            Assertions.assertThat(holder.getFreeConnectionsCounter().getCounter()).isEqualTo(1);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testWarmUpRejectsAmountGreaterThanPoolSize(@Mocked RedisClient client, @Mocked RedisConnection conn) {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            AtomicInteger createdConnections = new AtomicInteger();
            Function<RedisClient, CompletionStage<RedisConnection>> succeedingCallback = r -> {
                createdConnections.incrementAndGet();
                return CompletableFuture.completedFuture(conn);
            };
            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(client, 2, succeedingCallback, manager.getServiceManager(), false);

            CompletableFuture<Void> result = holder.warmUp(3);

            Assertions.assertThat(result).isCompletedExceptionally();
            Assertions.assertThat(createdConnections).hasValue(0);
            Assertions.assertThat(holder.getAllConnections()).isEmpty();
            Assertions.assertThat(holder.getFreeConnections()).isEmpty();
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }
}
