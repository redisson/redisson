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
package org.redisson.connection;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Verifications;
import org.assertj.core.api.Assertions;
import org.joor.Reflect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.NodeType;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.SubscriptionMode;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.RedisURI;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ClusterConnectionManagerTest {

    private ClusterConnectionManager manager;

    @BeforeEach
    void beforeEach() {
        Config config = new Config();
        ClusterServersConfig clusterConfig = config.useClusterServers()
                .addNodeAddress("redis://config-endpoint.example:6379");
        manager = new ClusterConnectionManager(clusterConfig, config);
        Reflect.on(manager).set("configEndpointHostName", "config-endpoint.example");
    }

    @AfterEach
    void afterEach() {
        manager.shutdown(0, 0, TimeUnit.SECONDS);
    }

    @Test
    void testConfigurationEndpointSkipsDiscoveredMasterManagementConnection() {
        CompletionStage<Void> connectionFuture = manager.ensureMasterNodeConnection(
                new ClusterServersConfig(), new RedisURI("redis://10.0.0.1:6379"));

        Assertions.assertThat(connectionFuture.toCompletableFuture())
                .isCompletedWithValue(null);
    }

    @Test
    void testConfigurationEndpointValidatesZeroIdleMasterThroughCommandPool(
            @Injectable MasterSlaveEntry entry, @Injectable RedisClient client,
            @Injectable RedisConnection connection) {
        MasterSlaveServersConfig masterConfig = new MasterSlaveServersConfig()
                .setMasterConnectionMinimumIdleSize(0)
                .setMasterConnectionPoolSize(64)
                .setSubscriptionMode(SubscriptionMode.SLAVE);
        RedisURI masterAddress = new RedisURI("redis://10.0.0.1:6379");

        new Expectations() {{
            entry.setupMasterEntry(masterAddress, "config-endpoint.example");
            result = CompletableFuture.completedFuture(client);
            entry.getConfig(); result = masterConfig;
            entry.connectionWriteOp(RedisCommands.PING);
            result = CompletableFuture.completedFuture(connection);
        }};

        RedisClient result = manager.setupAndValidateMasterEntry(entry, masterAddress).join();

        Assertions.assertThat(result).isSameAs(client);
        new Verifications() {{
            entry.releaseWrite(connection); times = 1;
        }};
    }

    @Test
    void testConfigurationEndpointValidatesZeroSizedMasterPoolWithTransientConnection(
            @Injectable MasterSlaveEntry entry, @Injectable RedisClient client,
            @Injectable RedisConnection connection) {
        MasterSlaveServersConfig masterConfig = new MasterSlaveServersConfig()
                .setMasterConnectionMinimumIdleSize(0)
                .setMasterConnectionPoolSize(0)
                .setSubscriptionMode(SubscriptionMode.SLAVE);
        RedisURI masterAddress = new RedisURI("redis://10.0.0.1:6379");

        new Expectations() {{
            entry.setupMasterEntry(masterAddress, "config-endpoint.example");
            result = CompletableFuture.completedFuture(client);
            entry.getConfig(); result = masterConfig;
            client.connectAsync();
            result = new CompletableFutureWrapper<>(connection);
        }};

        RedisClient result = manager.setupAndValidateMasterEntry(entry, masterAddress).join();

        Assertions.assertThat(result).isSameAs(client);
        new Verifications() {{
            connection.closeAsync(); times = 1;
            entry.connectionWriteOp(RedisCommands.PING); times = 0;
        }};
    }

    @Test
    void testConfigurationEndpointUsesInitializedSubscriptionConnection(
            @Injectable MasterSlaveEntry entry, @Injectable RedisClient client) {
        MasterSlaveServersConfig masterConfig = new MasterSlaveServersConfig()
                .setMasterConnectionMinimumIdleSize(0)
                .setMasterConnectionPoolSize(64)
                .setSubscriptionMode(SubscriptionMode.MASTER)
                .setSubscriptionConnectionMinimumIdleSize(1);
        RedisURI masterAddress = new RedisURI("redis://10.0.0.1:6379");

        new Expectations() {{
            entry.setupMasterEntry(masterAddress, "config-endpoint.example");
            result = CompletableFuture.completedFuture(client);
            entry.getConfig(); result = masterConfig;
        }};

        RedisClient result = manager.setupAndValidateMasterEntry(entry, masterAddress).join();

        Assertions.assertThat(result).isSameAs(client);
        new Verifications() {{
            entry.connectionWriteOp(RedisCommands.PING); times = 0;
            client.connectAsync(); times = 0;
        }};
    }

    @Test
    void testDirectNodeConnectsToDiscoveredMaster(
            @Injectable RedisClient client, @Injectable RedisConnection connection) {
        Config config = new Config();
        ClusterServersConfig clusterConfig = config.useClusterServers()
                .addNodeAddress("redis://10.0.0.1:6379");
        AtomicReference<RedisURI> connectedAddress = new AtomicReference<>();
        AtomicReference<String> sslHostname = new AtomicReference<>();
        ClusterConnectionManager directManager = new ClusterConnectionManager(clusterConfig, config) {
            @Override
            protected RedisClient createClient(NodeType type, RedisURI address, int timeout,
                    int commandTimeout, String hostname) {
                connectedAddress.set(address);
                sslHostname.set(hostname);
                return client;
            }
        };
        RedisURI masterAddress = new RedisURI("redis://10.0.0.2:6379");

        try {
            new Expectations() {{
                client.connectAsync(); result = new CompletableFutureWrapper<>(connection);
                connection.isActive(); result = true;
            }};

            directManager.ensureMasterNodeConnection(clusterConfig, masterAddress)
                    .toCompletableFuture().join();

            Assertions.assertThat(connectedAddress.get()).isEqualTo(masterAddress);
            Assertions.assertThat(sslHostname.get()).isNull();
        } finally {
            directManager.disconnectNode(masterAddress);
            directManager.shutdown(0, 0, TimeUnit.SECONDS);
        }
    }
}
