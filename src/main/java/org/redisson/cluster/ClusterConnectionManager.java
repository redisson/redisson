/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
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

public class ClusterConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Map<URI, RedisConnection> nodeConnections = new HashMap<URI, RedisConnection>();

    private final Map<ClusterSlotRange, ClusterPartition> lastPartitions = new HashMap<ClusterSlotRange, ClusterPartition>();

    private ScheduledFuture<?> monitorFuture;

    public ClusterConnectionManager(ClusterServersConfig cfg, Config config) {
        super(config);
        connectListener = new ClusterConnectionListener(cfg.getReadMode() == ReadMode.SLAVE);

        this.config = create(cfg);
        init(this.config);

        Exception lastException = null;
        for (URI addr : cfg.getNodeAddresses()) {
            Future<RedisConnection> connectionFuture = connect(cfg, addr);
            try {
                RedisConnection connection = connectionFuture.syncUninterruptibly().getNow();
                String nodesValue = connection.sync(RedisCommands.CLUSTER_NODES);

                Collection<ClusterPartition> partitions = parsePartitions(nodesValue);
                List<Future<Collection<Future<Void>>>> futures = new ArrayList<Future<Collection<Future<Void>>>>();
                for (ClusterPartition partition : partitions) {
                    Future<Collection<Future<Void>>> masterFuture = addMasterEntry(partition, cfg);
                    futures.add(masterFuture);
                }

                for (Future<Collection<Future<Void>>> masterFuture : futures) {
                    masterFuture.syncUninterruptibly();
                    for (Future<Void> future : masterFuture.getNow()) {
                        future.syncUninterruptibly();
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
            throw new RedisConnectionException("Can't connect to servers!", lastException);
        }

        scheduleClusterChangeCheck(cfg);
    }

    private Future<RedisConnection> connect(ClusterServersConfig cfg, final URI addr) {
        RedisConnection connection = nodeConnections.get(addr);
        if (connection != null) {
            return newSucceededFuture(connection);
        }

        RedisClient client = createClient(addr.getHost(), addr.getPort(), cfg.getConnectTimeout());
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
                    result.setFailure(future.cause());
                    return;
                }

                final RedisConnection connection = future.getNow();
                Future<Map<String, String>> clusterFuture = connection.async(RedisCommands.CLUSTER_INFO);
                clusterFuture.addListener(new FutureListener<Map<String, String>>() {

                    @Override
                    public void operationComplete(Future<Map<String, String>> future) throws Exception {
                        if (!future.isSuccess()) {
                            log.error("Can't execute CLUSTER_INFO with " + connection.getRedisClient().getAddr(), future.cause());
                            result.setFailure(future.cause());
                            return;
                        }

                        Map<String, String> params = future.getNow();
                        if ("fail".equals(params.get("cluster_state"))) {
                            RedisException e = new RedisException("Failed to add master: " +
                                    partition.getMasterAddress() + " for slot ranges: " +
                                    partition.getSlotRanges() + ". Reason - cluster_state:fail");
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

                            if (!partition.getSlaveAddresses().isEmpty()) {
                                List<Future<Void>> fs = e.initSlaveBalancer(partition.getFailedSlaveAddresses());
                                futures.addAll(fs);
                                log.info("slaves: {} added for slot ranges: {}", partition.getSlaveAddresses(), partition.getSlotRanges());
                                if (!partition.getFailedSlaveAddresses().isEmpty()) {
                                    log.warn("slaves: {} is down for slot ranges: {}", partition.getFailedSlaveAddresses(), partition.getSlotRanges());
                                }
                            }
                        }

                        Future<Void> f = e.setupMasterEntry(config.getMasterAddress().getHost(), config.getMasterAddress().getPort());
                        f.addListener(new FutureListener<Void>() {
                            @Override
                            public void operationComplete(Future<Void> future) throws Exception {
                                if (!future.isSuccess()) {
                                    return;
                                }
                                for (ClusterSlotRange slotRange : partition.getSlotRanges()) {
                                    addEntry(slotRange, e);
                                    lastPartitions.put(slotRange, partition);
                                }

                                log.info("master: {} added for slot ranges: {}", partition.getMasterAddress(), partition.getSlotRanges());
                            }
                        });
                        futures.add(f);
                        result.setSuccess(futures);
                    }
                });

            }
        });

        return result;
    }

    private void scheduleClusterChangeCheck(final ClusterServersConfig cfg) {
        monitorFuture = GlobalEventExecutor.INSTANCE.schedule(new Runnable() {
            @Override
            public void run() {
                List<URI> nodes = new ArrayList<URI>();
                List<URI> slaves = new ArrayList<URI>();
                AtomicReference<Throwable> lastException = new AtomicReference<Throwable>();
                for (ClusterPartition partition : lastPartitions.values()) {
                    if (!partition.isMasterFail()) {
                        nodes.add(partition.getMasterAddress());
                    }

                    Set<URI> partitionSlaves = new HashSet<URI>(partition.getSlaveAddresses());
                    partitionSlaves.removeAll(partition.getFailedSlaveAddresses());
                    slaves.addAll(partitionSlaves);
                }
                // master nodes first
                nodes.addAll(slaves);

                checkClusterState(cfg, nodes.iterator(), lastException);
            }

        }, cfg.getScanInterval(), TimeUnit.MILLISECONDS);
    }

    private void checkClusterState(final ClusterServersConfig cfg, final Iterator<URI> iterator, final AtomicReference<Throwable> lastException) {
        if (!iterator.hasNext()) {
            log.error("Can't update cluster state", lastException.get());
            scheduleClusterChangeCheck(cfg);
            return;
        }
        URI uri = iterator.next();
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
                updateClusterState(cfg, connection);
            }
        });
    }

    private void updateClusterState(final ClusterServersConfig cfg, final RedisConnection connection) {
        Future<String> future = connection.async(RedisCommands.CLUSTER_NODES);
        future.addListener(new FutureListener<String>() {
            @Override
            public void operationComplete(Future<String> future) throws Exception {
                if (!future.isSuccess()) {
                    log.error("Can't execute CLUSTER_NODES with " + connection.getRedisClient().getAddr(), future.cause());
                    scheduleClusterChangeCheck(cfg);
                    return;
                }

                String nodesValue = future.getNow();
                log.debug("cluster nodes state from {}:\n{}", connection.getRedisClient().getAddr(), nodesValue);

                Collection<ClusterPartition> newPartitions = parsePartitions(nodesValue);
                checkMasterNodesChange(newPartitions);
                checkSlaveNodesChange(newPartitions);
                checkSlotsChange(cfg, newPartitions);
                scheduleClusterChangeCheck(cfg);
            }
        });
    }

    private void checkSlaveNodesChange(Collection<ClusterPartition> newPartitions) {
        for (ClusterPartition newPart : newPartitions) {
            for (ClusterPartition currentPart : lastPartitions.values()) {
                if (!newPart.getMasterAddress().equals(currentPart.getMasterAddress())) {
                    continue;
                }

                MasterSlaveEntry entry = getEntry(currentPart.getMasterAddr());
                // should be invoked first in order to removed stale failedSlaveAddresses
                addRemoveSlaves(entry, currentPart, newPart);
                // Does some slaves change failed state to alive?
                upDownSlaves(entry, currentPart, newPart);

                break;
            }
        }
    }

    private void upDownSlaves(final MasterSlaveEntry entry, final ClusterPartition currentPart, final ClusterPartition newPart) {
        Set<URI> aliveSlaves = new HashSet<URI>(currentPart.getFailedSlaveAddresses());
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

    private void addRemoveSlaves(final MasterSlaveEntry entry, final ClusterPartition currentPart, final ClusterPartition newPart) {
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
                    log.info("slave {} added for slot ranges: {}", uri, currentPart.getSlotRanges());
                }
            });
        }
    }

    private Collection<ClusterSlotRange> slots(Collection<ClusterPartition> partitions) {
        List<ClusterSlotRange> result = new ArrayList<ClusterSlotRange>();
        for (ClusterPartition clusterPartition : partitions) {
            result.addAll(clusterPartition.getSlotRanges());
        }
        return result;
    }

    private ClusterPartition find(Collection<ClusterPartition> partitions, ClusterSlotRange slotRange) {
        for (ClusterPartition clusterPartition : partitions) {
            if (clusterPartition.getSlotRanges().contains(slotRange)) {
                return clusterPartition;
            }
        }
        return null;
    }

    private void checkMasterNodesChange(Collection<ClusterPartition> newPartitions) {
        for (ClusterPartition newPart : newPartitions) {
            for (ClusterPartition currentPart : lastPartitions.values()) {
                if (!newPart.getMasterAddress().equals(currentPart.getMasterAddress())) {
                    continue;
                }
                // current master marked as failed
                if (newPart.isMasterFail()) {
                    for (ClusterSlotRange currentSlotRange : currentPart.getSlotRanges()) {
                        ClusterPartition newMasterPart = find(newPartitions, currentSlotRange);
                        // does partition has a new master?
                        if (!newMasterPart.getMasterAddress().equals(currentPart.getMasterAddress())) {
                            log.info("changing master from {} to {} for {}",
                                    currentPart.getMasterAddress(), newMasterPart.getMasterAddress(), currentSlotRange);
                            URI newUri = newMasterPart.getMasterAddress();
                            URI oldUri = currentPart.getMasterAddress();

                            changeMaster(currentSlotRange, newUri.getHost(), newUri.getPort());
                            slaveDown(currentSlotRange, oldUri.getHost(), oldUri.getPort(), FreezeReason.MANAGER);

                            currentPart.setMasterAddress(newMasterPart.getMasterAddress());
                        }
                    }
                }
                break;
            }
        }
    }

    private void checkSlotsChange(ClusterServersConfig cfg, Collection<ClusterPartition> newPartitions) {
        checkSlotsMigration(newPartitions);

        Collection<ClusterSlotRange> newPartitionsSlots = slots(newPartitions);
        Set<ClusterSlotRange> removedSlots = new HashSet<ClusterSlotRange>(lastPartitions.keySet());
        removedSlots.removeAll(newPartitionsSlots);
        lastPartitions.keySet().removeAll(removedSlots);
        if (!removedSlots.isEmpty()) {
            log.info("{} slot ranges found to remove", removedSlots);
        }

        for (ClusterSlotRange slot : removedSlots) {
            MasterSlaveEntry entry = removeMaster(slot);
            entry.removeSlotRange(slot);
            if (entry.getSlotRanges().isEmpty()) {
                entry.shutdownMasterAsync();
                log.info("{} master and slaves for it removed", entry.getClient().getAddr());
            }
        }


        Set<ClusterSlotRange> addedSlots = new HashSet<ClusterSlotRange>(newPartitionsSlots);
        addedSlots.removeAll(lastPartitions.keySet());
        if (!addedSlots.isEmpty()) {
            log.info("{} slots found to add", addedSlots);
        }
        for (final ClusterSlotRange slot : addedSlots) {
            ClusterPartition partition = find(newPartitions, slot);
            boolean masterFound = false;
            for (MasterSlaveEntry entry : getEntries().values()) {
                if (entry.getClient().getAddr().equals(partition.getMasterAddr())) {
                    addEntry(slot, entry);
                    lastPartitions.put(slot, partition);
                    masterFound = true;
                    break;
                }
            }
            if (!masterFound) {
                Future<Collection<Future<Void>>> future = addMasterEntry(partition, cfg);
                future.addListener(new FutureListener<Collection<Future<Void>>>() {
                    @Override
                    public void operationComplete(Future<Collection<Future<Void>>> future) throws Exception {
                        if (!future.isSuccess()) {
                            log.error("New cluster slot range " + slot + " without master node detected", future.cause());
                        }
                    }
                });
            }
        }
    }

    private void checkSlotsMigration(Collection<ClusterPartition> newPartitions) {
        List<ClusterPartition> currentPartitions = new ArrayList<ClusterPartition>(lastPartitions.values());
        for (ClusterPartition currentPartition : currentPartitions) {
            for (ClusterPartition newPartition : newPartitions) {
                if (!currentPartition.getNodeId().equals(newPartition.getNodeId()) 
                        // skip master change case
                        || !currentPartition.getMasterAddr().equals(newPartition.getMasterAddr())) {
                    continue;
                }
                
                Set<ClusterSlotRange> addedSlots = new HashSet<ClusterSlotRange>(newPartition.getSlotRanges());
                addedSlots.removeAll(currentPartition.getSlotRanges());
                MasterSlaveEntry entry = getEntry(currentPartition.getSlotRanges().iterator().next());
                currentPartition.addSlotRanges(addedSlots);
                for (ClusterSlotRange slot : addedSlots) {
                    entry.addSlotRange(slot);
                    addEntry(slot, entry);
                    log.info("{} slot added for {}", slot, entry.getClient().getAddr());
                    lastPartitions.put(slot, currentPartition);
                }

                Set<ClusterSlotRange> removedSlots = new HashSet<ClusterSlotRange>(currentPartition.getSlotRanges());
                removedSlots.removeAll(newPartition.getSlotRanges());
                lastPartitions.keySet().removeAll(removedSlots);
                currentPartition.removeSlotRanges(removedSlots);

                for (ClusterSlotRange slot : removedSlots) {
                    log.info("{} slot removed for {}", slot, entry.getClient().getAddr());
                    entry.removeSlotRange(slot);
                    removeMaster(slot);
                }
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

    private Collection<ClusterPartition> parsePartitions(String nodesValue) {
        Map<String, ClusterPartition> partitions = new HashMap<String, ClusterPartition>();
        List<ClusterNodeInfo> nodes = parse(nodesValue);
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

    private List<ClusterNodeInfo> parse(String nodesResponse) {
        List<ClusterNodeInfo> nodes = new ArrayList<ClusterNodeInfo>();
        for (String nodeInfo : nodesResponse.split("\n")) {
            ClusterNodeInfo node = new ClusterNodeInfo();
            String[] params = nodeInfo.split(" ");

            String nodeId = params[0];
            node.setNodeId(nodeId);

            String addr = params[1];
            node.setAddress(addr);

            String flags = params[2];
            for (String flag : flags.split(",")) {
                String flagValue = flag.toUpperCase().replaceAll("\\?", "");
                node.addFlag(ClusterNodeInfo.Flag.valueOf(flagValue));
            }

            String slaveOf = params[3];
            if (!"-".equals(slaveOf)) {
                node.setSlaveOf(slaveOf);
            }

            if (params.length > 8) {
                for (int i = 0; i < params.length - 8; i++) {
                    String slots = params[i + 8];
                    String[] parts = slots.split("-");

                    if(parts.length == 1) {
                        node.addSlotRange(new ClusterSlotRange(Integer.valueOf(parts[0]), Integer.valueOf(parts[0])));
                    } else if(parts.length == 2) {
                        node.addSlotRange(new ClusterSlotRange(Integer.valueOf(parts[0]), Integer.valueOf(parts[1])));
                    }
                }
            }
            nodes.add(node);
        }
        return nodes;
    }

    @Override
    public void shutdown() {
        monitorFuture.cancel(true);
        super.shutdown();

        for (RedisConnection connection : nodeConnections.values()) {
            connection.getRedisClient().shutdown();
        }
    }
}

