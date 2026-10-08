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
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.ConnectionsHolder;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.Tuple;

import java.net.InetSocketAddress;
import java.util.Collections;
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

        Tuple<CompletableFuture<RedisConnection>, Throwable> result =
                pool.getTuple(RedisCommands.GET, false);

        assertThat(result.getT1()).isNull();
        assertThat(result.getT2()).isInstanceOf(RedisConnectionException.class);
        new Verifications() {{
            masterSlaveEntry.shutdownAndReconnectAsync(client, (Throwable) any);
            times = 1;
        }};
    }

    @Test
    void cancelledPoolAcquisitionDoesNotTripFailedConnectionDetector(
            @Mocked ConnectionManager connectionManager,
            @Mocked MasterSlaveEntry masterSlaveEntry,
            @Mocked ClientConnectionsEntry entry,
            @Mocked ConnectionsHolder<RedisConnection> holder,
            @Mocked RedisClient client,
            @Mocked RedisClientConfig clientConfig) throws Exception {
        CompletableFuture<RedisConnection> pending = new CompletableFuture<>();
        FailedConnectionDetector detector = new FailedConnectionDetector(1);

        new Expectations() {{
            entry.getConnectionsHolder();
            result = holder;
            holder.acquireConnection(RedisCommands.GET);
            result = pending;
            entry.getNodeType();
            result = NodeType.SLAVE;
            entry.getClient();
            result = client;
            minTimes = 0;
            client.getConfig();
            result = clientConfig;
            minTimes = 0;
            clientConfig.getFailedNodeDetector();
            result = detector;
            minTimes = 0;
        }};

        SlaveConnectionPool pool = new SlaveConnectionPool(
                new MasterSlaveServersConfig(), connectionManager, masterSlaveEntry);
        CompletableFuture<RedisConnection> acquisition =
                pool.get(RedisCommands.GET, entry, false);

        // RedisExecutor uses this form when a pool acquisition times out.
        assertThat(acquisition.completeExceptionally(
                new CancellationException("pool acquisition cancelled"))).isTrue();
        Thread.sleep(10);

        assertThat(detector.isNodeFailed()).isFalse();
    }

    @Test
    void connectionFailureStillTripsFailedConnectionDetector(
            @Mocked ConnectionManager connectionManager,
            @Mocked MasterSlaveEntry masterSlaveEntry,
            @Mocked ClientConnectionsEntry entry,
            @Mocked ConnectionsHolder<RedisConnection> holder,
            @Mocked RedisClient client,
            @Mocked RedisClientConfig clientConfig) throws Exception {
        CompletableFuture<RedisConnection> failed = new CompletableFuture<>();
        FailedConnectionDetector detector = new FailedConnectionDetector(1);

        new Expectations() {{
            entry.getConnectionsHolder();
            result = holder;
            holder.acquireConnection(RedisCommands.GET);
            result = failed;
            entry.getNodeType();
            result = NodeType.SLAVE;
            entry.getClient();
            result = client;
            client.getConfig();
            result = clientConfig;
            clientConfig.getFailedNodeDetector();
            result = detector;
        }};

        SlaveConnectionPool pool = new SlaveConnectionPool(
                new MasterSlaveServersConfig(), connectionManager, masterSlaveEntry);
        CompletableFuture<RedisConnection> acquisition =
                pool.get(RedisCommands.GET, entry, false);

        failed.completeExceptionally(new RedisConnectionException("connection failed"));
        assertThat(acquisition).isCompletedExceptionally();
        Thread.sleep(10);

        assertThat(detector.isNodeFailed()).isTrue();
    }

}
