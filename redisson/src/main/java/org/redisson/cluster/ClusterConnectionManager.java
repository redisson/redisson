/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.cluster.ClusterNodeInfo.Flag;
import org.redisson.cluster.ClusterPartition.Type;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.connection.CRC16;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.connection.MasterSlaveConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.SingleEntry;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedisURI;
import org.redisson.misc.RedissonPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.resolver.AddressResolver;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;

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
    
    private final Map<String, String> natMap;
    
    public ClusterConnectionManager(ClusterServersConfig cfg, Config config, UUID id) {
        super(config, id);

        if (cfg.getNodeAddresses().isEmpty()) {
            throw new IllegalArgumentException("At least one cluster node should be defined!");
        }

        natMap = cfg.getNatMap();
        this.config = create(cfg);
        initTimer(this.config);
        
        Throwable lastException = null;
        List<String> failedMasters = new ArrayList<String>();
        for (String address : cfg.getNodeAddresses()) {
            RedisURI addr = new RedisURI(address);
            RFuture<RedisConnection> connectionFuture = connectToNode(cfg, addr, null, addr.getHost());
            try {
                RedisConnection connection = connectionFuture.syncUninterruptibly().getNow();

                if (cfg.getNodeAddresses().size() == 1 && NetUtil.createByteArrayFromIpAddressString(addr.getHost()) == null) {
                    configEndpointHostName = addr.getHost();
                }
                
                clusterNodesCommand = RedisCommands.CLUSTER_NODES;
                if ("rediss".equals(addr.getScheme())) {
                    clusterNodesCommand = RedisCommands.CLUSTER_NODES_SSL;
                }
                
                List<ClusterNodeInfo> nodes = connection.sync(clusterNodesCommand);
                
                StringBuilder nodesValue = new StringBuilder();
                for (ClusterNodeInfo clusterNodeInfo : nodes) {
                    nodesValue.append(clusterNodeInfo.getNodeInfo()).append("\n");
                }
                log.info("Redis cluster nodes configuration got from {}:\n{}", connection.getRedisClient().getAddr(), nodesValue);

                lastClusterNode = addr;
                
                Collection<ClusterPartition> partitions = parsePartitions(nodes);
                List<RFuture<Collection<RFuture<Void>>>> futures = new ArrayList<RFuture<Collection<RFuture<Void>>>>();
                for (ClusterPartition partition : partitions) {
                    if (partition.isMasterFail()) {
                        failedMasters.add(partition.getMasterAddress().toString());
                        continue;
                    }
                    RFuture<Collection<RFuture<Void>>> masterFuture = addMasterEntry(partition, cfg);
                    futures.add(masterFuture);
                }

                for (RFuture<Collection<RFuture<Void>>> masterFuture : futures) {
                    masterFuture.awaitUninterruptibly();
                    if (!masterFuture.isSuccess()) {
                        lastException = masterFuture.cause();
                        continue;
                    }
                    for (RFuture<Void> future : masterFuture.getNow()) {
                        future.awaitUninterruptibly();
                        if (!future.isSuccess()) {
                            lastException = future.cause();
                        }
                    }
                }
                break;
            } catch (Exception e) {
                lastException = e;
                log.warn(e.getMessage());
            }
        }

        if (lastPartitions.isEmpty()) {
            stopThreads();
            if (failedMasters.isEmpty()) {
                throw new RedisConnectionException("Can't connect to servers!", lastException);
            } else {
                throw new RedisConnectionException("Can't connect to servers! Failed masters according to cluster status: " + failedMasters, lastException);
            }
        }

        if (lastPartitions.size() != MAX_SLOT) {
            stopThreads();
            if (failedMasters.isEmpty()) {
                throw new RedisConnectionException("Not all slots are covered! Only " + lastPartitions.size() + " slots are avaliable", lastException);
            } else {
                throw new RedisConnectionException("Not all slots are covered! Only " + lastPartitions.size() + " slots are avaliable. Failed masters according to cluster status: " + failedMasters, lastException);
            }
        }
        
        scheduleClusterChangeCheck(cfg, null);
    }
    
    @Override
    protected RedisClientConfig createRedisConfig(NodeType type, RedisURI address, int timeout, int commandTimeout, String sslHostname) {
        RedisClientConfig result = super.createRedisConfig(type, address, timeout, commandTimeout, sslHostname);
        result.setReadOnly(type == NodeType.SLAVE && config.getReadMode() != ReadMode.MASTER);
        return result;
    }
    
    private RFuture<Collection<RFuture<Void>>> addMasterEntry(ClusterPartition partition, ClusterServersConfig cfg) {
        if (partition.isMasterFail()) {
            RedisException e = new RedisException("Failed to add master: " +
                    partition.getMasterAddress() + " for slot ranges: " +
                    partition.getSlotRanges() + ". Reason - server has FAIL flag");

            if (partition.getSlotRanges().isEmpty()) {
                e = new RedisException("Failed to add master: " +
                        partition.getMasterAddress() + ". Reason - server has FAIL flag");
            }
            return RedissonPromise.newFailedFuture(e);
        }

        RPromise<Collection<RFuture<Void>>> result = new RedissonPromise<Collection<RFuture<Void>>>();
        RFuture<RedisConnection> connectionFuture = connectToNode(cfg, partition.getMasterAddress(), null, configEndpointHostName);
        connectionFuture.onComplete((connection, ex1) -> {
            if (ex1 != null) {
                log.error("Can't connect to master: {} with slot ranges: {}", partition.getMasterAddress(), partition.getSlotRanges());
                result.tryFailure(ex1);
                return;
            }

            RFuture<Map<String, String>> clusterFuture = connection.async(RedisCommands.CLUSTER_INFO);
            clusterFuture.onComplete((params, ex2) -> {
                if (ex2 != null) {
                    log.error("Can't execute CLUSTER_INFO for " + connection.getRedisClient().getAddr(), ex2);
                    result.tryFailure(ex2);
                    return;
                }

                if ("fail".equals(params.get("cluster_state"))) {
                    RedisException e = new RedisException("Failed to add master: " +
                            partition.getMasterAddress() + " for slot ranges: " +
                            partition.getSlotRanges() + ". Reason - cluster_state:fail");
                    log.error("cluster_state:fail for " + connection.getRedisClient().getAddr());
                    result.tryFailure(e);
                    return;
                }

                MasterSlaveServersConfig config = create(cfg);
                config.setMasterAddress(partition.getMasterAddress().toString());

                MasterSlaveEntry e;
                List<RFuture<Void>> futures = new ArrayList<RFuture<Void>>();
                if (config.checkSkipSlavesInit()) {
                    e = new SingleEntry(ClusterConnectionManager.this, config);
                } else {
                    Set<String> slaveAddresses = partition.getSlaveAddresses().stream().map(r -> r.toString()).collect(Collectors.toSet());
                    config.setSlaveAddresses(slaveAddresses);

                    e = new MasterSlaveEntry(ClusterConnectionManager.this, config);

                    List<RFuture<Void>> fs = e.initSlaveBalancer(partition.getFailedSlaveAddresses());
                    futures.addAll(fs);
                    if (!partition.getSlaveAddresses().isEmpty()) {
                        log.info("slaves: {} added for slot ranges: {}", partition.getSlaveAddresses(), partition.getSlotRanges());
                        if (!partition.getFailedSlaveAddresses().isEmpty()) {
                            log.warn("slaves: {} is down for slot ranges: {}", partition.getFailedSlaveAddresses(), partition.getSlotRanges());
                        }
                    }
                }

                RFuture<RedisClient> f = e.setupMasterEntry(new RedisURI(config.getMasterAddress()));
                RPromise<Void> initFuture = new RedissonPromise<Void>();
                futures.add(initFuture);
                f.onComplete((res, ex3) -> {
                    if (ex3 != null) {
                        log.error("Can't add master: {} for slot ranges: {}", partition.getMasterAddress(), partition.getSlotRanges());
                        initFuture.tryFailure(ex3);
                        return;
                    }
                    for (Integer slot : partition.getSlots()) {
                        addEntry(slot, e);
                        lastPartitions.put(slot, partition);
                    }

                    log.info("master: {} added for slot ranges: {}", partition.getMasterAddress(), partition.getSlotRanges());
                    if (!initFuture.trySuccess(null)) {
                        throw new IllegalStateException();
                    }
                });
                if (!result.trySuccess(futures)) {
                    throw new IllegalStateException();
                }
            });
        });

        return result;
    }

    private void scheduleClusterChangeCheck(ClusterServersConfig cfg, Iterator<RedisURI> iterator) {
        monitorFuture = group.schedule(new Runnable() {
            @Override
            public void run() {
                if (configEndpointHostName != null) {
                    String address = cfg.getNodeAddresses().iterator().next();
                    RedisURI uri = new RedisURI(address);
                    AddressResolver<InetSocketAddress> resolver = resolverGroup.getResolver(getGroup().next());
                    Future<List<InetSocketAddress>> allNodes = resolver.resolveAll(InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
                    allNodes.addListener(new FutureListener<List<InetSocketAddress>>() {
                        @Override
                        public void operationComplete(Future<List<InetSocketAddress>> future) throws Exception {
                            AtomicReference<Throwable> lastException = new AtomicReference<Throwable>(future.cause());
                            if (!future.isSuccess()) {
                                checkClusterState(cfg, Collections.<RedisURI>emptyList().iterator(), lastException);
                                return;
                            }
                            
                            List<RedisURI> nodes = new ArrayList<>();
                            for (InetSocketAddress addr : future.getNow()) {
                                RedisURI node = new RedisURI(uri.getScheme() + "://" + addr.getAddress().getHostAddress() + ":" + addr.getPort());
                                RedisURI address = applyNatMap(node);
                                nodes.add(address);
                            }
                            
                            Iterator<RedisURI> nodesIterator = nodes.iterator();
                            checkClusterState(cfg, nodesIterator, lastException);
                        }
                    });
                } else {
                    AtomicReference<Throwable> lastException = new AtomicReference<Throwable>();
                    Iterator<RedisURI> nodesIterator = iterator;
                    if (nodesIterator == null) {
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
                        // master nodes first
                        nodes.addAll(slaves);
                        
                        nodesIterator = nodes.iterator();
                    }
                    
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
            scheduleClusterChangeCheck(cfg, null);
            return;
        }
        if (!getShutdownLatch().acquire()) {
            return;
        }
        RedisURI uri = iterator.next();
        RFuture<RedisConnection> connectionFuture = connectToNode(cfg, uri, null, configEndpointHostName);
        connectionFuture.onComplete((connection, e) -> {
            if (e != null) {
                lastException.set(e);
                getShutdownLatch().release();
                checkClusterState(cfg, iterator, lastException);
                return;
            }

            updateClusterState(cfg, connection, iterator, uri, lastException);
        });
    }

    private void updateClusterState(ClusterServersConfig cfg, RedisConnection connection, 
            Iterator<RedisURI> iterator, RedisURI uri, AtomicReference<Throwable> lastException) {
        RFuture<List<ClusterNodeInfo>> future = connection.async(clusterNodesCommand);
        future.onComplete((nodes, e) -> {
                if (e != null) {
                    log.error("Can't execute CLUSTER_NODES with " + connection.getRedisClient().getAddr(), e);
                    closeNodeConnection(connection);
                    lastException.set(e);
                    getShutdownLatch().release();
                    checkClusterState(cfg, iterator, lastException);
                    return;
                }

                lastClusterNode = uri;

                StringBuilder nodesValue = new StringBuilder();
                if (log.isDebugEnabled()) {
                    for (ClusterNodeInfo clusterNodeInfo : nodes) {
                        nodesValue.append(clusterNodeInfo.getNodeInfo()).append("\n");
                    }
                    log.debug("cluster nodes state got from {}:\n{}", connection.getRedisClient().getAddr(), nodesValue);
                }

                Collection<ClusterPartition> newPartitions = parsePartitions(nodes);
                RFuture<Void> masterFuture = checkMasterNodesChange(cfg, newPartitions);
                checkSlaveNodesChange(newPartitions);
                masterFuture.onComplete((res, ex) -> {
                    checkSlotsMigration(newPartitions);
                    checkSlotsChange(cfg, newPartitions);
                    getShutdownLatch().release();
                    scheduleClusterChangeCheck(cfg, null);
                });
        });
    }

    private void checkSlaveNodesChange(Collection<ClusterPartition> newPartitions) {
        Set<ClusterPartition> lastPartitions = getLastPartitions();
        for (ClusterPartition newPart : newPartitions) {
            for (ClusterPartition currentPart : lastPartitions) {
                if (!newPart.getMasterAddress().equals(currentPart.getMasterAddress())) {
                    continue;
                }

                MasterSlaveEntry entry = getEntry(currentPart.slots().nextSetBit(0));
                // should be invoked first in order to remove stale failedSlaveAddresses
                Set<RedisURI> addedSlaves = addRemoveSlaves(entry, currentPart, newPart);
                // Do some slaves have changed state from failed to alive?
                upDownSlaves(entry, currentPart, newPart, addedSlaves);

                break;
            }
        }
    }

    private void upDownSlaves(MasterSlaveEntry entry, ClusterPartition currentPart, ClusterPartition newPart, Set<RedisURI> addedSlaves) {
        Set<RedisURI> aliveSlaves = new HashSet<>(currentPart.getFailedSlaveAddresses());
        aliveSlaves.removeAll(addedSlaves);
        aliveSlaves.removeAll(newPart.getFailedSlaveAddresses());
        for (RedisURI uri : aliveSlaves) {
            currentPart.removeFailedSlaveAddress(uri);
            if (entry.hasSlave(uri) && entry.slaveUp(uri, FreezeReason.MANAGER)) {
                log.info("slave: {} has up for slot ranges: {}", uri, currentPart.getSlotRanges());
            }
        }

        Set<RedisURI> failedSlaves = new HashSet<>(newPart.getFailedSlaveAddresses());
        failedSlaves.removeAll(currentPart.getFailedSlaveAddresses());
        for (RedisURI uri : failedSlaves) {
            currentPart.addFailedSlaveAddress(uri);
            if (entry.slaveDown(uri, FreezeReason.MANAGER)) {
                log.warn("slave: {} has down for slot ranges: {}", uri, currentPart.getSlotRanges());
            }
        }
    }

    private Set<RedisURI> addRemoveSlaves(MasterSlaveEntry entry, ClusterPartition currentPart, ClusterPartition newPart) {
        Set<RedisURI> removedSlaves = new HashSet<>(currentPart.getSlaveAddresses());
        removedSlaves.removeAll(newPart.getSlaveAddresses());

        for (RedisURI uri : removedSlaves) {
            currentPart.removeSlaveAddress(uri);

            if (entry.slaveDown(uri, FreezeReason.MANAGER)) {
                log.info("slave {} removed for slot ranges: {}", uri, currentPart.getSlotRanges());
            }
        }

        Set<RedisURI> addedSlaves = new HashSet<>(newPart.getSlaveAddresses());
        addedSlaves.removeAll(currentPart.getSlaveAddresses());
        for (RedisURI uri : addedSlaves) {
            RFuture<Void> future = entry.addSlave(uri);
            future.onComplete((res, ex) -> {
                if (ex != null) {
                    log.error("Can't add slave: " + uri, ex);
                    return;
                }

                currentPart.addSlaveAddress(uri);
                entry.slaveUp(uri, FreezeReason.MANAGER);
                log.info("slave: {} added for slot ranges: {}", uri, currentPart.getSlotRanges());
            });
        }
        return addedSlaves;
    }

    private int slotsAmount(Collection<ClusterPartition> partitions) {
        int result = 0;
        for (ClusterPartition clusterPartition : partitions) {
            result += clusterPartition.getSlotsAmount();
        }
        return result;
    }

    private ClusterPartition find(Collection<ClusterPartition> partitions, Integer slot) {
        for (ClusterPartition clusterPartition : partitions) {
            for (ClusterSlotRange slotRange : clusterPartition.getSlotRanges()) {
                if (slotRange.isOwn(slot)) {
                    return clusterPartition;
                }
            }
        }
        return null;
    }

    private RFuture<Void> checkMasterNodesChange(ClusterServersConfig cfg, Collection<ClusterPartition> newPartitions) {
        List<ClusterPartition> newMasters = new ArrayList<ClusterPartition>();
        Set<ClusterPartition> lastPartitions = getLastPartitions();
        for (ClusterPartition newPart : newPartitions) {
            boolean masterFound = false;
            for (ClusterPartition currentPart : lastPartitions) {
                if (!newPart.getMasterAddress().equals(currentPart.getMasterAddress())) {
                    continue;
                }
                masterFound = true;
                // current master marked as failed
                if (!newPart.isMasterFail()) {
                    continue;
                }
                for (Integer slot : currentPart.getSlots()) {
                    ClusterPartition newMasterPart = find(newPartitions, slot);
                    // does partition has a new master?
                    if (!newMasterPart.getMasterAddress().equals(currentPart.getMasterAddress())) {
                        RedisURI newUri = newMasterPart.getMasterAddress();
                        RedisURI oldUri = currentPart.getMasterAddress();
                        
                        RFuture<RedisClient> future = changeMaster(slot, newUri);
                        future.onComplete((res, e) -> {
                            if (e != null) {
                                currentPart.setMasterAddress(oldUri);
                            }
                        });
                        
                        currentPart.setMasterAddress(newUri);
                    }
                }
                break;
            }

            if (!masterFound && !newPart.getSlotRanges().isEmpty()) {
                newMasters.add(newPart);
            }
        }
        
        if (newMasters.isEmpty()) {
            return RedissonPromise.newSucceededFuture(null);
        }
        
        RPromise<Void> result = new RedissonPromise<Void>();
        AtomicInteger masters = new AtomicInteger(newMasters.size());
        Queue<RFuture<Void>> futures = new ConcurrentLinkedQueue<RFuture<Void>>(); 
        for (ClusterPartition newPart : newMasters) {
            RFuture<Collection<RFuture<Void>>> future = addMasterEntry(newPart, cfg);
            future.onComplete((res, e) -> {
                if (e == null) {
                    futures.addAll(res);
                }
                
                if (masters.decrementAndGet() == 0) {
                    AtomicInteger nodes = new AtomicInteger(futures.size());
                    for (RFuture<Void> nodeFuture : futures) {
                        nodeFuture.onComplete((r, ex) -> {
                            if (nodes.decrementAndGet() == 0) {
                                result.trySuccess(null);
                            }
                        });
                    }
                }
            });
        }
        return result;
    }

    private void checkSlotsChange(ClusterServersConfig cfg, Collection<ClusterPartition> newPartitions) {
        int newSlotsAmount = slotsAmount(newPartitions);
        if (newSlotsAmount == lastPartitions.size() && lastPartitions.size() == MAX_SLOT) {
            return;
        }

        Set<Integer> removedSlots = new HashSet<Integer>();
        for (Integer slot : lastPartitions.keySet()) {
            boolean found = false;
            for (ClusterPartition clusterPartition : newPartitions) {
                if (clusterPartition.hasSlot(slot)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                removedSlots.add(slot);
            }
        }
        lastPartitions.keySet().removeAll(removedSlots);
        if (!removedSlots.isEmpty()) {
            log.info("{} slots found to remove", removedSlots.size());
        }

        for (Integer slot : removedSlots) {
            MasterSlaveEntry entry = removeEntry(slot);
            if (entry.getReferences() == 0) {
                entry.shutdownAsync();
                log.info("{} master and slaves for it removed", entry.getClient().getAddr());
            }
        }

        BitSet addedSlots = new BitSet();
        for (ClusterPartition clusterPartition : newPartitions) {
            for (Integer slot : clusterPartition.getSlots()) {
                if (!lastPartitions.containsKey(slot)) {
                    addedSlots.set(slot);
                }
            }
        }
        if (!addedSlots.isEmpty()) {
            log.info("{} slots found to add", addedSlots.size());
        }
        for (Integer slot : (Iterable<Integer>) addedSlots.stream()::iterator) {
            ClusterPartition partition = find(newPartitions, slot);
            
            BitSet oldSlots = partition.copySlots();
            oldSlots.andNot(addedSlots);
            if (oldSlots.isEmpty()) {
                continue;
            }
            
            MasterSlaveEntry entry = getEntry(oldSlots.nextSetBit(0));
            if (entry != null) {
                addEntry(slot, entry);
                lastPartitions.put(slot, partition);
            }
        }
    }
    
    private void checkSlotsMigration(Collection<ClusterPartition> newPartitions) {
        for (ClusterPartition currentPartition : getLastPartitions()) {
            for (ClusterPartition newPartition : newPartitions) {
                if (!currentPartition.getNodeId().equals(newPartition.getNodeId())) {
                    continue;
                }
                
                MasterSlaveEntry entry = getEntry(currentPartition.slots().nextSetBit(0));
                BitSet addedSlots = newPartition.copySlots();
                addedSlots.andNot(currentPartition.slots());
                currentPartition.addSlots(addedSlots);
                

                for (Integer slot : (Iterable<Integer>) addedSlots.stream()::iterator) {
                    addEntry(slot, entry);
                    lastPartitions.put(slot, currentPartition);
                }
                if (!addedSlots.isEmpty()) {
                    log.info("{} slots added to {}", addedSlots.size(), currentPartition.getMasterAddress());
                }

                BitSet removedSlots = currentPartition.copySlots();
                removedSlots.andNot(newPartition.slots());
                for (Integer removeSlot : (Iterable<Integer>) removedSlots.stream()::iterator) {
                    if (lastPartitions.remove(removeSlot, currentPartition)) {
                        removeEntry(removeSlot);
                    }
                }
                currentPartition.removeSlots(removedSlots);

                if (!removedSlots.isEmpty()) {
                    log.info("{} slots removed from {}", removedSlots.size(), currentPartition.getMasterAddress());
                }
                break;
            }
        }
    }
    
    public String getConfigEndpointHostName() {
        return configEndpointHostName;
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
            key = Arrays.copyOfRange(key, start+1, end);
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
            key = key.substring(start+1, end);
        }

        int result = CRC16.crc16(key.getBytes()) % MAX_SLOT;
        log.debug("slot {} for {}", result, key);
        return result;
    }

    public RedisURI applyNatMap(RedisURI address) {
        String mappedAddress = natMap.get(address.getHost() + ":" + address.getPort());
        if (mappedAddress != null) {
            return new RedisURI(address.getScheme() + "://" + mappedAddress);
        }
        return address;
    }
    
    private Collection<ClusterPartition> parsePartitions(List<ClusterNodeInfo> nodes) {
        Map<String, ClusterPartition> partitions = new HashMap<String, ClusterPartition>();
        for (ClusterNodeInfo clusterNodeInfo : nodes) {
            if (clusterNodeInfo.containsFlag(Flag.NOADDR) || clusterNodeInfo.containsFlag(Flag.HANDSHAKE)) {
                // skip it
                continue;
            }

            String id = clusterNodeInfo.getNodeId();
            ClusterPartition slavePartition = getPartition(partitions, id);

            if (clusterNodeInfo.containsFlag(Flag.SLAVE)) {
                id = clusterNodeInfo.getSlaveOf();
            }

            ClusterPartition partition = getPartition(partitions, id);

            RedisURI address = applyNatMap(clusterNodeInfo.getAddress());
            if (clusterNodeInfo.containsFlag(Flag.SLAVE)) {
                slavePartition.setParent(partition);
                
                partition.addSlaveAddress(address);
                if (clusterNodeInfo.containsFlag(Flag.FAIL)) {
                    partition.addFailedSlaveAddress(address);
                }
            } else {
                partition.addSlotRanges(clusterNodeInfo.getSlotRanges());
                partition.setMasterAddress(address);
                partition.setType(Type.MASTER);
                if (clusterNodeInfo.containsFlag(Flag.FAIL)) {
                    partition.setMasterFail(true);
                }
            }
        }
        
        addCascadeSlaves(partitions);
        
        return partitions.values();
    }

    private void addCascadeSlaves(Map<String, ClusterPartition> partitions) {
        Iterator<ClusterPartition> iter = partitions.values().iterator();
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

    private ClusterPartition getPartition(Map<String, ClusterPartition> partitions, String id) {
        ClusterPartition partition = partitions.get(id);
        if (partition == null) {
            partition = new ClusterPartition(id);
            partition.setType(Type.SLAVE);
            partitions.put(id, partition);
        }
        return partition;
    }

    @Override
    public void shutdown() {
        if (monitorFuture != null) {
            monitorFuture.cancel(true);
        }
        
        closeNodeConnections();
        super.shutdown();
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

