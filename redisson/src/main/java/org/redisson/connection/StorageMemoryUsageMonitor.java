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

import io.netty.util.Timeout;
import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.misc.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author seakider
 */
public class StorageMemoryUsageMonitor {
    private static final Logger log = LoggerFactory.getLogger(StorageMemoryUsageMonitor.class);

    private final ConnectionManager connectionManager;

    private final Map<RedisURI, Integer> nodeMemoryUsages = new ConcurrentHashMap<>();
    private final StorageMemoryUsageListener listener;

    private volatile Timeout storageMemoryUsageMonitorFuture;
    private final long monitoringInterval;

    private static final String USED_MEMORY_DATASET_PERC = "used_memory_dataset_perc";

    public StorageMemoryUsageMonitor(ConnectionManager connectionManager, long monitoringInterval) {
        this.connectionManager = connectionManager;
        this.monitoringInterval = monitoringInterval;
        this.listener = connectionManager.getServiceManager().getCfg().getStorageMemoryUsageListener();
    }

    public void start() {
        monitor();
        log.debug("Storage memory usage monitoring enabled;");
    }

    public void stop() {
        if (storageMemoryUsageMonitorFuture != null) {
            storageMemoryUsageMonitorFuture.cancel();
        }
    }

    private void monitor() {
        storageMemoryUsageMonitorFuture = connectionManager.getServiceManager().newTimeout(t -> {
            if (connectionManager.getServiceManager().isShuttingDown()) {
                return;
            }

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (MasterSlaveEntry entry : connectionManager.getEntrySet()) {
                RedisURI uri = new RedisURI(entry.getClient().getConfig().getAddress().getScheme(),
                        entry.getClient().getAddr().getAddress().getHostAddress(),
                        entry.getClient().getAddr().getPort());
                //master
                futures.add(monitorNode(uri));

                // slaves
                entry.getAllEntries().forEach(e -> {
                    if (e.getNodeType().equals(NodeType.SLAVE)) {
                        RedisURI uriSlave = new RedisURI(e.getClient().getConfig().getAddress().getScheme(),
                                e.getClient().getAddr().getAddress().getHostAddress(),
                                e.getClient().getAddr().getPort());
                        futures.add(monitorNode(uriSlave));
                    }
                });
            }

            CompletableFuture.allOf(
                            futures.toArray(new CompletableFuture[0]))
                    .whenComplete((r, e) -> monitor());
        }, monitoringInterval, TimeUnit.MILLISECONDS);
    }

    private CompletableFuture<Void> monitorNode(RedisURI uri) {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        CompletionStage<RedisConnection> connectionFuture = ((MasterSlaveConnectionManager) connectionManager)
                .connectToNode(((MasterSlaveConnectionManager) connectionManager).config, uri, uri.getHost());
        connectionFuture.whenComplete((con, e) -> {
            if (e != null) {
                log.error("connect to node failed, RedisURI:{} ", uri, e);
                promise.complete(null);
                return;
            }

            RFuture<Map<String, String>> future = con.async(StringCodec.INSTANCE, RedisCommands.INFO_MEMORY);
            future.whenComplete((map, ex) -> {
                try {
                    if (ex != null) {
                        log.error("monitor node failed ", ex);
                        return;
                    }

                    int usagePercent = formatMetric(map, USED_MEMORY_DATASET_PERC);
                    Integer oldValue = nodeMemoryUsages.put(uri, usagePercent);

                    if (oldValue == null || oldValue != usagePercent) {
                        listener.onMemoryUsageChange(uri, usagePercent);
                    }

                } catch (Exception exception) {
                    log.error("Process monitor result failed", e);
                } finally {
                    promise.complete(null);
                }

            });

        });
        return promise;
    }

    private int formatMetric(Map<String, String> info, String fieldName) {
        String value = info.get(fieldName);

        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(
                    "Redis INFO MEMORY missing " + fieldName);
        }

        try {
            return (int) Math.round(Double.parseDouble(value.replace("%", "")));
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Invalid " + fieldName + ": " + value, e);
        }
    }

}
