/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.*;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.misc.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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

    private static final Logger log = LoggerFactory.getLogger(ReplicatedConnectionManager.class);

    private final AtomicReference<InetSocketAddress> currentMaster = new AtomicReference<>();

    private volatile Timeout monitorFuture;

    private enum Role {
        master,
        slave
    }

    private ReplicatedServersConfig cfg;

    public ReplicatedConnectionManager(ReplicatedServersConfig cfg, Config configCopy) {
        super(cfg, configCopy);
    }

    @Override
    public void doConnect(Function<RedisURI, String> hostnameMapper) {
        if (cfg.getNodeAddresses().isEmpty()) {
            throw new IllegalArgumentException("At least one Redis node should be defined!");
        }

        Exception ex = null;
        for (String address : cfg.getNodeAddresses()) {
            RedisURI addr = new RedisURI(address);
            CompletionStage<RedisConnection> connectionFuture = connectToNode(cfg, addr, addr.getHost());
            RedisConnection connection = null;
            try {
                connection = connectionFuture.toCompletableFuture().join();
            } catch (Exception e) {
                if (ex != null) {
                    ex.addSuppressed(e);
                } else {
                    ex = e;
                }
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
            internalShutdown();
            throw new RedisConnectionException("Can't connect to servers!", ex);
        }
        if (this.config.getReadMode() != ReadMode.MASTER && this.config.getSlaveAddresses().isEmpty()) {
            log.warn("ReadMode = {}, but slave nodes are not found! Please specify all nodes in replicated mode.", this.config.getReadMode());
        }

        super.doConnect(hostnameMapper);

        scheduleMasterChangeCheck(cfg);
    }

    @Override
    protected void startDNSMonitoring(RedisClient masterHost) {
        // disabled
    }

    @Override
    protected MasterSlaveServersConfig create(BaseMasterSlaveServersConfig<?> cfg) {
        this.cfg = (ReplicatedServersConfig) cfg;
        MasterSlaveServersConfig res = super.create(cfg);
        res.setDatabase(((ReplicatedServersConfig) cfg).getDatabase());
        return res;
    }
    
    private void scheduleMasterChangeCheck(ReplicatedServersConfig cfg) {
        if (serviceManager.isShuttingDown()) {
            return;
        }
        
        monitorFuture = serviceManager.newTimeout(t -> {
            if (serviceManager.isShuttingDown()) {
                return;
            }

            Set<InetSocketAddress> slaveIPs = Collections.newSetFromMap(new ConcurrentHashMap<>());
            List<CompletableFuture<Role>> roles = cfg.getNodeAddresses().stream()
                .map(address -> {
                    RedisURI uri = new RedisURI(address);
                    return checkNode(uri, cfg, slaveIPs);
                })
                .collect(Collectors.toList());

            CompletableFuture<Void> f = CompletableFuture.allOf(roles.toArray(new CompletableFuture[0]));
            f.whenComplete((r, e) -> {
                if (e == null) {
                    if (roles.stream().noneMatch(role -> Role.master.equals(role.getNow(Role.slave)))) {
                        log.error("No master available among the configured addresses, "
                                + "please check your configuration.");
                    }

                    checkFailedSlaves(slaveIPs);
                }

                scheduleMasterChangeCheck(cfg);
            });

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
            if (config.isSlaveNotUsed() || entry.slaveDown(slave.getAddr(), FreezeReason.MANAGER)) {
                log.info("slave: {} is down", slave);
                disconnectNode(new RedisURI(slave.getConfig().getAddress().getScheme(),
                                            slave.getAddr().getAddress().getHostAddress(),
                                            slave.getAddr().getPort()));
            }
        }
    }

    private CompletableFuture<Role> checkNode(RedisURI uri, ReplicatedServersConfig cfg, Set<InetSocketAddress> slaveIPs) {
        CompletionStage<RedisConnection> connectionFuture = connectToNode(cfg, uri, uri.getHost());
        return connectionFuture
                .thenCompose(c -> {
                    if (cfg.isMonitorIPChanges()) {
                        return serviceManager.resolveIP(uri);
                    }
                    return CompletableFuture.completedFuture(uri);
                })
                .thenCompose(ip -> {
                    if (serviceManager.isShuttingDown()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    RedisConnection connection = connectionFuture.toCompletableFuture().join();
                    if (cfg.isMonitorIPChanges() && !ip.equals(connection.getRedisClient().getAddr())) {
                        disconnectNode(uri);
                        log.info("Hostname: {} has changed IP from: {} to {}", uri, connection.getRedisClient().getAddr(), ip);
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
                        } else if (currentMaster.compareAndSet(master, addr)) {
                            CompletableFuture<RedisClient> changeFuture = changeMaster(singleSlotRange.getStartSlot(), uri);
                            return changeFuture.handle((ignored, e) -> {
                                if (e != null) {
                                    log.error("Unable to change master to {}", addr, e);
                                    currentMaster.compareAndSet(addr, master);
                                }
                                return role;
                            });
                        }
                    } else if (!config.isSlaveNotUsed()) {
                        CompletableFuture<Void> f = slaveUp(addr, uri);
                        slaveIPs.add(addr);
                        return f.thenApply(re -> role);
                    }
                    return CompletableFuture.completedFuture(role);
                })
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.error(ex.getMessage(), ex);
                    }
                })
                .toCompletableFuture();
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

                entry.excludeMasterFromSlaves(address);
                log.info("slave: {} added", address);
            });
        }

        return entry.slaveUpAsync(address, FreezeReason.MANAGER).thenAccept(r -> {
            if (r) {
                log.info("slave: {} is up", address);
            }
        });
    }

    @Override
    public void shutdown(long quietPeriod, long timeout, TimeUnit unit) {
        if (monitorFuture != null) {
            monitorFuture.cancel();
        }
        
        closeNodeConnections();
        super.shutdown(quietPeriod, timeout, unit);
    }
}

