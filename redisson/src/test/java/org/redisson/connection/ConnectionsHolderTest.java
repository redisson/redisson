package org.redisson.connection;

import org.assertj.core.api.Assertions;
import org.joor.Reflect;
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

            CompletableFuture<Void> result = Reflect.on(holder).call("createConnection", poolMaxSize, 1).get();
            Assertions.assertThat(result).isCompletedExceptionally();

            // a failed init acquires one permit and must release it exactly once; an extra release
            // lifts the counter above the pool max and jams idle eviction so the pool never drains
            Assertions.assertThat(counter.getCounter()).isEqualTo(poolMaxSize);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }
}
