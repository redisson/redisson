/**
 * Copyright (c) 2013-2026 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.connection.pool;

import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import org.junit.jupiter.api.Test;
import org.redisson.api.NodeType;
import org.redisson.client.FailedCommandsDetector;
import org.redisson.client.FailedConnectionDetector;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.Tuple;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionPoolTest {

    @Test
    void failedNodeDetectedDuringSelectionIsDisconnected(
            @Mocked ConnectionManager connectionManager,
            @Mocked MasterSlaveEntry masterSlaveEntry,
            @Mocked ClientConnectionsEntry entry,
            @Mocked RedisClient client,
            @Mocked RedisClientConfig clientConfig) {
        FailedCommandsDetector detector = new FailedCommandsDetector(10000, 1);
        detector.onCommandFailed(new RedisConnectionException("test"));
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 6379);

        new Expectations() {{
            masterSlaveEntry.getAllEntries();
            result = Collections.singletonList(entry);
            entry.isFreezed();
            result = false;
            entry.getNodeType();
            result = NodeType.SLAVE;
            entry.getClient();
            result = client;
            client.getConfig();
            result = clientConfig;
            clientConfig.getFailedNodeDetector();
            result = detector;
            client.getAddr();
            result = address;
            masterSlaveEntry.getClient();
            result = client;
        }};

        SlaveConnectionPool pool = new SlaveConnectionPool(
                new MasterSlaveServersConfig(), connectionManager, masterSlaveEntry);

        Tuple<java.util.concurrent.CompletableFuture<RedisConnection>, Throwable> result =
                pool.getTuple(RedisCommands.GET, false);

        assertThat(result.getT1()).isNull();
        assertThat(result.getT2()).isInstanceOf(RedisConnectionException.class);
        new Verifications() {{
            masterSlaveEntry.shutdownAndReconnectAsync(client, (Throwable) any);
            times = 1;
        }};
    }

    static class CountingDetector extends FailedConnectionDetector {

        final AtomicInteger connectFailures = new AtomicInteger();

        CountingDetector() {
            super(1);
        }

        @Override
        public void onConnectFailed(Throwable cause) {
            connectFailures.incrementAndGet();
            super.onConnectFailed(cause);
        }
    }

    private static ClientConnectionsEntry entry(ConnectionManager connectionManager, RedisClient client,
                                                RedisClientConfig clientConfig, FailedConnectionDetector detector,
                                                NodeType nodeType) {
        new Expectations() {{
            client.getConfig();
            result = clientConfig;
            minTimes = 0;
            clientConfig.getFailedNodeDetector();
            result = detector;
            minTimes = 0;
        }};
        MasterSlaveServersConfig config = new MasterSlaveServersConfig();
        config.setSubscriptionConnectionPoolSize(1);
        return new ClientConnectionsEntry(client, 0, 1, connectionManager, nodeType, config);
    }

    @Test
    void cancelledWhileWaitingForConnectionIsNotReported(
            @Mocked ConnectionManager connectionManager, @Mocked MasterSlaveEntry masterSlaveEntry,
            @Mocked RedisClient client, @Mocked RedisClientConfig clientConfig, @Mocked RedisConnection connection) {
        CountingDetector detector = new CountingDetector();
        ClientConnectionsEntry entry = entry(connectionManager, client, clientConfig, detector, NodeType.SLAVE);
        new Expectations() {{
            client.connectAsync();
            result = new CompletableFutureWrapper<>(connection);
        }};
        SlaveConnectionPool pool = new SlaveConnectionPool(new MasterSlaveServersConfig(), connectionManager, masterSlaveEntry);

        assertThat(pool.get(RedisCommands.GET, entry, false)).isCompleted();
        // the only connection is taken
        CompletableFuture<RedisConnection> waiting = pool.get(RedisCommands.GET, entry, false);
        assertThat(waiting).isNotDone();
        waiting.completeExceptionally(new CancellationException());

        assertThat(detector.connectFailures).hasValue(0);
    }

    @Test
    void connectFailureAfterCancellationIsReported(
            @Mocked ConnectionManager connectionManager, @Mocked MasterSlaveEntry masterSlaveEntry,
            @Mocked RedisClient client, @Mocked RedisClientConfig clientConfig) {
        CountingDetector detector = new CountingDetector();
        ClientConnectionsEntry entry = entry(connectionManager, client, clientConfig, detector, NodeType.SLAVE);
        CompletableFuture<RedisConnection> connect = new CompletableFuture<>();
        new Expectations() {{
            client.connectAsync();
            result = new CompletableFutureWrapper<>(connect);
        }};
        SlaveConnectionPool pool = new SlaveConnectionPool(new MasterSlaveServersConfig(), connectionManager, masterSlaveEntry);

        CompletableFuture<RedisConnection> acquisition = pool.get(RedisCommands.GET, entry, false);
        acquisition.completeExceptionally(new CancellationException());
        connect.completeExceptionally(new RedisConnectionException("connect timed out"));

        assertThat(detector.connectFailures).hasValue(1);
    }

    @Test
    void connectFailureIsReportedOnce(
            @Mocked ConnectionManager connectionManager, @Mocked MasterSlaveEntry masterSlaveEntry,
            @Mocked RedisClient client, @Mocked RedisClientConfig clientConfig) {
        CountingDetector detector = new CountingDetector();
        ClientConnectionsEntry entry = entry(connectionManager, client, clientConfig, detector, NodeType.SLAVE);
        CompletableFuture<RedisConnection> connect = new CompletableFuture<>();
        new Expectations() {{
            client.connectAsync();
            result = new CompletableFutureWrapper<>(connect);
        }};
        SlaveConnectionPool pool = new SlaveConnectionPool(new MasterSlaveServersConfig(), connectionManager, masterSlaveEntry);

        AtomicReference<Throwable> seen = new AtomicReference<>();
        pool.get(RedisCommands.GET, entry, false).whenComplete((r, e) -> seen.set(e));
        RedisConnectionException failure = new RedisConnectionException("connection refused");
        connect.completeExceptionally(failure);

        assertThat(seen.get()).isSameAs(failure);
        assertThat(detector.connectFailures).hasValue(1);
    }

    @Test
    void trackedConnectFailureIsReported(
            @Mocked ConnectionManager connectionManager, @Mocked MasterSlaveEntry masterSlaveEntry,
            @Mocked RedisClient client, @Mocked RedisClientConfig clientConfig) {
        CountingDetector detector = new CountingDetector();
        ClientConnectionsEntry entry = entry(connectionManager, client, clientConfig, detector, NodeType.SLAVE);
        CompletableFuture<RedisConnection> connect = new CompletableFuture<>();
        new Expectations() {{
            client.connectAsync();
            result = new CompletableFutureWrapper<>(connect);
        }};
        SlaveConnectionPool pool = new SlaveConnectionPool(new MasterSlaveServersConfig(), connectionManager, masterSlaveEntry);

        CompletableFuture<RedisConnection> acquisition = pool.get(RedisCommands.GET, entry, true);
        connect.completeExceptionally(new RedisConnectionException("connection refused"));

        assertThat(acquisition).isCompletedExceptionally();
        assertThat(detector.connectFailures).hasValue(1);
    }

    @Test
    void pubSubConnectFailureIsReported(
            @Mocked ConnectionManager connectionManager, @Mocked MasterSlaveEntry masterSlaveEntry,
            @Mocked RedisClient client, @Mocked RedisClientConfig clientConfig) {
        CountingDetector detector = new CountingDetector();
        ClientConnectionsEntry entry = entry(connectionManager, client, clientConfig, detector, NodeType.SLAVE);
        CompletableFuture<RedisPubSubConnection> connect = new CompletableFuture<>();
        new Expectations() {{
            client.connectPubSubAsync();
            result = new CompletableFutureWrapper<>(connect);
        }};
        PubSubConnectionPool pool = new PubSubConnectionPool(new MasterSlaveServersConfig(), connectionManager, masterSlaveEntry);

        CompletableFuture<RedisPubSubConnection> acquisition = pool.get(entry);
        connect.completeExceptionally(new RedisConnectionException("connection refused"));

        assertThat(acquisition).isCompletedExceptionally();
        assertThat(detector.connectFailures).hasValue(1);
    }

    @Test
    void throwingDetectorDoesNotAffectAcquisition(
            @Mocked ConnectionManager connectionManager, @Mocked MasterSlaveEntry masterSlaveEntry,
            @Mocked RedisClient client, @Mocked RedisClientConfig clientConfig) {
        FailedConnectionDetector detector = new FailedConnectionDetector(1) {
            @Override
            public void onConnectFailed(Throwable cause) {
                throw new IllegalStateException("detector failure");
            }
        };
        ClientConnectionsEntry entry = entry(connectionManager, client, clientConfig, detector, NodeType.SLAVE);
        CompletableFuture<RedisConnection> connect = new CompletableFuture<>();
        new Expectations() {{
            client.connectAsync();
            result = new CompletableFutureWrapper<>(connect);
        }};
        SlaveConnectionPool pool = new SlaveConnectionPool(new MasterSlaveServersConfig(), connectionManager, masterSlaveEntry);

        AtomicReference<Throwable> seen = new AtomicReference<>();
        pool.get(RedisCommands.GET, entry, false).whenComplete((r, e) -> seen.set(e));
        RedisConnectionException failure = new RedisConnectionException("connection refused");
        connect.completeExceptionally(failure);

        assertThat(seen.get()).isSameAs(failure);
    }
}
