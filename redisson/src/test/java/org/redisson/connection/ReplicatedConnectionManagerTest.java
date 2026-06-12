package org.redisson.connection;

import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import org.assertj.core.api.Assertions;
import org.joor.Reflect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.config.Config;
import org.redisson.config.DelayStrategy;
import org.redisson.config.ReplicatedServersConfig;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.RedisURI;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class ReplicatedConnectionManagerTest {

    private ReplicatedConnectionManager manager;

    @AfterEach
    public void cleanup() {
        if (manager != null) {
            for (RedisURI uri : new ArrayList<>(nodeConnections(manager).keySet())) {
                manager.disconnectNode(uri);
            }
            manager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testRemovesCachedConnectionOnTimeout() {
        manager = createManager();
        RedisURI uri = new RedisURI("redis://127.0.0.1:6379");
        DummyRedisConnection connection = new DummyRedisConnection();
        registerNodeConnection(manager, uri, connection);

        manager.handleNodeCheckError(uri, CompletableFuture.completedFuture(connection),
                new CompletionException(new RedisTimeoutException("timeout")));

        Assertions.assertThat(nodeConnections(manager)).isEmpty();
        Assertions.assertThat(connection.isClosed()).isTrue();
    }

    @Test
    public void testKeepsConnectionForNonTimeoutErrors() {
        manager = createManager();
        RedisURI uri = new RedisURI("redis://127.0.0.1:6379");
        DummyRedisConnection connection = new DummyRedisConnection();
        registerNodeConnection(manager, uri, connection);

        manager.handleNodeCheckError(uri, CompletableFuture.completedFuture(connection),
                new CompletionException(new IllegalArgumentException("bad role")));

        Assertions.assertThat(nodeConnections(manager)).containsEntry(uri, connection);
        Assertions.assertThat(connection.isClosed()).isFalse();
    }

    @Test
    public void testDisconnectsConnectionWhenNodeCheckFailsWithTimeout() throws Exception {
        manager = createManager();
        RedisURI uri = new RedisURI("redis://127.0.0.1:6379");
        TimeoutRedisConnection connection = TimeoutRedisConnection.create(uri);
        registerNodeConnection(manager, uri, connection);

        try {
            ReplicatedServersConfig serversConfig = new ReplicatedServersConfig();
            serversConfig.addNodeAddress(uri.toURIString());

            Set<InetSocketAddress> slaveIPs = Collections.newSetFromMap(new ConcurrentHashMap<>());
            CompletableFuture<?> future = manager.checkNode(uri, serversConfig, slaveIPs);

            Assertions.assertThatThrownBy(future::join)
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(RedisTimeoutException.class);

            Assertions.assertThat(nodeConnections(manager)).isEmpty();
            Assertions.assertThat(connection.isClosed()).isTrue();
        } finally {
            connection.shutdownClient();
        }
    }

    @Test
    public void testDoConnectBoundsWaitWhenSeedConnectionStalls() {
        Config config = new Config();
        ReplicatedServersConfig serversConfig = config.useReplicatedServers();
        serversConfig.addNodeAddress("redis://127.0.0.1:6379");
        // a stalled seed must surface a bounded-wait timeout rather than parking the caller forever
        serversConfig.setConnectTimeout(100);

        manager = buildStuckManager(serversConfig, config);

        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                Assertions.assertThatThrownBy(() -> manager.doConnect(u -> null))
                        .isInstanceOf(RedisConnectionException.class)
                        .hasCauseInstanceOf(TimeoutException.class));
    }

    private ReplicatedConnectionManager createManager() {
        Config config = new Config();
        ReplicatedServersConfig serversConfig = new ReplicatedServersConfig();
        serversConfig.addNodeAddress("redis://127.0.0.1:6379");
        return new ReplicatedConnectionManager(serversConfig, config);
    }

    private static ReplicatedConnectionManager buildStuckManager(ReplicatedServersConfig serversConfig, Config config) {
        // resolveAddr returns a future that never completes, so connectToNode never finishes and
        // the seed-node join in doConnect must time out instead of parking forever
        return new ReplicatedConnectionManager(serversConfig, config) {
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

    private static Map<RedisURI, RedisConnection> nodeConnections(ReplicatedConnectionManager manager) {
        return Reflect.on(manager).field("nodeConnections").<Map<RedisURI, RedisConnection>>get();
    }

    private static void registerNodeConnection(ReplicatedConnectionManager manager, RedisURI uri, RedisConnection connection) {
        nodeConnections(manager).put(uri, connection);
    }

    private static final class DummyRedisConnection extends RedisConnection {
        private boolean closed;

        private DummyRedisConnection() {
            super(null);
            updateChannel(new EmbeddedChannel());
        }

        @Override
        public ChannelFuture closeAsync() {
            closed = true;
            return super.closeAsync();
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }

    private static final class TimeoutRedisConnection extends RedisConnection {
        private boolean closed;

        private TimeoutRedisConnection(RedisClient client) {
            super(client);
            updateChannel(new EmbeddedChannel());
        }

        static TimeoutRedisConnection create(RedisURI uri) {
            RedisClientConfig clientConfig = new RedisClientConfig();
            InetSocketAddress address = new InetSocketAddress(uri.getHost(), uri.getPort());
            clientConfig.setAddress(address, uri);
            RedisClient client = RedisClient.create(clientConfig);
            return new TimeoutRedisConnection(client);
        }

        @Override
        public ChannelFuture closeAsync() {
            closed = true;
            getRedisClient().shutdownAsync();
            return super.closeAsync();
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public <T, R> RFuture<R> async(int retryAttempts, DelayStrategy delayStrategy, long timeout,
                                       Codec codec, RedisCommand<T> command, Object... params) {
            CompletableFuture<R> failure = new CompletableFuture<>();
            failure.completeExceptionally(new RedisTimeoutException("timeout"));
            return new CompletableFutureWrapper<>(failure);
        }

        void shutdownClient() {
            getRedisClient().shutdown();
        }
    }
}
