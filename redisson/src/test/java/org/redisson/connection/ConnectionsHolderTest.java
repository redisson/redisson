package org.redisson.connection;

import mockit.Expectations;
import mockit.Mocked;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.misc.AsyncSemaphore;

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
    void testWarmUpCreatesFreeConnectionsUpToDefinedAmount(@Mocked RedisConnection conn1,
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
    void testWarmUpCreatesConnectionsUntilFreeAmountIsReached(@Mocked RedisConnection conn1,
                                                              @Mocked RedisConnection conn2,
                                                              @Mocked RedisConnection conn3,
                                                              @Mocked RedisConnection conn4) {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            new Expectations() {{
                conn1.isActive(); result = true; minTimes = 0;
                conn2.isActive(); result = true; minTimes = 0;
                conn3.isActive(); result = true; minTimes = 0;
                conn4.isActive(); result = true; minTimes = 0;
            }};

            AtomicInteger createdConnections = new AtomicInteger();
            Function<RedisClient, CompletionStage<RedisConnection>> succeedingCallback =
                    succeedingCallback(createdConnections, conn1, conn2, conn3, conn4);
            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(null, 4, succeedingCallback, manager.getServiceManager(), false);

            Assertions.assertThat(holder.warmUp(3)).isCompleted();
            RedisConnection connection = holder.acquireConnection(null).join();

            CompletableFuture<Void> result = holder.warmUp(3);

            Assertions.assertThat(result).isCompleted();
            Assertions.assertThat(connection).isSameAs(conn1);
            Assertions.assertThat(createdConnections).hasValue(4);
            Assertions.assertThat(holder.getAllConnections()).hasSize(4);
            Assertions.assertThat(holder.getFreeConnections()).hasSize(3);
            Assertions.assertThat(holder.getFreeConnectionsCounter().getCounter()).isEqualTo(3);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testWarmUpCompletesAfterBorrowedConnectionReturnsWhenPoolIsFull(@Mocked RedisConnection conn1,
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
            Assertions.assertThat(result).isNotDone();

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
