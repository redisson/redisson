/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import io.netty.util.concurrent.ScheduledFuture;
import org.redisson.api.NodeType;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.*;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.misc.AsyncCountDownLatch;
import org.redisson.misc.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * {@link ConnectionManager} for AWS ElastiCache Replication Groups or Azure Redis Cache. By providing all nodes
 * of the replication group to this manager, the role of each node can be polled to determine
 * if a failover has occurred resulting in a new master.
 *
 * @author Nikita Koksharov
 * @author Steve Ungerer
 */
public class ReplicatedConnectionManager extends MasterSlaveConnectionManager {

    private static final String ROLE_KEY = "role";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final AtomicReference<InetSocketAddress> currentMaster = new AtomicReference<>();

    private ScheduledFuture<?> monitorFuture;

    private enum Role {
        master,
        slave
    }

    public ReplicatedConnectionManager(ReplicatedServersConfig cfg, Config config, UUID id) {
        super(config, id);

        this.config = create(cfg);
        initTimer(this.config);

        for (String address : cfg.getNodeAddresses()) {
            RedisURI addr = new RedisURI(address);
            CompletionStage<RedisConnection> connectionFuture = connectToNode(cfg, addr, addr.getHost());
            RedisConnection connection = null;
            try {
                connection = connectionFuture.toCompletableFuture().join();
            } catch (Exception e) {
                // skip
            }
            if (connection == null) {
                continue;
            }

            Role role = Role.valueOf(connection.sync(RedisCommands.INFO_REPLICATION).get(ROLE_KEY));
            if (Role.master.equals(role)) {
                currentMaster.set(connection.getRedisClient().getAddr());
                log.info("{} is the master", addr);
                this.config.setMasterAddress(addr.toString());
            } else {
                log.info("{} is a slave", addr);
                this.config.addSlaveAddress(addr.toString());
            }
        }

        if (currentMaster.get() == null) {
            stopThreads();
            throw new RedisConnectionException("Can't connect to servers!");
        }
        if (this.config.getReadMode() != ReadMode.MASTER && this.config.getSlaveAddresses().isEmpty()) {
            log.warn("ReadMode = " + this.config.getReadMode() + ", but slave nodes are not found! Please specify all nodes in replicated mode.");
        }

        initSingleEntry();

        scheduleMasterChangeCheck(cfg);
    }

    @Override
    protected void startDNSMonitoring(RedisClient masterHost) {
        // disabled
    }

    @Override
    protected MasterSlaveServersConfig create(BaseMasterSlaveServersConfig<?> cfg) {
        MasterSlaveServersConfig res = super.create(cfg);
        res.setDatabase(((ReplicatedServersConfig) cfg).getDatabase());
        return res;
    }
    
    private void scheduleMasterChangeCheck(ReplicatedServersConfig cfg) {
        if (isShuttingDown()) {
            return;
        }
        
        monitorFuture = group.schedule(() -> {
            if (isShuttingDown()) {
                return;
            }

            Set<InetSocketAddress> slaveIPs = Collections.newSetFromMap(new ConcurrentHashMap<>());
            AsyncCountDownLatch latch = new AsyncCountDownLatch();
            latch.latch(() -> {
                checkFailedSlaves(slaveIPs);
                scheduleMasterChangeCheck(cfg);
            }, cfg.getNodeAddresses().size());

            for (String address : cfg.getNodeAddresses()) {
                RedisURI uri = new RedisURI(address);
                checkNode(latch, uri, cfg, slaveIPs);
            }
        }, cfg.getScanInterval(), TimeUnit.MILLISECONDS);
    }

    private void checkFailedSlaves(Set<InetSocketAddress> slaveIPs) {
        MasterSlaveEntry entry = getEntry(singleSlotRange.getStartSlot());
        Set<RedisClient> failedSlaves = entry.getAllEntries().stream()
                .filter(e -> e.getNodeType() == NodeType.SLAVE
                                    && !slaveIPs.contains(e.getClient().getAddr()))
                .map(e -> e.getClient())
                .collect(Collectors.toSet());

        for (RedisClient slave : failedSlaves) {
            if (entry.slaveDown(slave.getAddr(), FreezeReason.MANAGER)) {
                log.info("slave: {} is down", slave);
                disconnectNode(new RedisURI(slave.getConfig().getAddress().getScheme(),
                                            slave.getAddr().getAddress().getHostAddress(),
                                            slave.getAddr().getPort()));
            }
        }
    }

    private void checkNode(AsyncCountDownLatch latch, RedisURI uri, ReplicatedServersConfig cfg, Set<InetSocketAddress> slaveIPs) {
        CompletionStage<RedisConnection> connectionFuture = connectToNode(cfg, uri, uri.getHost());
        connectionFuture
                .thenCompose(c -> resolveIP(uri))
                .thenCompose(ip -> {
                    if (isShuttingDown()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    RedisConnection connection = connectionFuture.toCompletableFuture().join();
                    if (!RedisURI.compare(connection.getRedisClient().getAddr(), ip)) {
                        disconnectNode(uri);
                        log.info("Hostname: " + uri + " has changed IP from: "
                                    + connection.getRedisClient().getAddr() + " to " + ip);
                        return CompletableFuture.<Map<String, String>>completedFuture(null);
                    }

                    return connection.async(RedisCommands.INFO_REPLICATION);
                })
                .thenCompose(r -> {
                    if (r == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    RedisConnection connection = connectionFuture.toCompletableFuture().join();
                    InetSocketAddress addr = connection.getRedisClient().getAddr();
                    Role role = Role.valueOf(r.get(ROLE_KEY));
                    if (Role.master.equals(role)) {
                        InetSocketAddress master = currentMaster.get();
                        if (master.equals(addr)) {
                            log.debug("Current master {} unchanged", master);
                            return CompletableFuture.completedFuture(null);
                        } else if (currentMaster.compareAndSet(master, addr)) {
                            CompletableFuture<RedisClient> changeFuture = changeMaster(singleSlotRange.getStartSlot(), uri);
                            return changeFuture.exceptionally(e -> {
                                    log.error("Unable to change master to " + addr, e);
                                    currentMaster.compareAndSet(addr, master);
                                    return null;
                            });
                        }
                    } else if (!config.checkSkipSlavesInit()) {
                        CompletableFuture<Void> f = slaveUp(addr, uri);
                        slaveIPs.add(addr);
                        return f.thenApply(re -> null);
                    }
                    return CompletableFuture.completedFuture(null);
                })
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.error(ex.getMessage(), ex);
                    }
                    latch.countDown();
                });
    }

    private CompletableFuture<Void> slaveUp(InetSocketAddress address, RedisURI uri) {
        MasterSlaveEntry entry = getEntry(singleSlotRange.getStartSlot());
        if (!entry.hasSlave(address)) {
            CompletableFuture<Void> f = entry.addSlave(address, uri, uri.getHost());
            return f.whenComplete((r, e) -> {
                if (e != null) {
                    log.error("Unable to add slave", e);
                    return;
                }

                log.info("slave: {} added", address);
            });
        } else if (entry.slaveUp(address, FreezeReason.MANAGER)) {
            log.info("slave: {} is up", address);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void shutdown() {
        if (monitorFuture != null) {
            monitorFuture.cancel(true);
        }
        
        closeNodeConnections();
        super.shutdown();
    }
}

