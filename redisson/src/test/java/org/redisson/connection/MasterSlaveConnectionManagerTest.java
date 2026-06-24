package org.redisson.connection;

import mockit.Expectations;
import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.NodeType;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnectionException;
import org.redisson.config.*;

import org.redisson.misc.RedisURI;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

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
            AtomicReference<NodeType> emittedNodeType = new AtomicReference<>();
            manager.getServiceManager()
                    .getConnectionEventsHub()
                    .addListener(new ConnectionListener() {
                        @Override
                        public void onConnect(InetSocketAddress address) {
                        }

                        @Override
                        public void onConnect(InetSocketAddress address, NodeType nodeType) {
                            emittedNodeType.set(nodeType);
                        }

                        @Override
                        public void onDisconnect(InetSocketAddress address) {
                        }
                    });

            NodeType[] types = {NodeType.MASTER, NodeType.SLAVE};
            for (int i = 0; i < types.length; i++) {
                NodeType type = types[i];
                // distinct address per iteration: ConnectionEventsHub suppresses a repeat connect for an already-CONNECTED address
                int port = 6379 + i;
                InetSocketAddress addr = new InetSocketAddress("127.0.0.1", port);
                emittedNodeType.set(null);

                RedisClientConfig redisConfig = manager.createRedisConfig(
                        type, new RedisURI("redis://127.0.0.1:" + port), 1000, 1000, null);
                redisConfig.getConnectedListener().accept(addr);
                // pre-init resolution must echo the passed-in type without consulting entries
                Assertions.assertThat(emittedNodeType.get()).isEqualTo(type);
            }

            Assertions.assertThat(doConnectInvocations.get()).isZero();
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    void listenerResolvesActualNodeTypeAfterLazyInitialization(
            @Mocked MasterSlaveEntry entry, @Mocked RedisClient client) {
        Config config = new Config();
        config.setLazyInitialization(true);
        MasterSlaveServersConfig msConfig = config.useMasterSlaveServers();
        msConfig.setMasterAddress("redis://127.0.0.1:6379");
        msConfig.setReadMode(ReadMode.MASTER);

        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 6379);
        AtomicReference<NodeType> emittedNodeType = new AtomicReference<>();

        MasterSlaveConnectionManager manager = new MasterSlaveConnectionManager(msConfig, config) {
            @Override
            protected void doConnect(Function<RedisURI, String> hostnameMapper) {
                // successful lazy init
            }

            @Override
            public MasterSlaveEntry getEntry(InetSocketAddress address) {
                lazyConnect();
                return entry;
            }
        };

        try {
            new Expectations() {{
                entry.isInit(); result = true;
                entry.getClient(); result = client;
                client.getAddr(); result = addr;
            }};

            manager.getServiceManager()
                    .getConnectionEventsHub()
                    .addListener(new ConnectionListener() {
                        @Override
                        public void onConnect(InetSocketAddress address) {
                        }

                        @Override
                        public void onConnect(InetSocketAddress address, NodeType nodeType) {
                            emittedNodeType.set(nodeType);
                        }

                        @Override
                        public void onDisconnect(InetSocketAddress address) {
                        }
                    });

            manager.getEntrySet();

            RedisClientConfig redisConfig = manager.createRedisConfig(
                    NodeType.SLAVE,
                    new RedisURI("redis://127.0.0.1:6379"),
                    1000,
                    1000,
                    null);

            redisConfig.getConnectedListener().accept(addr);

            Assertions.assertThat(emittedNodeType.get()).isEqualTo(NodeType.MASTER);
        } finally {
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }
}
