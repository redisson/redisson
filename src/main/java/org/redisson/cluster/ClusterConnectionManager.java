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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.redisson.ClusterServersConfig;
import org.redisson.Config;
import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.cluster.ClusterNodeInfo.Flag;
import org.redisson.connection.CRC16;
import org.redisson.connection.MasterSlaveConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.ConnectionEntry.FreezeReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.ScheduledFuture;

public class ClusterConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Map<URI, RedisConnection> nodeConnections = new HashMap<URI, RedisConnection>();

    private final Map<ClusterSlotRange, ClusterPartition> lastPartitions = new HashMap<ClusterSlotRange, ClusterPartition>();

    private ScheduledFuture<?> monitorFuture;

    public ClusterConnectionManager(ClusterServersConfig cfg, Config config) {
        connectListener = new ClusterConnectionListener(cfg.isReadFromSlaves());
        init(config);

        this.config = create(cfg);
        init(this.config);

        for (URI addr : cfg.getNodeAddresses()) {
            RedisConnection connection = connect(cfg, addr);
            if (connection == null) {
                continue;
            }

            String nodesValue = connection.sync(RedisCommands.CLUSTER_NODES);

            Collection<ClusterPartition> partitions = parsePartitions(nodesValue);
            for (ClusterPartition partition : partitions) {
                addMasterEntry(partition, cfg);
            }

            break;
        }

        monitorClusterChange(cfg);
    }

    private RedisConnection connect(ClusterServersConfig cfg, URI addr) {
        RedisConnection connection = nodeConnections.get(addr);
        if (connection != null) {
            return connection;
        }
        RedisClient client = createClient(addr.getHost(), addr.getPort(), cfg.getTimeout());
        try {
            connection = client.connect();
            nodeConnections.put(addr, connection);
        } catch (RedisConnectionException e) {
            log.warn(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        if (connection != null && !connection.isActive()) {
            log.warn("connection for {} is not active!", connection.getRedisClient().getAddr());
            connection.closeAsync();
            connection = null;
        }
        if (connection == null) {
            nodeConnections.remove(addr);
        }
        return connection;
    }

    @Override
    protected void initEntry(MasterSlaveServersConfig config) {
    }

    private void addMasterEntry(ClusterPartition partition, ClusterServersConfig cfg) {
        if (partition.isMasterFail()) {
            log.warn("add master: {} for slot ranges: {} failed. Reason - server has FAIL flag", partition.getMasterAddress(), partition.getSlotRanges());
            return;
        }

        RedisConnection connection = connect(cfg, partition.getMasterAddress());
        if (connection == null) {
            return;
        }
        Map<String, String> params = connection.sync(RedisCommands.CLUSTER_INFO);
        if ("fail".equals(params.get("cluster_state"))) {
            log.warn("add master: {} for slot ranges: {} failed. Reason - cluster_state:fail", partition.getMasterAddress(), partition.getSlotRanges());
            return;
        }

        MasterSlaveServersConfig config = create(cfg);
        log.info("master: {} added for slot ranges: {}", partition.getMasterAddress(), partition.getSlotRanges());
        config.setMasterAddress(partition.getMasterAddress());
        config.setSlaveAddresses(partition.getSlaveAddresses());

        log.info("slaves: {} added for slot ranges: {}", partition.getSlaveAddresses(), partition.getSlotRanges());

        MasterSlaveEntry entry = new MasterSlaveEntry(partition.getSlotRanges(), this, config, connectListener);
        entry.setupMasterEntry(config.getMasterAddress().getHost(), config.getMasterAddress().getPort());
        for (ClusterSlotRange slotRange : partition.getSlotRanges()) {
            addEntry(slotRange, entry);
            lastPartitions.put(slotRange, partition);
        }
    }

    private void monitorClusterChange(final ClusterServersConfig cfg) {
        monitorFuture = GlobalEventExecutor.INSTANCE.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    for (ClusterPartition partition : lastPartitions.values()) {
                        for (URI uri : partition.getAllAddresses()) {
                            RedisConnection connection = connect(cfg, uri);
                            if (connection == null) {
                                continue;
                            }

                            updateClusterState(cfg, connection);
                            return;
                        }
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }, cfg.getScanInterval(), cfg.getScanInterval(), TimeUnit.MILLISECONDS);
    }

    private void updateClusterState(ClusterServersConfig cfg, RedisConnection connection) {
        String nodesValue = connection.sync(RedisCommands.CLUSTER_NODES);
        log.debug("cluster nodes state from {}:\n{}", connection.getRedisClient().getAddr(), nodesValue);

        Collection<ClusterPartition> newPartitions = parsePartitions(nodesValue);
        checkMasterNodesChange(newPartitions);
        checkSlaveNodesChange(newPartitions);
        checkSlotsChange(cfg, newPartitions);
    }

    private void checkSlaveNodesChange(Collection<ClusterPartition> newPartitions) {
        for (ClusterPartition newPart : newPartitions) {
            for (ClusterPartition currentPart : lastPartitions.values()) {
                if (!newPart.getMasterAddress().equals(currentPart.getMasterAddress())) {
                    continue;
                }
                MasterSlaveEntry entry = getEntry(currentPart.getMasterAddr());

                Set<URI> removedSlaves = new HashSet<URI>(currentPart.getSlaveAddresses());
                removedSlaves.removeAll(newPart.getSlaveAddresses());

                for (URI uri : removedSlaves) {
                    currentPart.removeSlaveAddress(uri);

                    slaveDown(entry, uri.getHost(), uri.getPort(), FreezeReason.MANAGER);
                    log.info("slave {} removed for slot ranges: {}", uri, currentPart.getSlotRanges());
                }

                Set<URI> addedSlaves = new HashSet<URI>(newPart.getSlaveAddresses());
                addedSlaves.removeAll(currentPart.getSlaveAddresses());
                for (URI uri : addedSlaves) {
                    currentPart.addSlaveAddress(uri);

                    entry.addSlave(uri.getHost(), uri.getPort());
                    entry.slaveUp(uri.getHost(), uri.getPort(), FreezeReason.MANAGER);
                    log.info("slave {} added for slot ranges: {}", uri, currentPart.getSlotRanges());
                }

                break;
            }
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
        for (ClusterSlotRange slot : addedSlots) {
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
                addMasterEntry(partition, cfg);
            }
        }
    }

    private void checkSlotsMigration(Collection<ClusterPartition> newPartitions) {
        List<ClusterPartition> currentPartitions = new ArrayList<ClusterPartition>(lastPartitions.values());
        for (ClusterPartition currentPartition : currentPartitions) {
            for (ClusterPartition newPartition : newPartitions) {
                if (!currentPartition.getNodeId().equals(newPartition.getNodeId())) {
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
                partition.setMasterFail(true);
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

    private MasterSlaveServersConfig create(ClusterServersConfig cfg) {
        MasterSlaveServersConfig c = new MasterSlaveServersConfig();
        c.setRetryInterval(cfg.getRetryInterval());
        c.setRetryAttempts(cfg.getRetryAttempts());
        c.setTimeout(cfg.getTimeout());
        c.setPingTimeout(cfg.getPingTimeout());
        c.setLoadBalancer(cfg.getLoadBalancer());
        c.setPassword(cfg.getPassword());
        c.setDatabase(cfg.getDatabase());
        c.setClientName(cfg.getClientName());
        c.setRefreshConnectionAfterFails(cfg.getRefreshConnectionAfterFails());
        c.setMasterConnectionPoolSize(cfg.getMasterConnectionPoolSize());
        c.setSlaveConnectionPoolSize(cfg.getSlaveConnectionPoolSize());
        c.setSlaveSubscriptionConnectionPoolSize(cfg.getSlaveSubscriptionConnectionPoolSize());
        c.setSubscriptionsPerConnection(cfg.getSubscriptionsPerConnection());
        return c;
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

