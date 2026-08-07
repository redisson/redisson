package org.redisson.connection;

import io.netty.channel.embedded.EmbeddedChannel;
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

    /**
     * Inactive free-pool entries (channel closed without RedisConnection.closeAsync)
     * must be discarded, not re-queued for reuse. See redisson/redisson#7236.
     */
    @Test
    void testInactiveConnectionDroppedFromFreePoolOnPoll() throws Exception {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            EmbeddedChannel channel = new EmbeddedChannel();
            RedisConnection connection = new RedisConnection(null, channel, new CompletableFuture<>());
            channel.close().syncUninterruptibly();
            Assertions.assertThat(connection.isActive()).isFalse();
            Assertions.assertThat(connection.isClosed()).isFalse();

            Function<RedisClient, CompletionStage<RedisConnection>> failingCallback = r -> {
                CompletableFuture<RedisConnection> f = new CompletableFuture<>();
                f.completeExceptionally(new RuntimeException("no new connection"));
                return f;
            };
            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(null, 2, failingCallback, manager.getServiceManager(), true);
            holder.getFreeConnections().add(connection);
            holder.getAllConnections().add(connection);

            CompletableFuture<RedisConnection> acquired = holder.acquireConnection(null);
            try {
                acquired.get(2, TimeUnit.SECONDS);
                Assertions.fail("expected acquire to fail after dropping inactive connection");
            } catch (Exception expected) {
                // createConnection fails after inactive free entry is dropped
            }

            Assertions.assertThat(holder.getFreeConnections()).doesNotContain(connection);
            Assertions.assertThat(holder.getAllConnections()).doesNotContain(connection);
            Assertions.assertThat(connection.isClosed()).isTrue();
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    /**
     * Releasing an inactive connection must not return it to the free pool.
     */
    @Test
    void testInactiveConnectionNotReleasedToFreePool(@Mocked RedisClient redisClient) throws Exception {
        MasterSlaveConnectionManager manager = buildManager();
        try {
            EmbeddedChannel channel = new EmbeddedChannel();
            RedisConnection connection = new RedisConnection(null, channel, new CompletableFuture<>());
            connection.incUsage();
            channel.close().syncUninterruptibly();
            Assertions.assertThat(connection.isActive()).isFalse();
            Assertions.assertThat(connection.isClosed()).isFalse();

            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(null, 2, r -> new CompletableFuture<>(),
                            manager.getServiceManager(), true);
            holder.getAllConnections().add(connection);

            MasterSlaveServersConfig msConfig = new MasterSlaveServersConfig();
            msConfig.setSubscriptionConnectionPoolSize(0);
            msConfig.setSubscriptionConnectionMinimumIdleSize(0);
            ClientConnectionsEntry entry = new ClientConnectionsEntry(
                    redisClient, 0, 2, manager, org.redisson.api.NodeType.MASTER, msConfig);
            holder.releaseConnection(entry, connection);

            Assertions.assertThat(holder.getFreeConnections()).doesNotContain(connection);
            Assertions.assertThat(holder.getAllConnections()).doesNotContain(connection);
            Assertions.assertThat(connection.isClosed()).isTrue();
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }
}
