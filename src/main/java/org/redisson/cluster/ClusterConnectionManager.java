/**
 * Copyright 2016 Nikita Koksharov
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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.ClusterServersConfig;
import org.redisson.Config;
import org.redisson.MasterSlaveServersConfig;
import org.redisson.ReadMode;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.cluster.ClusterNodeInfo.Flag;
import org.redisson.connection.CRC16;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.connection.MasterSlaveConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.SingleEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.PlatformDependent;

public class ClusterConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Map<URI, RedisConnection> nodeConnections = PlatformDependent.newConcurrentHashMap();

    private final ConcurrentMap<Integer, ClusterPartition> lastPartitions = PlatformDependent.newConcurrentHashMap();

    private ScheduledFuture<?> monitorFuture;
    
    private volatile URI lastClusterNode;

    public ClusterConnectionManager(ClusterServersConfig cfg, Config config) {
        super(config);
        connectListener = new ClusterConnectionListener(cfg.getReadMode() != ReadMode.MASTER);

        this.config = create(cfg);
        init(this.config);

        Throwable lastException = null;
        List<String> failedMasters = new ArrayList<String>();
        for (URI addr : cfg.getNodeAddresses()) {
            Future<RedisConnection> connectionFuture = connect(cfg, addr);
            try {
                RedisConnection connection = connectionFuture.syncUninterruptibly().getNow();
                List<ClusterNodeInfo> nodes = connection.sync(RedisCommands.CLUSTER_NODES);
                
                if (log.isDebugEnabled()) {
                    StringBuilder nodesValue = new StringBuilder();
                    for (ClusterNodeInfo clusterNodeInfo : nodes) {
                        nodesValue.append(clusterNodeInfo.getNodeInfo()).append("\n");
                    }
                    log.debug("cluster nodes state from {}:\n{}", connection.getRedisClient().getAddr(), nodesValue);
                }

                lastClusterNode = addr;
                
                Collection<ClusterPartition> partitions = parsePartitions(nodes);
                List<Future<Collection<Future<Void>>>> futures = new ArrayList<Future<Collection<Future<Void>>>>();
                for (ClusterPartition partition : partitions) {
                    if (partition.isMasterFail()) {
                        failedMasters.add(partition.getMasterAddr().toString());
                        continue;
                    }
                    Future<Collection<Future<Void>>> masterFuture = addMasterEntry(partition, cfg);
                    futures.add(masterFuture);
                }

                for (Future<Collection<Future<Void>>> masterFuture : futures) {
                    masterFuture.awaitUninterruptibly();
                    if (!masterFuture.isSuccess()) {
                        lastException = masterFuture.cause();
                        continue;
                    }
                    for (Future<Void> future : masterFuture.getNow()) {
                        future.awaitUninterruptibly();
                        if (!future.isSuccess()) {
                            lastException = future.cause();
                            continue;
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
    
    private void close(RedisConnection conn) {
        if (nodeConnections.values().remove(conn)) {
            conn.closeAsync();
        }
    }
    
    private Future<RedisConnection> connect(ClusterServersConfig cfg, final URI addr) {
        RedisConnection connection = nodeConnections.get(addr);
        if (connection != null) {
            return newSucceededFuture(connection);
        }

        RedisClient client = createClient(addr.getHost(), addr.getPort(), cfg.getConnectTimeout(), cfg.getRetryInterval() * cfg.getRetryAttempts());
        final Promise<RedisConnection> result = newPromise();
        Future<RedisConnection> future = client.connectAsync();
        future.addListener(new FutureListener<RedisConnection>() {
            @Override
            public void operationComplete(Future<RedisConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    result.setFailure(future.cause());
                    return;
                }

                RedisConnection connection = future.getNow();
                Promise<RedisConnection> promise = newPromise();
                connectListener.onConnect(promise, connection, null, config);
                promise.addListener(new FutureListener<RedisConnection>() {
                    @Override
                    public void operationComplete(Future<RedisConnection> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.setFailure(future.cause());
                            return;
                        }

                        RedisConnection connection = future.getNow();
                        if (connection.isActive()) {
                            nodeConnections.put(addr, connection);
                            result.setSuccess(connection);
                        } else {
                            connection.closeAsync();
                            result.setFailure(new RedisException("Connection to " + connection.getRedisClient().getAddr() + " is not active!"));
                        }
                    }
                });
            }
        });

        return result;
    }

    @Override
    protected void initEntry(MasterSlaveServersConfig config) {
    }

    private Future<Collection<Future<Void>>> addMasterEntry(final ClusterPartition partition, final ClusterServersConfig cfg) {
        if (partition.isMasterFail()) {
            RedisException e = new RedisException("Failed to add master: " +
                    partition.getMasterAddress() + " for slot ranges: " +
                    partition.getSlotRanges() + ". Reason - server has FAIL flag");

            if (partition.getSlotRanges().isEmpty()) {
                e = new RedisException("Failed to add master: " +
                        partition.getMasterAddress() + ". Reason - server has FAIL flag");
            }
            return newFailedFuture(e);
        }

        final Promise<Collection<Future<Void>>> result = newPromise();
        Future<RedisConnection> connectionFuture = connect(cfg, partition.getMasterAddress());
        connectionFuture.addListener(new FutureListener<RedisConnection>() {
            @Override
            public void operationComplete(Future<RedisConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    log.error("Can't connect to master: {} with slot ranges: {}", partition.getMasterAddress(), partition.getSlotRanges());
                    result.setFailure(future.cause());
                    return;
                }

                final RedisConnection connection = future.getNow();
                Future<Map<String, String>> clusterFuture = connection.async(RedisCommands.CLUSTER_INFO);
                clusterFuture.addListener(new FutureListener<Map<String, String>>() {

                    @Override
                    public void operationComplete(Future<Map<String, String>> future) throws Exception {
                        if (!future.isSuccess()) {
                            log.error("Can't execute CLUSTER_INFO for " + connection.getRedisClient().getAddr(), future.cause());
                            result.setFailure(future.cause());
                            return;
                        }

                        Map<String, String> params = future.getNow();
                        if ("fail".equals(params.get("cluster_state"))) {
                            RedisException e = new RedisException("Failed to add master: " +
                                    partition.getMasterAddress() + " for slot ranges: " +
                                    partition.getSlotRanges() + ". Reason - cluster_state:fail");
                            log.error("cluster_state:fail for " + connection.getRedisClient().getAddr());
                            result.setFailure(e);
                            return;
                        }

                        MasterSlaveServersConfig config = create(cfg);
                        config.setMasterAddress(partition.getMasterAddress());

                        final MasterSlaveEntry e;
                        List<Future<Void>> futures = new ArrayList<Future<Void>>();
                        if (config.getReadMode() == ReadMode.MASTER) {
                            e = new SingleEntry(partition.getSlotRanges(), ClusterConnectionManager.this, config);
                        } else {
                            config.setSlaveAddresses(partition.getSlaveAddresses());

                            e = new MasterSlaveEntry(partition.getSlotRanges(), ClusterConnectionManager.this, config);

                            List<Future<Void>> fs = e.initSlaveBalancer(partition.getFailedSlaveAddresses());
                            futures.addAll(fs);
                            if (!partition.getSlaveAddresses().isEmpty()) {
                                log.info("slaves: {} added for slot ranges: {}", partition.getSlaveAddresses(), partition.getSlotRanges());
                                if (!partition.getFailedSlaveAddresses().isEmpty()) {
                                    log.warn("slaves: {} is down for slot ranges: {}", partition.getFailedSlaveAddresses(), partition.getSlotRanges());
                                }
                            }
                        }

                        Future<Void> f = e.setupMasterEntry(config.getMasterAddress().getHost(), config.getMasterAddress().getPort());
                        final Promise<Void> initFuture = newPromise();
                        futures.add(initFuture);
                        f.addListener(new FutureListener<Void>() {
                            @Override
                            public void operationComplete(Future<Void> future) throws Exception {
                                if (!future.isSuccess()) {
                                    log.error("Can't add master: {} for slot ranges: {}", partition.getMasterAddress(), partition.getSlotRanges());
                                    initFuture.setFailure(future.cause());
                                    return;
                                }
                                for (Integer slot : partition.getSlots()) {
                                    addEntry(slot, e);
                                    lastPartitions.put(slot, partition);
                                }

                                log.info("master: {} added for slot ranges: {}", partition.getMasterAddress(), partition.getSlotRanges());
                                initFuture.setSuccess(null);
                            }
                        });
                        result.setSuccess(futures);
                    }
                });

            }
        });

        return result;
    }

    private void scheduleClusterChangeCheck(final ClusterServersConfig cfg, final Iterator<URI> iterator) {
        monitorFuture = GlobalEventExecutor.INSTANCE.schedule(new Runnable() {
            @Override
            public void run() {
                AtomicReference<Throwable> lastException = new AtomicReference<Throwable>();
                Iterator<URI> nodesIterator = iterator;
                if (nodesIterator == null) {
                    List<URI> nodes = new ArrayList<URI>();
                    List<URI> slaves = new ArrayList<URI>();
                    if (lastPartitions.isEmpty()) {
                        System.out.println("lastPartitions.isEmpty()");
                    }
                    
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

        }, cfg.getScanInterval(), TimeUnit.MILLISECONDS);
    }

    private void checkClusterState(final ClusterServersConfig cfg, final Iterator<URI> iterator, final AtomicReference<Throwable> lastException) {
        if (!iterator.hasNext()) {
            log.error("Can't update cluster state", lastException.get());
            scheduleClusterChangeCheck(cfg, null);
            return;
        }
        final URI uri = iterator.next();
        Future<RedisConnection> connectionFuture = connect(cfg, uri);
        connectionFuture.addListener(new FutureListener<RedisConnection>() {
            @Override
            public void operationComplete(Future<RedisConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    lastException.set(future.cause());
                    checkClusterState(cfg, iterator, lastException);
                    return;
                }

                RedisConnection connection = future.getNow();
                updateClusterState(cfg, connection, iterator, uri);
            }
        });
    }

    private void updateClusterState(final ClusterServersConfig cfg, final RedisConnection connection, final Iterator<URI> iterator, final URI uri) {
        Future<List<ClusterNodeInfo>> future = connection.async(RedisCommands.CLUSTER_NODES);
        future.addListener(new FutureListener<List<ClusterNodeInfo>>() {
            @Override
            public void operationComplete(Future<List<ClusterNodeInfo>> future) throws Exception {
                if (!future.isSuccess()) {
                    log.error("Can't execute CLUSTER_NODES with " + connection.getRedisClient().getAddr(), future.cause());
                    close(connection);
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
                Future<Void> masterFuture = checkMasterNodesChange(cfg, newPartitions);
                checkSlaveNodesChange(newPartitions);
                masterFuture.addListener(new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        checkSlotsMigration(newPartitions, nodesValue.toString());
                        checkSlotsChange(cfg, newPartitions, nodesValue.toString());
                        scheduleClusterChangeCheck(cfg, null);
                    }
                });
            }
        });
    }

    private void checkSlaveNodesChange(Collection<ClusterPartition> newPartitions) {
        for (ClusterPartition newPart : newPartitions) {
            for (ClusterPartition currentPart : getLastPartitions()) {
                if (!newPart.getMasterAddress().equals(currentPart.getMasterAddress())) {
                    continue;
                }

                MasterSlaveEntry entry = getEntry(currentPart.getMasterAddr());
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
            if (entry.slaveUp(uri.getHost(), uri.getPort(), FreezeReason.MANAGER)) {
                log.info("slave: {} has up for slot ranges: {}", uri, currentPart.getSlotRanges());
            }
        }

        Set<URI> failedSlaves = new HashSet<URI>(newPart.getFailedSlaveAddresses());
        failedSlaves.removeAll(currentPart.getFailedSlaveAddresses());
        for (URI uri : failedSlaves) {
            currentPart.addFailedSlaveAddress(uri);
            if (entry.slaveDown(uri.getHost(), uri.getPort(), FreezeReason.MANAGER)) {
                log.warn("slave: {} has down for slot ranges: {}", uri, currentPart.getSlotRanges());
            }
        }
    }

    private Set<URI> addRemoveSlaves(final MasterSlaveEntry entry, final ClusterPartition currentPart, final ClusterPartition newPart) {
        Set<URI> removedSlaves = new HashSet<URI>(currentPart.getSlaveAddresses());
        removedSlaves.removeAll(newPart.getSlaveAddresses());

        for (URI uri : removedSlaves) {
            currentPart.removeSlaveAddress(uri);

            if (entry.slaveDown(uri.getHost(), uri.getPort(), FreezeReason.MANAGER)) {
                log.info("slave {} removed for slot ranges: {}", uri, currentPart.getSlotRanges());
            }
        }

        Set<URI> addedSlaves = new HashSet<URI>(newPart.getSlaveAddresses());
        addedSlaves.removeAll(currentPart.getSlaveAddresses());
        for (final URI uri : addedSlaves) {
            Future<Void> future = entry.addSlave(uri.getHost(), uri.getPort());
            future.addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    if (!future.isSuccess()) {
                        log.error("Can't add slave: " + uri, future.cause());
                        return;
                    }

                    currentPart.addSlaveAddress(uri);
                    entry.slaveUp(uri.getHost(), uri.getPort(), FreezeReason.MANAGER);
                    log.info("slave: {} added for slot ranges: {}", uri, currentPart.getSlotRanges());
                }
            });
        }
        return addedSlaves;
    }

    private Collection<Integer> slots(Collection<ClusterPartition> partitions) {
        Set<Integer> result = new HashSet<Integer>(MAX_SLOT);
        for (ClusterPartition clusterPartition : partitions) {
            result.addAll(clusterPartition.getSlots());
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

    private Future<Void> checkMasterNodesChange(ClusterServersConfig cfg, Collection<ClusterPartition> newPartitions) {
        List<ClusterPartition> newMasters = new ArrayList<ClusterPartition>();
        for (final ClusterPartition newPart : newPartitions) {
            boolean masterFound = false;
            for (ClusterPartition currentPart : getLastPartitions()) {
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
                        log.info("changing master from {} to {} for {}",
                                currentPart.getMasterAddress(), newMasterPart.getMasterAddress(), slot);
                        URI newUri = newMasterPart.getMasterAddress();
                        URI oldUri = currentPart.getMasterAddress();
                        
                        changeMaster(slot, newUri.getHost(), newUri.getPort());
                        
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
            return newSucceededFuture(null);
        }
        
        final Promise<Void> result = newPromise();
        final AtomicInteger masters = new AtomicInteger(newMasters.size());
        final Queue<Future<Void>> futures = new ConcurrentLinkedQueue<Future<Void>>(); 
        for (ClusterPartition newPart : newMasters) {
            Future<Collection<Future<Void>>> future = addMasterEntry(newPart, cfg);
            future.addListener(new FutureListener<Collection<Future<Void>>>() {
                @Override
                public void operationComplete(Future<Collection<Future<Void>>> future) throws Exception {
                    if (future.isSuccess()) {
                        futures.addAll(future.getNow());
                    }
                    
                    if (masters.decrementAndGet() == 0) {
                        final AtomicInteger nodes = new AtomicInteger(futures.size());
                        for (Future<Void> nodeFuture : futures) {
                            nodeFuture.addListener(new FutureListener<Void>() {
                                @Override
                                public void operationComplete(Future<Void> future) throws Exception {
                                    if (nodes.decrementAndGet() == 0) {
                                        result.setSuccess(null);
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

    private void checkSlotsChange(ClusterServersConfig cfg, Collection<ClusterPartition> newPartitions, String nodes) {
        Collection<Integer> newPartitionsSlots = slots(newPartitions);
        if (newPartitionsSlots.size() == lastPartitions.size() && lastPartitions.size() == MAX_SLOT) {
            return;
        }

        Set<Integer> removedSlots = new HashSet<Integer>(lastPartitions.keySet());
        removedSlots.removeAll(newPartitionsSlots);
        lastPartitions.keySet().removeAll(removedSlots);
        if (!removedSlots.isEmpty()) {
            log.info("{} slots found to remove", removedSlots.size());
        }

        for (Integer slot : removedSlots) {
            MasterSlaveEntry entry = removeMaster(slot);
            entry.removeSlotRange(slot);
            if (entry.getSlotRanges().isEmpty()) {
                entry.shutdownMasterAsync();
                log.info("{} master and slaves for it removed", entry.getClient().getAddr());
            }
        }


        Set<Integer> addedSlots = new HashSet<Integer>(newPartitionsSlots);
        addedSlots.removeAll(lastPartitions.keySet());
        if (!addedSlots.isEmpty()) {
            log.info("{} slots found to add", addedSlots.size());
        }
        for (final Integer slot : addedSlots) {
            ClusterPartition partition = find(newPartitions, slot);
            for (MasterSlaveEntry entry : getEntrySet()) {
                if (entry.getClient().getAddr().equals(partition.getMasterAddr())) {
                    addEntry(slot, entry);
                    lastPartitions.put(slot, partition);
                    break;
                }
            }
        }
    }

    private void checkSlotsMigration(Collection<ClusterPartition> newPartitions, String nodes) {
        Set<ClusterPartition> currentPartitions = getLastPartitions();
        for (ClusterPartition currentPartition : currentPartitions) {
            for (ClusterPartition newPartition : newPartitions) {
                if (!currentPartition.getNodeId().equals(newPartition.getNodeId()) 
                        // skip master change case
                        || !currentPartition.getMasterAddr().equals(newPartition.getMasterAddr())) {
                    continue;
                }
                
                Set<Integer> addedSlots = new HashSet<Integer>(newPartition.getSlots());
                addedSlots.removeAll(currentPartition.getSlots());
                currentPartition.addSlots(addedSlots);
                
                MasterSlaveEntry entry = getEntry(currentPartition.getMasterAddr());

                for (Integer slot : addedSlots) {
                    entry.addSlotRange(slot);
                    addEntry(slot, entry);
                    lastPartitions.put(slot, currentPartition);
                }
                if (!addedSlots.isEmpty()) {
                    log.info("{} slots added to {}", addedSlots.size(), currentPartition.getMasterAddr());
                }

                Set<Integer> removedSlots = new HashSet<Integer>(currentPartition.getSlots());
                removedSlots.removeAll(newPartition.getSlots());
                for (Integer removeSlot : removedSlots) {
                    if (lastPartitions.remove(removeSlot, currentPartition)) {
                        entry.removeSlotRange(removeSlot);
                        removeMaster(removeSlot);
                    }
                }
                currentPartition.removeSlots(removedSlots);

                if (!removedSlots.isEmpty()) {
                    log.info("{} slots removed from {}", removedSlots.size(), currentPartition.getMasterAddr());
                }
                break;
            }
        }
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
            if (clusterNodeInfo.containsFlag(Flag.NOADDR)) {
                // skip it
                continue;
            }

            String id = clusterNodeInfo.getNodeId();
            if (clusterNodeInfo.containsFlag(Flag.SLAVE)) {
                id = clusterNodeInfo.getSlaveOf();
            }


            ClusterPartition partition = partitions.get(id);
            if (partition == null) {
                partition = new ClusterPartition(id);
                partitions.put(id, partition);
            }

            if (clusterNodeInfo.containsFlag(Flag.FAIL)) {
                if (clusterNodeInfo.containsFlag(Flag.SLAVE)) {
                    partition.addFailedSlaveAddress(clusterNodeInfo.getAddress());
                } else {
                    partition.setMasterFail(true);
                }
            }

            if (clusterNodeInfo.containsFlag(Flag.SLAVE)) {
                partition.addSlaveAddress(clusterNodeInfo.getAddress());
            } else {
                partition.addSlotRanges(clusterNodeInfo.getSlotRanges());
                partition.setMasterAddress(clusterNodeInfo.getAddress());
            }
        }
        return partitions.values();
    }

    @Override
    public void shutdown() {
        monitorFuture.cancel(true);
        super.shutdown();

        for (RedisConnection connection : nodeConnections.values()) {
            connection.getRedisClient().shutdown();
        }
    }

    private HashSet<ClusterPartition> getLastPartitions() {
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

