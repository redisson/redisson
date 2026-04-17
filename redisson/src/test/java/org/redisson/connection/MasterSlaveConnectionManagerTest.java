package org.redisson.connection;

import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.NodeType;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnectionException;
import org.redisson.config.*;

import org.redisson.misc.RedisURI;

import java.net.InetSocketAddress;
import java.util.concurrent.*;


public class MasterSlaveConnectionManagerTest {

    MasterSlaveConnectionManager buildSlowConnectionManager(BaseMasterSlaveServersConfig<?> msConfig, Config config) {
        return new MasterSlaveConnectionManager(msConfig, config) {
            @Override
            protected RedisClient createClient(NodeType type, RedisURI address, int timeout, int commandTimeout, String sslHostname) {
                RedisClient client = super.createClient(type, address, timeout, commandTimeout, sslHostname);
                new MockUp<RedisClient>(client) {
                    @Mock
                    @SuppressWarnings("unchecked")
                    public CompletableFuture<InetSocketAddress> resolveAddr(Invocation inv) {
                        return CompletableFuture.supplyAsync(() -> {
                            try {
                                long delay = ThreadLocalRandom.current().nextLong(10, 20);
                                Thread.sleep(delay); // simulated long network call
                            } catch (InterruptedException e) {
                                // do nothing
                            }
                            throw new RuntimeException("Fatal test scenario, thread should be interrupted before this point, should not reach here");
                        });
                    }
                };
                return client;
            }
        };
    }

    @Test
    void testDoConnectInterruptedException() {
        Config config = new Config();
        MasterSlaveServersConfig msConfig = config.useMasterSlaveServers();
        msConfig.setMasterAddress("redis://127.0.0.1:6379");
        msConfig.setReadMode(ReadMode.MASTER);

        MasterSlaveConnectionManager manager = buildSlowConnectionManager(msConfig, config);

        // Pre-interrupting the thread to simulate interruption behavior
        Thread.currentThread().interrupt();
        try {
            Assertions.assertThatThrownBy(() -> manager.doConnect(u -> null))
                    .isInstanceOf(RedisConnectionException.class)
                    .hasCauseInstanceOf(InterruptedException.class);

            Assertions.assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // restore clean interrupt state
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testConnectInterruptedException() {
        Config config = new Config();
        MasterSlaveServersConfig msConfig = config.useMasterSlaveServers();
        msConfig.setMasterAddress("redis://127.0.0.1:6379");
        msConfig.setReadMode(ReadMode.MASTER);

        MasterSlaveConnectionManager manager = buildSlowConnectionManager(msConfig, config);

        // Pre-interrupting the thread to simulate interruption behavior
        Thread.currentThread().interrupt();
        try {
            Assertions.assertThatThrownBy(manager::connect)
                    .isInstanceOf(RedisConnectionException.class)
                    .hasCauseInstanceOf(InterruptedException.class);

            Assertions.assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // restore clean interrupt state
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }
}
