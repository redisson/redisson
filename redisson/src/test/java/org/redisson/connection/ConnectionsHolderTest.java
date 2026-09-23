package org.redisson.connection;

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
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


public class ConnectionsHolderTest {

    private static final class ActiveConnection extends RedisConnection {

        private ActiveConnection() {
            super(null);
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }

    private MasterSlaveConnectionManager buildManager() {
        Config config = new Config();
        config.setLazyInitialization(true);
        MasterSlaveServersConfig msConfig = config.useMasterSlaveServers();
        msConfig.setMasterAddress("redis://127.0.0.1:6379");
        msConfig.setReadMode(ReadMode.MASTER);
        return new MasterSlaveConnectionManager(msConfig, config);
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
    void testSuccessfulInitReleasesEachPermitExactlyOnce(@Mocked RedisClient client, @Mocked RedisConnection conn) {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            int poolMaxSize = 2;
            Function<RedisClient, CompletionStage<RedisConnection>> succeedingCallback =
                    r -> CompletableFuture.completedFuture(conn);
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
    void testCancelledAcquisitionDoesNotLeaveReusedConnectionWithZeroUsage() {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            ActiveConnection connection = new ActiveConnection();
            CompletableFuture<RedisConnection> physicalConnection = new CompletableFuture<>();
            ConnectionsHolder<RedisConnection> holder = new ConnectionsHolder<>(null, 1,
                    r -> physicalConnection, manager.getServiceManager(), true);

            CompletableFuture<RedisConnection> firstAcquisition = holder.acquireConnection(null);

            // Cancel future while physical socket connection is still in flight
            firstAcquisition.completeExceptionally(new CancellationException("request cancelled"));
            physicalConnection.complete(connection);

            // ensure consumed permits = 0
            Assertions.assertThat(connection.getUsage()).isZero();

            // ensure we can still re-acquire the connection
            CompletableFuture<RedisConnection> nextAcquisition = holder.acquireConnection(null);
            Assertions.assertThat(nextAcquisition).isCompletedWithValue(connection);
            // ... and it is correctly tracked
            Assertions.assertThat(connection.getUsage()).isEqualTo(1);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }
}
