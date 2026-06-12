package org.redisson.connection;

import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import org.assertj.core.api.Assertions;
import org.joor.Reflect;
import org.junit.jupiter.api.Test;
import org.redisson.api.NodeType;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnectionException;
import org.redisson.config.*;

import org.redisson.misc.RedisURI;

import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;


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

    @Test
    void testLazyConnectRetriesAfterFailedInitialization() {
        Config config = new Config();
        config.setLazyInitialization(true);
        MasterSlaveServersConfig msConfig = config.useMasterSlaveServers();
        msConfig.setMasterAddress("redis://127.0.0.1:6379");
        msConfig.setReadMode(ReadMode.MASTER);
        msConfig.setRetryAttempts(0);

        AtomicInteger doConnectInvocations = new AtomicInteger();
        MasterSlaveConnectionManager manager = new MasterSlaveConnectionManager(msConfig, config) {
            @Override
            protected void doConnect(Function<RedisURI, String> hostnameMapper) {
                if (doConnectInvocations.incrementAndGet() == 1) {
                    // mimics ClusterConnectionManager.doConnect surfacing a bounded-wait timeout:
                    // a stuck master entry setup must not park lazyConnect callers indefinitely.
                    throw new RedisConnectionException(
                            "Timed out waiting for cluster master entries to initialize");
                }
                // second attempt succeeds — no exception thrown
            }
        };

        try {
            Assertions.assertThatThrownBy(manager::getEntrySet)
                    .isInstanceOf(RedisConnectionException.class)
                    .hasMessageContaining("Timed out");

            // exceptional latch must be replaceable so a subsequent call retries initialization
            Assertions.assertThatCode(manager::getEntrySet).doesNotThrowAnyException();
            Assertions.assertThat(doConnectInvocations.get()).isEqualTo(2);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void testListenerNodeTypeResolutionDoesNotTriggerLazyConnect() {
        Config config = new Config();
        config.setLazyInitialization(true);
        MasterSlaveServersConfig msConfig = config.useMasterSlaveServers();
        msConfig.setMasterAddress("redis://127.0.0.1:6379");
        msConfig.setReadMode(ReadMode.MASTER);

        AtomicInteger doConnectInvocations = new AtomicInteger();
        MasterSlaveConnectionManager manager = new MasterSlaveConnectionManager(msConfig, config) {
            @Override
            protected void doConnect(Function<RedisURI, String> hostnameMapper) {
                doConnectInvocations.incrementAndGet();
                throw new AssertionError(
                        "lazyConnect must not run on the connection lifecycle listener path");
            }
        };

        try {
            InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 6379);
            NodeType resolvedMaster = Reflect.on(manager)
                    .call("getNodeType", NodeType.MASTER, addr)
                    .get();
            NodeType resolvedSlave = Reflect.on(manager)
                    .call("getNodeType", NodeType.SLAVE, addr)
                    .get();

            Assertions.assertThat(resolvedMaster).isEqualTo(NodeType.MASTER);
            Assertions.assertThat(resolvedSlave).isEqualTo(NodeType.SLAVE);
            Assertions.assertThat(doConnectInvocations.get()).isZero();
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }
}
