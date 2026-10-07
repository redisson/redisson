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

import org.redisson.api.NodeType;
import org.redisson.client.FailedNodeDetector;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.ConnectionsHolder;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Base connection pool class 
 * 
 * @author Nikita Koksharov
 *
 * @param <T> - connection type
 */
abstract class ConnectionPool<T extends RedisConnection> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    final ConnectionManager connectionManager;

    final MasterSlaveServersConfig config;

    final MasterSlaveEntry masterSlaveEntry;

    ConnectionPool(MasterSlaveServersConfig config, ConnectionManager connectionManager, MasterSlaveEntry masterSlaveEntry) {
        this.config = config;
        this.masterSlaveEntry = masterSlaveEntry;
        this.connectionManager = connectionManager;
    }

    protected abstract ConnectionsHolder<T> getConnectionHolder(ClientConnectionsEntry entry, boolean trackChanges);

    public Tuple<CompletableFuture<T>, Throwable> getTuple(RedisCommand<?> command, boolean trackChanges) {
        return getTuple(command, trackChanges, null);
    }

    protected Tuple<CompletableFuture<T>, Throwable> getTuple(RedisCommand<?> command, boolean trackChanges, ReadMode readMode) {
        Collection<ClientConnectionsEntry> entries = masterSlaveEntry.getAllEntries();
        List<ClientConnectionsEntry> entriesCopy = new ArrayList<>(entries);
        entriesCopy.removeIf(entry -> {
            if (entry.isFreezed()) {
                return true;
            }
            if (isHealthy(entry)) {
                return false;
            }

            FailedNodeDetector detector = entry.getClient().getConfig().getFailedNodeDetector();
            RedisConnectionException cause = new RedisConnectionException(
                    "Redis node has been marked as failed according to the detection logic defined in " + detector);
            shutdownAndReconnect(entry, detector, cause);
            return true;
        });
        if (readMode != null && readMode.isAvailabilityZoneAware()) {
            ClientConnectionsEntry entry = selectInAvailabilityZoneOrder(command, readMode, entriesCopy);
            if (entry != null) {
                return new Tuple<>(acquireConnection(command, entry, trackChanges), null);
            }
        } else if (!entriesCopy.isEmpty()) {
            ClientConnectionsEntry entry = config.getLoadBalancer().getEntry(entriesCopy, command);
            if (entry != null) {
                log.debug("Entry {} selected as connection source", entry);
                return new Tuple<>(acquireConnection(command, entry, trackChanges), null);
            }
        }
        
        List<InetSocketAddress> failed = new ArrayList<>();
        List<InetSocketAddress> freezed = new ArrayList<>();
        for (ClientConnectionsEntry entry : entries) {
            if (entry.getClient().getConfig().getFailedNodeDetector().isNodeFailed(entry.getClient().getAddr())) {
                failed.add(entry.getClient().getAddr());
            } else if (entry.isFreezed()) {
                freezed.add(entry.getClient().getAddr());
            }
        }

        StringBuilder errorMsg = new StringBuilder(getClass().getSimpleName() + " no available Redis entries. " +
                "Master entry host: " + masterSlaveEntry.getClient().getAddr() + " entries " + entries);
        if (!freezed.isEmpty()) {
            errorMsg.append(" Disconnected hosts: ").append(freezed);
        }
        if (!failed.isEmpty()) {
            errorMsg.append(" Hosts disconnected by 'failedNodeDetector:' ").append(failed);
        }

        RedisConnectionException exception = new RedisConnectionException(errorMsg.toString());
        return new Tuple<>(null, exception);
    }

    private ClientConnectionsEntry selectInAvailabilityZoneOrder(RedisCommand<?> command, ReadMode readMode,
                                                                 List<ClientConnectionsEntry> entriesCopy) {
        String zone = config.getClientAvailabilityZone();
        Map<Integer, List<ClientConnectionsEntry>> byStep = new TreeMap<>();
        for (ClientConnectionsEntry entry : entriesCopy) {
            int step = availabilityZoneStep(readMode, entry.getNodeType() == NodeType.SLAVE,
                    zone != null && zone.equals(entry.getAvailabilityZone()));
            byStep.computeIfAbsent(step, k -> new ArrayList<>()).add(entry);
        }

        for (Map.Entry<Integer, List<ClientConnectionsEntry>> step : byStep.entrySet()) {
            ClientConnectionsEntry entry = config.getLoadBalancer().getEntry(step.getValue(), command);
            if (entry != null) {
                log.debug("Entry {} selected as connection source, availability zone step {}", entry, step.getKey());
                return entry;
            }
        }
        return null;
    }

    public CompletableFuture<T> get(RedisCommand<?> command, boolean trackChanges) {
        return get(command, trackChanges, null);
    }

    public CompletableFuture<T> get(RedisCommand<?> command, boolean trackChanges, ReadMode readMode) {
        Tuple<CompletableFuture<T>, Throwable> tuple = getTuple(command, trackChanges, readMode);
        if (tuple.getT2() != null) {
            CompletableFuture<T> result = new CompletableFuture<>();
            result.completeExceptionally(tuple.getT2());
            return result;
        }
        return tuple.getT1();
    }

    public CompletableFuture<T> get(RedisCommand<?> command, ClientConnectionsEntry entry, boolean trackChanges) {
        return acquireConnection(command, entry, trackChanges);
    }

    protected final CompletableFuture<T> acquireConnection(RedisCommand<?> command, ClientConnectionsEntry entry, boolean trackChanges) {
        ConnectionsHolder<T> handler = getConnectionHolder(entry, trackChanges);
        CompletableFuture<T> result = handler.acquireConnection(command);
        CompletableFuture<T> cancelableFuture = new CompletableFuture<>();
        cancelableFuture.whenComplete((r, e) -> {
            if (e != null) {
                result.completeExceptionally(e);
            }
        });
        result.whenComplete((r, e) -> {
            if (e != null) {
                if (entry.getNodeType() == NodeType.SLAVE) {
                    FailedNodeDetector detector = entry.getClient().getConfig().getFailedNodeDetector();
                    detector.onConnectFailed(e, entry.getClient().getAddr());
                    if (detector.isNodeFailed(entry.getClient().getAddr())) {
                        shutdownAndReconnect(entry, detector, e);
                    }
                }
                cancelableFuture.completeExceptionally(e);
                return;
            }

            entry.addHandler(r, handler);

            if (entry.getNodeType() == NodeType.SLAVE) {
                entry.getClient().getConfig().getFailedNodeDetector()
                        .onConnectSuccessful(entry.getClient().getAddr());
            }

            if (!cancelableFuture.complete(r)) {
                entry.returnConnection(r);
            }
        });
        return cancelableFuture;
    }
        
    private static int availabilityZoneStep(ReadMode readMode, boolean slave, boolean local) {
        switch (readMode) {
            case AZ_AFFINITY:
                // slaves in the zone, then any slave, then the master (GLDE AZ_AFFINITY)
                if (slave && local) {
                    return 0;
                }
                if (slave) {
                    return 1;
                }
                return 2;
            case AZ_AFFINITY_SLAVES_AND_MASTER:
                // slaves in the zone, then the master in the zone, then any slave, then the master
                // (GLDE AZ_AFFINITY_REPLICAS_AND_PRIMARY)
                if (slave && local) {
                    return 0;
                }
                if (local) {
                    return 1;
                }
                if (slave) {
                    return 2;
                }
                return 3;
            case AZ_AFFINITY_MASTER_SLAVE:
                // any node in the zone, then any node (GLDE AZ_AFFINITY_ALL_NODES)
                if (local) {
                    return 0;
                }
                return 1;
            default:
                // ReadMode.isAvailabilityZoneAware accepted a mode that has no order here
                throw new IllegalStateException("No availability zone order defined for readMode " + readMode);
        }
    }

    private boolean isHealthy(ClientConnectionsEntry entry) {
        if (entry.getNodeType() != NodeType.SLAVE) {
            return true;
        }

        FailedNodeDetector detector = entry.getClient().getConfig().getFailedNodeDetector();
        return !detector.isNodeFailed(entry.getClient().getAddr());
    }

    private void shutdownAndReconnect(ClientConnectionsEntry entry, FailedNodeDetector detector, Throwable cause) {
        log.error("Redis node {} has been marked as failed according to the detection logic defined in {}",
                entry.getClient().getAddr(), detector);
        masterSlaveEntry.shutdownAndReconnectAsync(entry.getClient(), cause);
    }

}
