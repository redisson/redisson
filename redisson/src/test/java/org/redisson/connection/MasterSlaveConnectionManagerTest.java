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
import java.time.Duration;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;


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

    MasterSlaveConnectionManager buildStuckConnectionManager(BaseMasterSlaveServersConfig<?> msConfig, Config config) {
        // resolveAddr returns a future that never completes, mimicking a node that accepts TCP
        // but never finishes the handshake. setupMasterEntry then never completes either.
        return new MasterSlaveConnectionManager(msConfig, config) {
            @Override
            protected RedisClient createClient(NodeType type, RedisURI address, int timeout, int commandTimeout, String sslHostname) {
                RedisClient client = super.createClient(type, address, timeout, commandTimeout, sslHostname);
                new MockUp<RedisClient>(client) {
                    @Mock
                    public CompletableFuture<InetSocketAddress> resolveAddr(Invocation inv) {
                        return new CompletableFuture<>();
                    }
                };
                return client;
            }
        };
    }

    @Test
    void testDoConnectBoundsWaitWhenMinimumIdleSizeIsZero() {
        Config config = new Config();
        MasterSlaveServersConfig msConfig = config.useMasterSlaveServers();
        msConfig.setMasterAddress("redis://127.0.0.1:6379");
        msConfig.setReadMode(ReadMode.MASTER);
        // the unbounded-join branch was taken only when minimumIdleSize == 0
        msConfig.setMasterConnectionMinimumIdleSize(0);
        msConfig.setConnectTimeout(100);

        MasterSlaveConnectionManager manager = buildStuckConnectionManager(msConfig, config);

        try {
            // a stuck master entry setup must surface a bounded-wait timeout rather than parking the caller
            assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                    Assertions.assertThatThrownBy(() -> manager.doConnect(u -> null))
                            .isInstanceOf(RedisConnectionException.class)
                            .hasCauseInstanceOf(TimeoutException.class));
        } finally {
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
