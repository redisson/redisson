/**
 * Copyright 2018 Nikita Koksharov
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
import java.net.URI;
import java.util.ArrayList;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
import org.redisson.misc.RedissonPromise;
import org.redisson.misc.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.resolver.AddressResolver;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ClusterConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ConcurrentMap<Integer, ClusterPartition> lastPartitions = PlatformDependent.newConcurrentHashMap();

    private ScheduledFuture<?> monitorFuture;
    
    private volatile URI lastClusterNode;
    
    private RedisStrictCommand<List<ClusterNodeInfo>> clusterNodesCommand;
    
    private String configEndpointHostName;
    
    private boolean isConfigEndpoint;

    public ClusterConnectionManager(ClusterServersConfig cfg, Config config, UUID id) {
        super(config, id);

        this.config = create(cfg);
        initTimer(this.config);
        
        Throwable lastException = null;
        List<String> failedMasters = new ArrayList<String>();
        for (URI addr : cfg.getNodeAddresses()) {
            RFuture<RedisConnection> connectionFuture = connectToNode(cfg, addr, null, addr.getHost());
            try {
                RedisConnection connection = connectionFuture.syncUninterruptibly().getNow();

                if (cfg.getNodeAddresses().size() == 1 && NetUtil.createByteArrayFromIpAddressString(addr.getHost()) == null) {
                    configEndpointHostName = addr.getHost();
                    isConfigEndpoint = true;
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
    protected RedisClientConfig createRedisConfig(NodeType type, URI address, int timeout, int commandTimeout, String sslHostname) {
        RedisClientConfig result = super.createRedisConfig(type, address, timeout, commandTimeout, sslHostname);
        result.setReadOnly(type == NodeType.SLAVE && config.getReadMode() != ReadMode.MASTER);
        return result;
    }
    
    private RFuture<Collection<RFuture<Void>>> addMasterEntry(final ClusterPartition partition, final ClusterServersConfig cfg) {
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

        final RPromise<Collection<RFuture<Void>>> result = new RedissonPromise<Collection<RFuture<Void>>>();
        RFuture<RedisConnection> connectionFuture = connectToNode(cfg, partition.getMasterAddress(), null, configEndpointHostName);
        connectionFuture.addListener(new FutureListener<RedisConnection>() {
            @Override
            public void operationComplete(Future<RedisConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    log.error("Can't connect to master: {} with slot ranges: {}", partition.getMasterAddress(), partition.getSlotRanges());
                    result.tryFailure(future.cause());
                    return;
                }

                final RedisConnection connection = future.getNow();
                RFuture<Map<String, String>> clusterFuture = connection.async(RedisCommands.CLUSTER_INFO);
                clusterFuture.addListener(new FutureListener<Map<String, String>>() {

                    @Override
                    public void operationComplete(Future<Map<String, String>> future) throws Exception {
                        if (!future.isSuccess()) {
                            log.error("Can't execute CLUSTER_INFO for " + connection.getRedisClient().getAddr(), future.cause());
                            result.tryFailure(future.cause());
                            return;
                        }

                        Map<String, String> params = future.getNow();
                        if ("fail".equals(params.get("cluster_state"))) {
                            RedisException e = new RedisException("Failed to add master: " +
                                    partition.getMasterAddress() + " for slot ranges: " +
                                    partition.getSlotRanges() + ". Reason - cluster_state:fail");
                            log.error("cluster_state:fail for " + connection.getRedisClient().getAddr());
                            result.tryFailure(e);
                            return;
                        }

                        MasterSlaveServersConfig config = create(cfg);
                        config.setMasterAddress(partition.getMasterAddress());

                        final MasterSlaveEntry e;
                        List<RFuture<Void>> futures = new ArrayList<RFuture<Void>>();
                        if (config.checkSkipSlavesInit()) {
                            e = new SingleEntry(partition.getSlotRanges(), ClusterConnectionManager.this, config);
                        } else {
                            config.setSlaveAddresses(partition.getSlaveAddresses());

                            e = new MasterSlaveEntry(partition.getSlotRanges(), ClusterConnectionManager.this, config);

                            List<RFuture<Void>> fs = e.initSlaveBalancer(partition.getFailedSlaveAddresses());
                            futures.addAll(fs);
                            if (!partition.getSlaveAddresses().isEmpty()) {
                                log.info("slaves: {} added for slot ranges: {}", partition.getSlaveAddresses(), partition.getSlotRanges());
                                if (!partition.getFailedSlaveAddresses().isEmpty()) {
                                    log.warn("slaves: {} is down for slot ranges: {}", partition.getFailedSlaveAddresses(), partition.getSlotRanges());
                                }
                            }
                        }

                        RFuture<RedisClient> f = e.setupMasterEntry(config.getMasterAddress());
                        final RPromise<Void> initFuture = new RedissonPromise<Void>();
                        futures.add(initFuture);
                        f.addListener(new FutureListener<RedisClient>() {
                            @Override
                            public void operationComplete(Future<RedisClient> future) throws Exception {
                                if (!future.isSuccess()) {
                                    log.error("Can't add master: {} for slot ranges: {}", partition.getMasterAddress(), partition.getSlotRanges());
                                    initFuture.tryFailure(future.cause());
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
                            }
                        });
                        if (!result.trySuccess(futures)) {
                            throw new IllegalStateException();
                        }
                    }
                });

            }
        });

        return result;
    }

    private void scheduleClusterChangeCheck(final ClusterServersConfig cfg, final Iterator<URI> iterator) {
        monitorFuture = group.schedule(new Runnable() {
            @Override
            public void run() {
                if (isConfigEndpoint) {
                    final URI uri = cfg.getNodeAddresses().iterator().next();
                    final AddressResolver<InetSocketAddress> resolver = resolverGroup.getResolver(getGroup().next());
                    Future<List<InetSocketAddress>> allNodes = resolver.resolveAll(InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
                    allNodes.addListener(new FutureListener<List<InetSocketAddress>>() {
                        @Override
                        public void operationComplete(Future<List<InetSocketAddress>> future) throws Exception {
                            AtomicReference<Throwable> lastException = new AtomicReference<Throwable>(future.cause());
                            if (!future.isSuccess()) {
                                checkClusterState(cfg, Collections.<URI>emptyList().iterator(), lastException);
                                return;
                            }
                            
                            List<URI> nodes = new ArrayList<URI>();
                            for (InetSocketAddress addr : future.getNow()) {
                                URI node = URIBuilder.create(uri.getScheme() + "://" + addr.getAddress().getHostAddress() + ":" + addr.getPort());
                                nodes.add(node);
                            }
                            
                            Iterator<URI> nodesIterator = nodes.iterator();
                            checkClusterState(cfg, nodesIterator, lastException);
                        }
                    });
                } else {
                    AtomicReference<Throwable> lastException = new AtomicReference<Throwable>();
                    Iterator<URI> nodesIterator = iterator;
                    if (nodesIterator == null) {
                        List<URI> nodes = new ArrayList<URI>();
                        List<URI> slaves = new ArrayList<URI>();
                        
                        for (ClusterPartition partition : getLastPartitions()) {
                            if (!partition.isMasterFail()) {
                                nodes.add(partition.getMasterAddress());
                            }
                            
                            Set<URI> partitionSlaves = new HashSet<URI>(partition.getSlaveAddresses());
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

    private void checkClusterState(final ClusterServersConfig cfg, final Iterator<URI> iterator, final AtomicReference<Throwable> lastException) {
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
        final URI uri = iterator.next();
        RFuture<RedisConnection> connectionFuture = connectToNode(cfg, uri, null, configEndpointHostName);
        connectionFuture.addListener(new FutureListener<RedisConnection>() {
            @Override
            public void operationComplete(Future<RedisConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    lastException.set(future.cause());
                    getShutdownLatch().release();
                    checkClusterState(cfg, iterator, lastException);
                    return;
                }

                RedisConnection connection = future.getNow();
                updateClusterState(cfg, connection, iterator, uri);
            }
        });
    }

    private void updateClusterState(final ClusterServersConfig cfg, final RedisConnection connection, final Iterator<URI> iterator, final URI uri) {
        RFuture<List<ClusterNodeInfo>> future = connection.async(clusterNodesCommand);
        future.addListener(new FutureListener<List<ClusterNodeInfo>>() {
            @Override
            public void operationComplete(Future<List<ClusterNodeInfo>> future) throws Exception {
                if (!future.isSuccess()) {
                    log.error("Can't execute CLUSTER_NODES with " + connection.getRedisClient().getAddr(), future.cause());
                    closeNodeConnection(connection);
                    getShutdownLatch().release();
                    scheduleClusterChangeCheck(cfg, iterator);
                    return;
                }

                lastClusterNode = uri;
                
                List<ClusterNodeInfo> nodes = future.getNow();
                final StringBuilder nodesValue = new StringBuilder();
                if (log.isDebugEnabled()) {
                    for (ClusterNodeInfo clusterNodeInfo : nodes) {
                        nodesValue.append(clusterNodeInfo.getNodeInfo()).append("\n");
                    }
                    log.debug("cluster nodes state from {}:\n{}", connection.getRedisClient().getAddr(), nodesValue);
                }

                final Collection<ClusterPartition> newPartitions = parsePartitions(nodes);
                RFuture<Void> masterFuture = checkMasterNodesChange(cfg, newPartitions);
                checkSlaveNodesChange(newPartitions);
                masterFuture.addListener(new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        checkSlotsMigration(newPartitions);
                        checkSlotsChange(cfg, newPartitions);
                        getShutdownLatch().release();
                        scheduleClusterChangeCheck(cfg, null);
                    }
                });
            }
        });
    }

    private void checkSlaveNodesChange(Collection<ClusterPartition> newPartitions) {
        Set<ClusterPartition> lastPartitions = getLastPartitions();
        for (ClusterPartition newPart : newPartitions) {
            for (ClusterPartition currentPart : lastPartitions) {
                if (!newPart.getMasterAddress().equals(currentPart.getMasterAddress())) {
                    continue;
                }

                MasterSlaveEntry entry = getEntry(currentPart.getSlots().iterator().next());
                // should be invoked first in order to remove stale failedSlaveAddresses
                Set<URI> addedSlaves = addRemoveSlaves(entry, currentPart, newPart);
                // Do some slaves have changed state from failed to alive?
                upDownSlaves(entry, currentPart, newPart, addedSlaves);

                break;
            }
        }
    }

    private void upDownSlaves(final MasterSlaveEntry entry, final ClusterPartition currentPart, final ClusterPartition newPart, Set<URI> addedSlaves) {
        Set<URI> aliveSlaves = new HashSet<URI>(currentPart.getFailedSlaveAddresses());
        aliveSlaves.removeAll(addedSlaves);
        aliveSlaves.removeAll(newPart.getFailedSlaveAddresses());
        for (URI uri : aliveSlaves) {
            currentPart.removeFailedSlaveAddress(uri);
            if (entry.hasSlave(uri) && entry.slaveUp(uri, FreezeReason.MANAGER)) {
                log.info("slave: {} has up for slot ranges: {}", uri, currentPart.getSlotRanges());
            }
        }

        Set<URI> failedSlaves = new HashSet<URI>(newPart.getFailedSlaveAddresses());
        failedSlaves.removeAll(currentPart.getFailedSlaveAddresses());
        for (URI uri : failedSlaves) {
            currentPart.addFailedSlaveAddress(uri);
            if (entry.slaveDown(uri, FreezeReason.MANAGER)) {
                log.warn("slave: {} has down for slot ranges: {}", uri, currentPart.getSlotRanges());
            }
        }
    }

    private Set<URI> addRemoveSlaves(final MasterSlaveEntry entry, final ClusterPartition currentPart, ClusterPartition newPart) {
        Set<URI> removedSlaves = new HashSet<URI>(currentPart.getSlaveAddresses());
        removedSlaves.removeAll(newPart.getSlaveAddresses());

        for (URI uri : removedSlaves) {
            currentPart.removeSlaveAddress(uri);

            if (entry.slaveDown(uri, FreezeReason.MANAGER)) {
                log.info("slave {} removed for slot ranges: {}", uri, currentPart.getSlotRanges());
            }
        }

        Set<URI> addedSlaves = new HashSet<URI>(newPart.getSlaveAddresses());
        addedSlaves.removeAll(currentPart.getSlaveAddresses());
        for (final URI uri : addedSlaves) {
            RFuture<Void> future = entry.addSlave(uri);
            future.addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    if (!future.isSuccess()) {
                        log.error("Can't add slave: " + uri, future.cause());
                        return;
                    }

                    currentPart.addSlaveAddress(uri);
                    entry.slaveUp(uri, FreezeReason.MANAGER);
                    log.info("slave: {} added for slot ranges: {}", uri, currentPart.getSlotRanges());
                }
            });
        }
        return addedSlaves;
    }

    private int slotsAmount(Collection<ClusterPartition> partitions) {
        int result = 0;
        for (ClusterPartition clusterPartition : partitions) {
            result += clusterPartition.getSlots().size();
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
        for (final ClusterPartition newPart : newPartitions) {
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
                        URI newUri = newMasterPart.getMasterAddress();
                        URI oldUri = currentPart.getMasterAddress();
                        
                        changeMaster(slot, newUri);
                        
                        currentPart.setMasterAddress(newMasterPart.getMasterAddress());
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
        
        final RPromise<Void> result = new RedissonPromise<Void>();
        final AtomicInteger masters = new AtomicInteger(newMasters.size());
        final Queue<RFuture<Void>> futures = new ConcurrentLinkedQueue<RFuture<Void>>(); 
        for (ClusterPartition newPart : newMasters) {
            RFuture<Collection<RFuture<Void>>> future = addMasterEntry(newPart, cfg);
            future.addListener(new FutureListener<Collection<RFuture<Void>>>() {
                @Override
                public void operationComplete(Future<Collection<RFuture<Void>>> future) throws Exception {
                    if (future.isSuccess()) {
                        futures.addAll(future.getNow());
                    }
                    
                    if (masters.decrementAndGet() == 0) {
                        final AtomicInteger nodes = new AtomicInteger(futures.size());
                        for (RFuture<Void> nodeFuture : futures) {
                            nodeFuture.addListener(new FutureListener<Void>() {
                                @Override
                                public void operationComplete(Future<Void> future) throws Exception {
                                    if (nodes.decrementAndGet() == 0) {
                                        result.trySuccess(null);
                                    }
                                }
                            });
                        }
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
                if (clusterPartition.getSlots().contains(slot)) {
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
            if (entry.getSlotRanges().isEmpty()) {
                entry.shutdownAsync();
                log.info("{} master and slaves for it removed", entry.getClient().getAddr());
            }
        }

        Set<Integer> addedSlots = new HashSet<Integer>();
        for (ClusterPartition clusterPartition : newPartitions) {
            for (Integer slot : clusterPartition.getSlots()) {
                if (!lastPartitions.containsKey(slot)) {
                    addedSlots.add(slot);
                }
            }
        }
        if (!addedSlots.isEmpty()) {
            log.info("{} slots found to add", addedSlots.size());
        }
        for (final Integer slot : addedSlots) {
            ClusterPartition partition = find(newPartitions, slot);
            
            Set<Integer> oldSlots = new HashSet<Integer>(partition.getSlots());
            oldSlots.removeAll(addedSlots);
            if (oldSlots.isEmpty()) {
                continue;
            }
            
            MasterSlaveEntry entry = getEntry(oldSlots.iterator().next());
            if (entry != null) {
                addEntry(slot, entry);
                lastPartitions.put(slot, partition);
            }
        }
    }

    private void checkSlotsMigration(Collection<ClusterPartition> newPartitions) {
        for (ClusterPartition currentPartition : getLastPartitions()) {
            for (ClusterPartition newPartition : newPartitions) {
                if (!currentPartition.getNodeId().equals(newPartition.getNodeId()) 
                        // skip master change case
                        || !currentPartition.getMasterAddress().equals(newPartition.getMasterAddress())) {
                    continue;
                }
                
                MasterSlaveEntry entry = getEntry(currentPartition.getSlots().iterator().next());
                Set<Integer> addedSlots = new HashSet<Integer>(newPartition.getSlots());
                addedSlots.removeAll(currentPartition.getSlots());
                currentPartition.addSlots(addedSlots);
                

                for (Integer slot : addedSlots) {
                    addEntry(slot, entry);
                    lastPartitions.put(slot, currentPartition);
                }
                if (!addedSlots.isEmpty()) {
                    log.info("{} slots added to {}", addedSlots.size(), currentPartition.getMasterAddress());
                }

                Set<Integer> removedSlots = new HashSet<Integer>(currentPartition.getSlots());
                removedSlots.removeAll(newPartition.getSlots());
                for (Integer removeSlot : removedSlots) {
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

            if (clusterNodeInfo.containsFlag(Flag.SLAVE)) {
                slavePartition.setParent(partition);
                
                partition.addSlaveAddress(clusterNodeInfo.getAddress());
                if (clusterNodeInfo.containsFlag(Flag.FAIL)) {
                    partition.addFailedSlaveAddress(clusterNodeInfo.getAddress());
                }
            } else {
                partition.addSlotRanges(clusterNodeInfo.getSlotRanges());
                partition.setMasterAddress(clusterNodeInfo.getAddress());
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
                for (URI addr : cp.getSlaveAddresses()) {
                    parent.addSlaveAddress(addr);
                }
                for (URI addr : cp.getFailedSlaveAddresses()) {
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
        monitorFuture.cancel(true);
        
        closeNodeConnections();
        super.shutdown();
    }

    private Set<ClusterPartition> getLastPartitions() {
        return new HashSet<ClusterPartition>(lastPartitions.values());
    }
    
    @Override
    public URI getLastClusterNode() {
        return lastClusterNode;
    }
    
    @Override
    public boolean isClusterMode() {
        return true;
    }
    
}

