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
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.Tuple;

import java.net.InetSocketAddress;
import java.util.Collections;

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
}
