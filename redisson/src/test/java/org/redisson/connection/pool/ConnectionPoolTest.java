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
import org.redisson.client.FailedNodeDetector;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.ConnectionsHolder;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.Tuple;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

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

    @Test
    void cancelledSlaveAcquisitionDoesNotMarkNodeFailed(
            @Mocked ConnectionManager connectionManager,
            @Mocked MasterSlaveEntry masterSlaveEntry,
            @Mocked ClientConnectionsEntry entry,
            @Mocked RedisClient client,
            @Mocked RedisClientConfig clientConfig,
            @Mocked ConnectionsHolder<RedisConnection> holder) {
        RecordingFailedNodeDetector detector = new RecordingFailedNodeDetector();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 6379);
        CompletableFuture<RedisConnection> cancelledFuture = new CompletableFuture<>();
        cancelledFuture.completeExceptionally(new CancellationException());

        new Expectations() {{
            entry.getConnectionsHolder();
            result = holder;
            holder.acquireConnection(RedisCommands.GET);
            result = cancelledFuture;
            entry.getNodeType();
            result = NodeType.SLAVE;
        }};

        SlaveConnectionPool pool = new SlaveConnectionPool(
                new MasterSlaveServersConfig(), connectionManager, masterSlaveEntry);

        CompletableFuture<RedisConnection> result = pool.get(RedisCommands.GET, entry, false);

        assertThat(result).isCompletedExceptionally();
        assertThat(detector.connectFailures).isEmpty();
    }

    @Test
    void failedSlaveAcquisitionMarksNodeFailed(
            @Mocked ConnectionManager connectionManager,
            @Mocked MasterSlaveEntry masterSlaveEntry,
            @Mocked ClientConnectionsEntry entry,
            @Mocked RedisClient client,
            @Mocked RedisClientConfig clientConfig,
            @Mocked ConnectionsHolder<RedisConnection> holder) {
        RecordingFailedNodeDetector detector = new RecordingFailedNodeDetector();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 6379);
        CompletableFuture<RedisConnection> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RedisConnectionException("connection failed"));

        new Expectations() {{
            entry.getConnectionsHolder();
            result = holder;
            holder.acquireConnection(RedisCommands.GET);
            result = failedFuture;
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
        }};

        SlaveConnectionPool pool = new SlaveConnectionPool(
                new MasterSlaveServersConfig(), connectionManager, masterSlaveEntry);

        CompletableFuture<RedisConnection> result = pool.get(RedisCommands.GET, entry, false);

        assertThat(result).isCompletedExceptionally();
        assertThat(detector.connectFailures).hasSize(1);
    }

    static final class RecordingFailedNodeDetector implements FailedNodeDetector {

        final List<Throwable> connectFailures = new ArrayList<>();
        volatile boolean nodeFailed;

        @Override
        public void onConnectFailed(Throwable cause, InetSocketAddress address) {
            connectFailures.add(cause);
        }

        @Override
        public void onConnectSuccessful() {
        }

        @Override
        public void onConnectFailed() {
        }

        @Override
        public void onConnectFailed(Throwable cause) {
            connectFailures.add(cause);
        }

        @Override
        public void onPingSuccessful() {
        }

        @Override
        public void onPingFailed() {
        }

        @Override
        public void onPingFailed(Throwable cause) {
        }

        @Override
        public void onCommandSuccessful() {
        }

        @Override
        public void onCommandFailed(Throwable cause) {
        }

        @Override
        public boolean isNodeFailed() {
            return nodeFailed;
        }

        @Override
        public boolean isNodeFailed(InetSocketAddress address) {
            return nodeFailed;
        }

        @Override
        public FailedNodeDetector copy() {
            return this;
        }
    }
}
