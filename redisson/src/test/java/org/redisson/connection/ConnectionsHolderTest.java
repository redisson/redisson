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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


public class ConnectionsHolderTest {

    private MasterSlaveConnectionManager buildManager() {
        return buildManager(null);
    }

    private MasterSlaveConnectionManager buildManager(ThreadPoolExecutor executor) {
        Config config = new Config();
        config.setLazyInitialization(true);
        if (executor != null) {
            config.setExecutor(executor);
        }
        MasterSlaveServersConfig msConfig = config.useMasterSlaveServers();
        msConfig.setMasterAddress("redis://127.0.0.1:6379");
        msConfig.setReadMode(ReadMode.MASTER);
        return new MasterSlaveConnectionManager(msConfig, config);
    }

    @Test
    void testConnectionCounterUsesServiceExecutorToWakeWaiter() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch unblock = new CountDownLatch(1);
        MasterSlaveConnectionManager manager = buildManager(executor);
        try {
            executor.execute(() -> {
                taskStarted.countDown();
                try {
                    unblock.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            Assertions.assertThat(taskStarted.await(1, TimeUnit.SECONDS)).isTrue();

            ConnectionsHolder<RedisConnection> holder =
                    new ConnectionsHolder<>(null, 1, r -> new CompletableFuture<>(), manager.getServiceManager(), false);
            AsyncSemaphore counter = holder.getFreeConnectionsCounter();

            CompletableFuture<Void> acquired = counter.acquire();
            Assertions.assertThat(acquired).isCompleted();

            CompletableFuture<Void> waiter = counter.acquire();
            Assertions.assertThat(waiter).isNotDone();

            counter.release();

            Assertions.assertThat(waiter).isNotDone();
            Assertions.assertThat(executor.getQueue()).hasSize(1);

            unblock.countDown();

            waiter.get(1, TimeUnit.SECONDS);
            Assertions.assertThat(waiter).isCompleted();
        } finally {
            unblock.countDown();
            manager.shutdown(0, 0, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
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

}
