/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.cluster;

import io.netty.resolver.AddressResolver;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.*;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.cluster.ClusterNodeInfo.Flag;
import org.redisson.cluster.ClusterPartition.Type;
import org.redisson.config.BaseMasterSlaveServersConfig;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.connection.*;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.misc.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ClusterConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ConcurrentMap<Integer, ClusterPartition> lastPartitions = new ConcurrentHashMap<>();

    private ScheduledFuture<?> monitorFuture;
    
    private volatile RedisURI lastClusterNode;
    
    private RedisStrictCommand<List<ClusterNodeInfo>> clusterNodesCommand;
    
    private String configEndpointHostName;
    
    private final AtomicReferenceArray<MasterSlaveEntry> slot2entry = new AtomicReferenceArray<>(MAX_SLOT);

    private final Map<RedisClient, MasterSlaveEntry> client2entry = new ConcurrentHashMap<>();

    private ClusterServersConfig cfg;

    public ClusterConnectionManager(ClusterServersConfig cfg, ServiceManager serviceManager) {
        super(cfg, serviceManager);
        this.serviceManager.setNatMapper(cfg.getNatMapper());
    }

    @Override
    protected MasterSlaveServersConfig create(BaseMasterSlaveServersConfig<?> cfg) {
        this.cfg = (ClusterServersConfig) cfg;
        return super.create(cfg);
    }

    @Override
    public void connect() {
        if (cfg.getNodeAddresses().isEmpty()) {
            throw new IllegalArgumentException("At least one cluster node should be defined!");
        }

        Throwable lastException = null;
        List<String> failedMasters = new ArrayList<>();
        for (String address : cfg.getNodeAddresses()) {
            RedisURI addr = new RedisURI(address);
            CompletionStage<RedisConnection> connectionFuture = connectToNode(cfg, addr, addr.getHost());
            try {
                RedisConnection connection = connectionFuture.toCompletableFuture().join();

                connection.async(RedisCommands.SPUBLISH, "", "").thenAccept(r -> {
                    subscribeService.setShardingSupported(true);
                });


                if (cfg.getNodeAddresses().size() == 1 && !addr.isIP()) {
                    configEndpointHostName = addr.getHost();
                }

                clusterNodesCommand = RedisCommands.CLUSTER_NODES;
                if (addr.isSsl()) {
                    clusterNodesCommand = RedisCommands.CLUSTER_NODES_SSL;
                }

                List<ClusterNodeInfo> nodes = connection.sync(clusterNodesCommand);

                StringBuilder nodesValue = new StringBuilder();
                for (ClusterNodeInfo clusterNodeInfo : nodes) {
                    nodesValue.append(clusterNodeInfo.getNodeInfo()).append("\n");
                }
                log.info("Redis cluster nodes configuration got from {}:\n{}", connection.getRedisClient().getAddr(), nodesValue);

                lastClusterNode = addr;

                CompletableFuture<Collection<ClusterPartition>> partitionsFuture = parsePartitions(nodes);
                Collection<ClusterPartition> partitions;
                try {
                    partitions = partitionsFuture.join();
                } catch (CompletionException e) {
                    lastException = e.getCause();
                    break;
                }

                List<CompletableFuture<Void>> masterFutures = new ArrayList<>();
                for (ClusterPartition partition : partitions) {
                    if (partition.isMasterFail()) {
                        failedMasters.add(partition.getMasterAddress().toString());
                        continue;
                    }
                    if (partition.getMasterAddress() == null) {
                        throw new IllegalStateException("Master node: " + partition.getNodeId() + " doesn't have address.");
                    }

                    CompletionStage<Void> masterFuture = addMasterEntry(partition, cfg);
                    masterFutures.add(masterFuture.toCompletableFuture());
                }

                CompletableFuture<Void> masterFuture = CompletableFuture.allOf(masterFutures.toArray(new CompletableFuture[0]));
                try {
                    masterFuture.join();
                } catch (CompletionException e) {
                    lastException = e.getCause();
                }
                break;
            } catch (Exception e) {
                if (e instanceof CompletionException) {
                    e = (Exception) e.getCause();
                }
                lastException = e;
                log.warn(e.getMessage());
            }
        }

        if (lastPartitions.isEmpty()) {
            shutdown();
            if (failedMasters.isEmpty()) {
                throw new RedisConnectionException("Can't connect to servers!", lastException);
            } else {
                throw new RedisConnectionException("Can't connect to servers! Failed masters according to cluster status: " + failedMasters, lastException);
            }
        }

        if (cfg.isCheckSlotsCoverage() && lastPartitions.size() != MAX_SLOT) {
            shutdown();
            if (failedMasters.isEmpty()) {
                throw new RedisConnectionException("Not all slots covered! Only " + lastPartitions.size() + " slots are available. Set checkSlotsCoverage = false to avoid this check.", lastException);
            } else {
                throw new RedisConnectionException("Not all slots covered! Only " + lastPartitions.size() + " slots are available. Set checkSlotsCoverage = false to avoid this check. Failed masters according to cluster status: " + failedMasters, lastException);
            }
        }

        scheduleClusterChangeCheck(cfg);
    }

    @Override
    public Collection<MasterSlaveEntry> getEntrySet() {
        return client2entry.values();
    }

    @Override
    public MasterSlaveEntry getEntry(RedisURI addr) {
        for (MasterSlaveEntry entry : client2entry.values()) {
            if (addr.equals(entry.getClient().getAddr())) {
                return entry;
            }
            if (entry.hasSlave(addr)) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public MasterSlaveEntry getEntry(RedisClient redisClient) {
        MasterSlaveEntry entry = client2entry.get(redisClient);
        if (entry != null) {
            return entry;
        }

        for (MasterSlaveEntry mentry : client2entry.values()) {
            if (mentry.hasSlave(redisClient)) {
                return mentry;
            }
        }
        return null;
    }

    @Override
    public MasterSlaveEntry getEntry(InetSocketAddress address) {
        for (MasterSlaveEntry entry : client2entry.values()) {
            InetSocketAddress addr = entry.getClient().getAddr();
            if (addr.getAddress().equals(address.getAddress()) && addr.getPort() == address.getPort()) {
                return entry;
            }
        }
        return null;
    }

    @Override
    protected CompletableFuture<RedisClient> changeMaster(int slot, RedisURI address) {
        MasterSlaveEntry entry = getEntry(slot);
        RedisClient oldClient = entry.getClient();
        CompletableFuture<RedisClient> future = super.changeMaster(slot, address);
        return future.thenApply(res -> {
            client2entry.remove(oldClient);
            client2entry.put(entry.getClient(), entry);
            return res;
        });
    }

    @Override
    protected MasterSlaveEntry getEntry(int slot) {
        return slot2entry.get(slot);
    }

    private void addEntry(Integer slot, MasterSlaveEntry entry) {
        MasterSlaveEntry oldEntry = slot2entry.getAndSet(slot, entry);
        if (oldEntry != entry) {
            entry.incReference();
            shutdownEntry(oldEntry);
        }
        client2entry.put(entry.getClient(), entry);
    }

    private void removeEntry(Integer slot) {
        MasterSlaveEntry entry = slot2entry.getAndSet(slot, null);
        shutdownEntry(entry);
    }

    private void shutdownEntry(MasterSlaveEntry entry) {
        if (entry != null && entry.decReference() == 0) {
            entry.getAllEntries().forEach(e -> {
                RedisURI uri = new RedisURI(e.getClient().getConfig().getAddress().getScheme(),
                        e.getClient().getAddr().getAddress().getHostAddress(),
                        e.getClient().getAddr().getPort());
                disconnectNode(uri);
                entry.nodeDown(e);
            });
            entry.masterDown();
            entry.shutdownAsync();
            subscribeService.remove(entry);
            RedisURI uri = new RedisURI(entry.getClient().getConfig().getAddress().getScheme(),
                                        entry.getClient().getAddr().getAddress().getHostAddress(),
                                        entry.getClient().getAddr().getPort());
            disconnectNode(uri);

            client2entry.remove(entry.getClient());

            String slaves = entry.getAllEntries().stream()
                    .filter(e -> !e.getClient().getAddr().equals(entry.getClient().getAddr()))
                    .map(e -> e.getClient().toString())
                    .collect(Collectors.joining(","));
            log.info("{} master and related slaves: {} removed", entry.getClient().getAddr(), slaves);
        }
    }

    @Override
    protected RedisClientConfig createRedisConfig(NodeType type, RedisURI address, int timeout, int commandTimeout, String sslHostname) {
        RedisClientConfig result = super.createRedisConfig(type, address, timeout, commandTimeout, sslHostname);
        result.setReadOnly(type == NodeType.SLAVE && config.getReadMode() != ReadMode.MASTER);
        return result;
    }
    
    private CompletionStage<Void> addMasterEntry(ClusterPartition partition, ClusterServersConfig cfg) {
        if (partition.isMasterFail()) {
            RedisException e = new RedisException("Failed to add master: " +
                    partition.getMasterAddress() + " for slot ranges: " +
                    partition.getSlotRanges() + ". Reason - server has FAIL flag");

            if (partition.getSlotsAmount() == 0) {
                e = new RedisException("Failed to add master: " +
                        partition.getMasterAddress() + ". Reason - server has FAIL flag");
            }
            CompletableFuture<Void> result = new CompletableFuture<>();
            result.completeExceptionally(e);
            return result;
        }

        CompletionStage<RedisConnection> connectionFuture = connectToNode(cfg, partition.getMasterAddress(), configEndpointHostName);
        return connectionFuture.thenCompose(connection -> {
            MasterSlaveServersConfig config = create(cfg);
            config.setMasterAddress(partition.getMasterAddress().toString());

            MasterSlaveEntry entry;
            if (config.isSlaveNotUsed()) {
                entry = new SingleEntry(this, serviceManager.getConnectionWatcher(), config);
            } else {
                Set<String> slaveAddresses = partition.getSlaveAddresses().stream().map(r -> r.toString()).collect(Collectors.toSet());
                config.setSlaveAddresses(slaveAddresses);

                entry = new MasterSlaveEntry(ClusterConnectionManager.this, serviceManager.getConnectionWatcher(), config);
            }

            CompletableFuture<RedisClient> f = entry.setupMasterEntry(new RedisURI(config.getMasterAddress()), configEndpointHostName);
            return f.thenCompose(masterClient -> {
                for (Integer slot : partition.getSlots()) {
                    addEntry(slot, entry);
                    lastPartitions.put(slot, partition);
                }

                if (!config.isSlaveNotUsed()) {
                    CompletableFuture<Void> fs = entry.initSlaveBalancer(partition.getFailedSlaveAddresses(), configEndpointHostName);
                    return fs.thenAccept(r -> {
                        if (!partition.getSlaveAddresses().isEmpty()) {
                            log.info("slaves: {} added for slot ranges: {}", partition.getSlaveAddresses(), partition.getSlotRanges());
                            if (!partition.getFailedSlaveAddresses().isEmpty()) {
                                log.warn("slaves: {} are down for slot ranges: {}", partition.getFailedSlaveAddresses(), partition.getSlotRanges());
                            }
                        }

                        log.info("master: {} added for slot ranges: {}", partition.getMasterAddress(), partition.getSlotRanges());
                    });
                }

                log.info("master: {} added for slot ranges: {}", partition.getMasterAddress(), partition.getSlotRanges());
                return CompletableFuture.completedFuture(null);
            });
        });
    }

    private void scheduleClusterChangeCheck(ClusterServersConfig cfg) {
        monitorFuture = serviceManager.getGroup().schedule(new Runnable() {
            @Override
            public void run() {
                if (configEndpointHostName != null) {
                    String address = cfg.getNodeAddresses().iterator().next();
                    RedisURI uri = new RedisURI(address);
                    AddressResolver<InetSocketAddress> resolver = serviceManager.getResolverGroup().getResolver(serviceManager.getGroup().next());
                    Future<List<InetSocketAddress>> allNodes = resolver.resolveAll(InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
                    allNodes.addListener(new FutureListener<List<InetSocketAddress>>() {
                        @Override
                        public void operationComplete(Future<List<InetSocketAddress>> future) throws Exception {
                            AtomicReference<Throwable> lastException = new AtomicReference<Throwable>(future.cause());
                            if (!future.isSuccess()) {
                                checkClusterState(cfg, Collections.emptyIterator(), lastException);
                                return;
                            }
                            
                            List<RedisURI> nodes = new ArrayList<>();
                            for (InetSocketAddress addr : future.getNow()) {
                                RedisURI address = serviceManager.toURI(uri.getScheme(), addr.getAddress().getHostAddress(), "" + addr.getPort());
                                nodes.add(address);
                            }
                            
                            Iterator<RedisURI> nodesIterator = nodes.iterator();
                            checkClusterState(cfg, nodesIterator, lastException);
                        }
                    });
                } else {
                    AtomicReference<Throwable> lastException = new AtomicReference<>();
                    List<RedisURI> nodes = new ArrayList<>();
                    List<RedisURI> slaves = new ArrayList<>();

                    for (ClusterPartition partition : getLastPartitions()) {
                        if (!partition.isMasterFail()) {
                            nodes.add(partition.getMasterAddress());
                        }

                        Set<RedisURI> partitionSlaves = new HashSet<>(partition.getSlaveAddresses());
                        partitionSlaves.removeAll(partition.getFailedSlaveAddresses());
                        slaves.addAll(partitionSlaves);
                    }
                    Collections.shuffle(nodes);
                    Collections.shuffle(slaves);
                    
                    // master nodes first
                    nodes.addAll(slaves);

                    Iterator<RedisURI> nodesIterator = nodes.iterator();

                    checkClusterState(cfg, nodesIterator, lastException);
                }
            }

        }, cfg.getScanInterval(), TimeUnit.MILLISECONDS);
    }

    private void checkClusterState(ClusterServersConfig cfg, Iterator<RedisURI> iterator, AtomicReference<Throwable> lastException) {
        if (!iterator.hasNext()) {
            if (lastException.get() != null) {
                log.error("Can't update cluster state", lastException.get());
            }
            scheduleClusterChangeCheck(cfg);
            return;
        }
        if (!serviceManager.getShutdownLatch().acquire()) {
            return;
        }
        RedisURI uri = iterator.next();
        CompletionStage<RedisConnection> connectionFuture = connectToNode(cfg, uri, configEndpointHostName);
        connectionFuture.whenComplete((connection, e) -> {
            if (e != null) {
                lastException.set(e);
                serviceManager.getShutdownLatch().release();
                checkClusterState(cfg, iterator, lastException);
                return;
            }

            updateClusterState(cfg, connection, iterator, uri, lastException);
        });
    }

    private void updateClusterState(ClusterServersConfig cfg, RedisConnection connection,
            Iterator<RedisURI> iterator, RedisURI uri, AtomicReference<Throwable> lastException) {
        RFuture<List<ClusterNodeInfo>> future = connection.async(clusterNodesCommand);
        future.whenComplete((nodes, e) -> {
                if (e != null) {
                    log.error("Unable to execute {}", clusterNodesCommand, e);
                    lastException.set(e);
                    serviceManager.getShutdownLatch().release();
                    checkClusterState(cfg, iterator, lastException);
                    return;
                }

                if (nodes.isEmpty()) {
                    log.debug("cluster nodes state got from {}: doesn't contain any nodes", connection.getRedisClient().getAddr());
                    serviceManager.getShutdownLatch().release();
                    checkClusterState(cfg, iterator, lastException);
                    return;
                }

                lastClusterNode = uri;

                if (log.isDebugEnabled()) {
                    StringBuilder nodesValue = new StringBuilder();
                    for (ClusterNodeInfo clusterNodeInfo : nodes) {
                        nodesValue.append(clusterNodeInfo.getNodeInfo()).append("\n");
                    }
                    log.debug("cluster nodes state got from {}:\n{}", connection.getRedisClient().getAddr(), nodesValue);
                }

                CompletableFuture<Collection<ClusterPartition>> newPartitionsFuture = parsePartitions(nodes);
                newPartitionsFuture
                        .whenComplete((r, ex) -> {
                            if (ex != null) {
                                StringBuilder nodesValue = new StringBuilder();
                                for (ClusterNodeInfo clusterNodeInfo : nodes) {
                                    nodesValue.append(clusterNodeInfo.getNodeInfo()).append("\n");
                                }
                                log.error("Unable to parse cluster nodes state got from: {}:\n{}", connection.getRedisClient().getAddr(), nodesValue, ex);
                                lastException.set(ex);
                                serviceManager.getShutdownLatch().release();
                                checkClusterState(cfg, iterator, lastException);
                            }
                        })
                        .thenCompose(newPartitions -> checkMasterNodesChange(cfg, newPartitions))
                        .thenCompose(r -> newPartitionsFuture)
                        .thenCompose(newPartitions -> checkSlaveNodesChange(newPartitions))
                        .thenCompose(r -> newPartitionsFuture)
                        .thenApply(newPartitions -> {
                            checkSlotsMigration(newPartitions);
                            checkSlotsChange(newPartitions);
                            serviceManager.getShutdownLatch().release();
                            scheduleClusterChangeCheck(cfg);
                            return newPartitions;
                        });
        });
    }

    private CompletableFuture<Void> checkSlaveNodesChange(Collection<ClusterPartition> newPartitions) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        Map<RedisURI, ClusterPartition> lastPartitions = getLastPartitonsByURI();
        for (ClusterPartition newPart : newPartitions) {
            ClusterPartition currentPart = lastPartitions.get(newPart.getMasterAddress());
            if (currentPart == null) {
                continue;
            }

            MasterSlaveEntry entry = getEntry(currentPart.slots().nextSetBit(0));
            // should be invoked first in order to remove stale failedSlaveAddresses
            CompletableFuture<Set<RedisURI>> addedSlavesFuture = addRemoveSlaves(entry, currentPart, newPart);
            CompletableFuture<Void> f = addedSlavesFuture.thenCompose(addedSlaves -> {
                // Do some slaves have changed state from failed to alive?
                return upDownSlaves(entry, currentPart, newPart, addedSlaves);
            });
            futures.add(f);
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                    .exceptionally(e -> {
                                        if (e != null) {
                                            log.error(e.getMessage(), e);
                                        }
                                        return null;
                                    });
    }

    private CompletableFuture<Void> upDownSlaves(MasterSlaveEntry entry, ClusterPartition currentPart, ClusterPartition newPart, Set<RedisURI> addedSlaves) {
        List<CompletableFuture<?>> futures = new ArrayList<>();

        List<RedisURI> nonFailedSlaves = currentPart.getFailedSlaveAddresses().stream()
                .filter(uri -> !addedSlaves.contains(uri) && !newPart.getFailedSlaveAddresses().contains(uri))
                .collect(Collectors.toList());
        nonFailedSlaves.forEach(uri -> {
            if (entry.hasSlave(uri)) {
                CompletableFuture<Boolean> f = entry.slaveUpAsync(uri, FreezeReason.MANAGER);
                f = f.thenCompose(v -> {
                    if (v) {
                        log.info("slave: {} is up for slot ranges: {}", uri, currentPart.getSlotRanges());
                        currentPart.removeFailedSlaveAddress(uri);
                        return entry.excludeMasterFromSlaves(uri);
                    }
                    return CompletableFuture.completedFuture(v);
                });
                futures.add(f);
            }
        });

        newPart.getFailedSlaveAddresses().stream()
                .filter(uri -> !currentPart.getFailedSlaveAddresses().contains(uri))
                .forEach(uri -> {
                    currentPart.addFailedSlaveAddress(uri);
                    CompletableFuture<Boolean> f = entry.slaveDownAsync(uri, FreezeReason.MANAGER);
                    f.thenApply(v -> {
                        if (v) {
                            disconnectNode(uri);
                            log.warn("slave: {} has down for slot ranges: {}", uri, currentPart.getSlotRanges());
                        }
                        return v;
                    });
                    futures.add(f);
                });
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private CompletableFuture<Set<RedisURI>> addRemoveSlaves(MasterSlaveEntry entry, ClusterPartition currentPart, ClusterPartition newPart) {
        List<CompletableFuture<?>> futures = new ArrayList<>();

        Set<RedisURI> removedSlaves = new HashSet<>(currentPart.getSlaveAddresses());
        removedSlaves.removeAll(newPart.getSlaveAddresses());

        for (RedisURI uri : removedSlaves) {
            currentPart.removeSlaveAddress(uri);

            CompletableFuture<Boolean> slaveDownFuture = entry.slaveDownAsync(uri, FreezeReason.MANAGER);
            slaveDownFuture.thenApply(r -> {
                log.info("slave {} removed for slot ranges: {}", uri, currentPart.getSlotRanges());
                return r;
            });
            futures.add(slaveDownFuture);
        }

        Set<RedisURI> addedSlaves = newPart.getSlaveAddresses().stream()
                                                                .filter(uri -> !currentPart.getSlaveAddresses().contains(uri)
                                                                                && !newPart.getFailedSlaveAddresses().contains(uri))
                                                                .collect(Collectors.toSet());

        for (RedisURI uri : addedSlaves) {
            ClientConnectionsEntry slaveEntry = entry.getEntry(uri);
            if (slaveEntry != null) {
                CompletableFuture<Boolean> slaveUpFuture = entry.slaveUpAsync(uri, FreezeReason.MANAGER);
                slaveUpFuture = slaveUpFuture.thenCompose(v -> {
                    if (v) {
                        currentPart.addSlaveAddress(uri);
                        log.info("slave: {} added for slot ranges: {}", uri, currentPart.getSlotRanges());
                        return entry.excludeMasterFromSlaves(uri);
                    }
                    return CompletableFuture.completedFuture(v);
                });
                futures.add(slaveUpFuture);
                continue;
            }

            CompletableFuture<Void> slaveUpFuture = entry.addSlave(uri, false, NodeType.SLAVE, configEndpointHostName);
            CompletableFuture<Void> f = slaveUpFuture.thenCompose(res -> {
                currentPart.addSlaveAddress(uri);
                log.info("slave: {} added for slot ranges: {}", uri, currentPart.getSlotRanges());
                return entry.excludeMasterFromSlaves(uri)
                        .thenApply(r -> null);
            });
            futures.add(f);
        }

        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return f.thenApply(r -> addedSlaves);
    }

    private ClusterPartition find(Collection<ClusterPartition> partitions, Integer slot) {
        return partitions.stream().filter(p -> p.hasSlot(slot)).findFirst().orElseThrow(() -> {
            return new IllegalStateException("Unable to find partition with slot " + slot);
        });
    }

    private CompletableFuture<Void> checkMasterNodesChange(ClusterServersConfig cfg, Collection<ClusterPartition> newPartitions) {
        Map<RedisURI, ClusterPartition> lastPartitions = getLastPartitonsByURI();
        Map<RedisURI, ClusterPartition> addedPartitions = new HashMap<>();
        Set<RedisURI> mastersElected = new HashSet<>();
        for (ClusterPartition newPart : newPartitions) {
            if (newPart.getSlotsAmount() == 0) {
                continue;
            }

            ClusterPartition currentPart = lastPartitions.get(newPart.getMasterAddress());
            boolean masterFound = currentPart != null;
            if (masterFound && newPart.isMasterFail()) {
                for (Integer slot : currentPart.getSlots()) {
                    ClusterPartition newMasterPart = find(newPartitions, slot);
                    // does partition has a new master?
                    if (!Objects.equals(newMasterPart.getMasterAddress(), currentPart.getMasterAddress())) {
                        RedisURI newUri = newMasterPart.getMasterAddress();
                        RedisURI oldUri = currentPart.getMasterAddress();

                        mastersElected.add(newUri);

                        CompletableFuture<RedisClient> future = changeMaster(slot, newUri);
                        currentPart.setMasterAddress(newUri);
                        future.whenComplete((res, e) -> {
                            if (e != null) {
                                currentPart.setMasterAddress(oldUri);
                            } else {
                                disconnectNode(oldUri);
                            }
                        });
                    }
                }
            }

            if (!masterFound && !newPart.isMasterFail()) {
                addedPartitions.put(newPart.getMasterAddress(), newPart);
            }
        }

        addedPartitions.keySet().removeAll(mastersElected);
        if (addedPartitions.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (ClusterPartition newPart : addedPartitions.values()) {
            CompletionStage<Void> future = addMasterEntry(newPart, cfg);
            futures.add(future.toCompletableFuture());
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                    .exceptionally(e -> {
                                        if (e != null) {
                                            log.error(e.getMessage(), e);
                                        }
                                        return null;
                                    });
    }

    private void checkSlotsChange(Collection<ClusterPartition> newPartitions) {
        int newSlotsAmount = newPartitions.stream()
                                .mapToInt(ClusterPartition::getSlotsAmount)
                                .sum();
        if (newSlotsAmount == lastPartitions.size() && lastPartitions.size() == MAX_SLOT) {
            return;
        }

        Set<Integer> removedSlots = lastPartitions.keySet().stream()
                .filter(s -> newPartitions.stream().noneMatch(p -> p.hasSlot(s)))
                .collect(Collectors.toSet());

        lastPartitions.keySet().removeAll(removedSlots);
        if (!removedSlots.isEmpty()) {
            log.info("{} slots found to remove", removedSlots.size());
        }

        for (Integer slot : removedSlots) {
            removeEntry(slot);
        }

        Integer addedSlots = 0;
        for (ClusterPartition clusterPartition : newPartitions) {
            MasterSlaveEntry entry = getEntry(clusterPartition.getMasterAddress());
            for (Integer slot : clusterPartition.getSlots()) {
                if (lastPartitions.containsKey(slot)) {
                    continue;
                }

                if (entry != null) {
                    addEntry(slot, entry);
                    lastPartitions.put(slot, clusterPartition);
                    addedSlots++;
                }
            }
        }
        if (addedSlots > 0) {
            log.info("{} slots found to add", addedSlots);
        }
    }
    
    private void checkSlotsMigration(Collection<ClusterPartition> newPartitions) {
        Set<ClusterPartition> clusterLastPartitions = getLastPartitions();

        // https://github.com/redisson/redisson/issues/3635
        Map<String, MasterSlaveEntry> nodeEntries = clusterLastPartitions.stream().collect(Collectors.toMap(p -> p.getNodeId(),
                                                                                    p -> getEntry(p.slots().nextSetBit(0))));

        Set<Integer> changedSlots = new HashSet<>();
        for (ClusterPartition currentPartition : clusterLastPartitions) {
            String nodeId = currentPartition.getNodeId();
            for (ClusterPartition newPartition : newPartitions) {
                if (!Objects.equals(nodeId, newPartition.getNodeId())) {
                    continue;
                }

                MasterSlaveEntry entry = nodeEntries.get(nodeId);
                BitSet addedSlots = newPartition.copySlots();
                addedSlots.andNot(currentPartition.slots());

                addedSlots.stream().forEach(slot -> {
                    addEntry(slot, entry);
                    lastPartitions.put(slot, currentPartition);
                    changedSlots.add(slot);
                });
                if (!addedSlots.isEmpty()) {
                    log.info("{} slots added to {}", addedSlots.cardinality(), currentPartition.getMasterAddress());
                }

                BitSet removedSlots = currentPartition.copySlots();
                removedSlots.andNot(newPartition.slots());

                removedSlots.stream().forEach(slot -> {
                    if (lastPartitions.remove(slot, currentPartition)) {
                        removeEntry(slot);
                        changedSlots.add(slot);
                    }
                });
                if (!removedSlots.isEmpty()) {
                    log.info("{} slots removed from {}", removedSlots.cardinality(), currentPartition.getMasterAddress());
                }

                if (!addedSlots.isEmpty() || !removedSlots.isEmpty()) {
                    // https://github.com/redisson/redisson/issues/3695, slotRanges not update when slots of node changed.
                    currentPartition.clear();
                    currentPartition.addSlotRanges(newPartition.getSlotRanges());
                }
                break;
            }
        }

        changedSlots.forEach(subscribeService::reattachPubSub);
    }
    
    private int indexOf(byte[] array, byte element) {
        for (int i = 0; i < array.length; ++i) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }  
    
    @Override
    public int calcSlot(byte[] key) {
        if (key == null) {
            return 0;
        }

        int start = indexOf(key, (byte) '{');
        if (start != -1) {
            int end = indexOf(key, (byte) '}');
            if (end != -1 && start + 1 < end) {
                key = Arrays.copyOfRange(key, start + 1, end);
            }
        }
        
        int result = CRC16.crc16(key) % MAX_SLOT;
        return result;
    }
    
    @Override
    public int calcSlot(String key) {
        if (key == null) {
            return 0;
        }

        int start = key.indexOf('{');
        if (start != -1) {
            int end = key.indexOf('}');
            if (end != -1 && start + 1 < end) {
                key = key.substring(start + 1, end);
            }
        }

        int result = CRC16.crc16(key.getBytes()) % MAX_SLOT;
        log.debug("slot {} for {}", result, key);
        return result;
    }

    private CompletableFuture<Collection<ClusterPartition>> parsePartitions(List<ClusterNodeInfo> nodes) {
        Map<String, ClusterPartition> partitions = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ClusterNodeInfo clusterNodeInfo : nodes) {
            if (clusterNodeInfo.containsFlag(Flag.NOADDR)
                    || clusterNodeInfo.containsFlag(Flag.HANDSHAKE)
                        || clusterNodeInfo.getAddress() == null
                            || (clusterNodeInfo.getSlotRanges().isEmpty() && clusterNodeInfo.containsFlag(Flag.MASTER))) {
                // skip it
                continue;
            }

            String masterId;
            if (clusterNodeInfo.containsFlag(Flag.SLAVE)) {
                masterId = clusterNodeInfo.getSlaveOf();
            } else {
                masterId = clusterNodeInfo.getNodeId();
            }

            if (masterId == null) {
                // skip it
                continue;
            }

            CompletableFuture<RedisURI> ipFuture = serviceManager.resolveIP(clusterNodeInfo.getAddress());
            CompletableFuture<Void> f = ipFuture.thenAccept(address -> {

                if (clusterNodeInfo.containsFlag(Flag.SLAVE)) {
                    ClusterPartition masterPartition = partitions.computeIfAbsent(masterId, k -> new ClusterPartition(masterId));
                    ClusterPartition slavePartition = partitions.computeIfAbsent(clusterNodeInfo.getNodeId(),
                            k -> new ClusterPartition(clusterNodeInfo.getNodeId()));
                    slavePartition.setType(Type.SLAVE);
                    slavePartition.setParent(masterPartition);

                    masterPartition.addSlaveAddress(address);
                    if (clusterNodeInfo.containsFlag(Flag.FAIL)) {
                        masterPartition.addFailedSlaveAddress(address);
                    }
                } else if (clusterNodeInfo.containsFlag(Flag.MASTER)) {
                    ClusterPartition masterPartition = partitions.computeIfAbsent(masterId, k -> new ClusterPartition(masterId));
                    masterPartition.addSlotRanges(clusterNodeInfo.getSlotRanges());
                    masterPartition.setMasterAddress(address);
                    masterPartition.setType(Type.MASTER);
                    if (clusterNodeInfo.containsFlag(Flag.FAIL)) {
                        masterPartition.setMasterFail(true);
                    }
                }
            });
            futures.add(f);
        }

        CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return future.thenApply(r -> {
            addCascadeSlaves(partitions.values());

            List<ClusterPartition> ps = partitions.values()
                    .stream()
                    .filter(cp -> cp.getType() == Type.MASTER
                                    && cp.getMasterAddress() != null
                                        && ((!cp.slots().isEmpty() && partitions.size() == 1) || partitions.size() > 1))
                    .collect(Collectors.toList());
            return ps;
        });
    }

    private void addCascadeSlaves(Collection<ClusterPartition> partitions) {
        Iterator<ClusterPartition> iter = partitions.iterator();
        while (iter.hasNext()) {
            ClusterPartition cp = iter.next();
            if (cp.getType() != Type.SLAVE) {
                continue;
            }
            
            if (cp.getParent() != null && cp.getParent().getType() == Type.MASTER) {
                ClusterPartition parent = cp.getParent();
                for (RedisURI addr : cp.getSlaveAddresses()) {
                    parent.addSlaveAddress(addr);
                }
                for (RedisURI addr : cp.getFailedSlaveAddresses()) {
                    parent.addFailedSlaveAddress(addr);
                }
            }
            iter.remove();
        }
    }

    @Override
    public void shutdown(long quietPeriod, long timeout, TimeUnit unit) {
        if (monitorFuture != null) {
            monitorFuture.cancel(true);
        }
        
        closeNodeConnections();
        super.shutdown(quietPeriod, timeout, unit);
    }

    private Map<RedisURI, ClusterPartition> getLastPartitonsByURI() {
        return lastPartitions.values().stream().collect(Collectors.toMap(p -> p.getMasterAddress(), p -> p, (e1, e2) -> e1));
    }

    private Set<ClusterPartition> getLastPartitions() {
        return new HashSet<>(lastPartitions.values());
    }
    
    @Override
    public RedisURI getLastClusterNode() {
        return lastClusterNode;
    }
    
    @Override
    public boolean isClusterMode() {
        return true;
    }
    
}

