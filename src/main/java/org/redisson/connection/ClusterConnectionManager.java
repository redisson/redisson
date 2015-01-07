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
package org.redisson.connection;

import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.ScheduledFuture;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.redisson.ClusterServersConfig;
import org.redisson.Config;
import org.redisson.MasterSlaveServersConfig;
import org.redisson.connection.ClusterNodeInfo.Flag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnectionException;

public class ClusterConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final List<RedisClient> nodeClients = new ArrayList<RedisClient>();

    private Collection<ClusterPartition> lastPartitions;

    private ScheduledFuture<?> monitorFuture;

    public ClusterConnectionManager(ClusterServersConfig cfg, Config config) {
        init(config);

        for (URI addr : cfg.getNodeAddresses()) {
            RedisClient client = new RedisClient(group, addr.getHost(), addr.getPort(), cfg.getTimeout());
            try {
                RedisAsyncConnection<String, String> connection = client.connectAsync();
                String nodesValue = connection.clusterNodes().awaitUninterruptibly().getNow();

                Collection<ClusterPartition> partitions = extractPartitions(nodesValue);
                for (ClusterPartition partition : partitions) {
                    if (partition.isMasterFail()) {
                        continue;
                    }

                    MasterSlaveServersConfig c = create(cfg);
                    log.info("master: {}", partition.getMasterAddress());
                    c.setMasterAddress(partition.getMasterAddress());

                    SingleEntry entry = new SingleEntry(codec, group, c);
                    entries.put(partition.getEndSlot(), entry);
                }

                lastPartitions = partitions;
                break;

            } catch (RedisConnectionException e) {
                log.warn(e.getMessage(), e);
            } finally {
                client.shutdown();
            }
        }

        this.config = create(cfg);

        monitorClusterChange(cfg);
    }

    private void monitorClusterChange(final ClusterServersConfig cfg) {
        monitorFuture = GlobalEventExecutor.INSTANCE.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    for (URI addr : cfg.getNodeAddresses()) {
                        final RedisClient client = new RedisClient(group, addr.getHost(), addr.getPort(), cfg.getTimeout());
                        try {
                            RedisAsyncConnection<String, String> connection = client.connectAsync();
                            String nodesValue = connection.clusterNodes().awaitUninterruptibly().getNow();

                            Collection<ClusterPartition> partitions = extractPartitions(nodesValue);
                            for (ClusterPartition newPart : partitions) {
                                boolean found = false;
                                for (ClusterPartition part : lastPartitions) {
                                    if (newPart.getMasterAddress().equals(part.getMasterAddress())) {
                                        log.debug("found endslot {} for {} fail {}", newPart.getEndSlot(), newPart.getMasterAddress(), newPart.isMasterFail());
                                        found = true;
                                        if (newPart.isMasterFail() && !part.isMasterFail()) {
                                            for (ClusterPartition newMasterPart : partitions) {
                                                if (!newMasterPart.getMasterAddress().equals(part.getMasterAddress())
                                                        && newMasterPart.getEndSlot() == part.getEndSlot()) {

                                                    log.debug("changing master from {} to {} for {}",
                                                            part.getMasterAddress(), newMasterPart.getMasterAddress(), newMasterPart.getEndSlot());
                                                    URI newUri = toURI(newMasterPart.getMasterAddress());
                                                    URI oldUri = toURI(part.getMasterAddress());

                                                    changeMaster(newMasterPart.getEndSlot(), newUri.getHost(), newUri.getPort());
                                                    slaveDown(newMasterPart.getEndSlot(), oldUri.getHost(), oldUri.getPort());
                                                    part.setMasterFail(true);

                                                    monitorFuture.cancel(true);
                                                }
                                            }

                                        }
                                        break;
                                    }
                                }
                                if (!found) {
                                    // TODO slot changed
                                }
                            }

                            break;

                        } catch (RedisConnectionException e) {
                            // skip it
                        } finally {
                            client.shutdownAsync();
                        }
                    }

                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }

            }
        }, cfg.getScanInterval(), cfg.getScanInterval(), TimeUnit.MILLISECONDS);
    }

    private Collection<ClusterPartition> extractPartitions(String nodesValue) {
        Map<String, ClusterPartition> partitions = new HashMap<String, ClusterPartition>();
        List<ClusterNodeInfo> nodes = parse(nodesValue);
        for (ClusterNodeInfo clusterNodeInfo : nodes) {
            String id = clusterNodeInfo.getNodeId();
            if (clusterNodeInfo.getFlags().contains(Flag.SLAVE)) {
                id = clusterNodeInfo.getSlaveOf();
            }
            ClusterPartition partition = partitions.get(id);
            if (partition == null) {
                partition = new ClusterPartition();
                partitions.put(id, partition);
            }

            if (clusterNodeInfo.getFlags().contains(Flag.FAIL)) {
                partition.setMasterFail(true);
            }

            if (clusterNodeInfo.getFlags().contains(Flag.SLAVE)) {
                partition.addSlaveAddress(clusterNodeInfo.getAddress());
            } else {
                partition.setEndSlot(clusterNodeInfo.getEndSlot());
                partition.setMasterAddress(clusterNodeInfo.getAddress());
            }
        }
        return partitions.values();
    }

    private MasterSlaveServersConfig create(ClusterServersConfig cfg) {
        MasterSlaveServersConfig c = new MasterSlaveServersConfig();
        c.setLoadBalancer(cfg.getLoadBalancer());
        c.setPassword(cfg.getPassword());
        c.setDatabase(cfg.getDatabase());
        c.setMasterConnectionPoolSize(cfg.getMasterConnectionPoolSize());
        c.setSlaveConnectionPoolSize(cfg.getSlaveConnectionPoolSize());
        c.setSlaveSubscriptionConnectionPoolSize(cfg.getSlaveSubscriptionConnectionPoolSize());
        c.setSubscriptionsPerConnection(cfg.getSubscriptionsPerConnection());
        return c;
    }

    public static void main(String[] args) {
        String s = "FAIL?".replaceAll("\\?", "");
        System.out.println(s);
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
                String slots = params[8];
                String[] parts = slots.split("-");
                node.setStartSlot(Integer.valueOf(parts[0]));
                node.setEndSlot(Integer.valueOf(parts[1]));
            }

            nodes.add(node);
        }
        return nodes;
    }

    @Override
    public void shutdown() {
        monitorFuture.cancel(true);

        for (RedisClient sentinel : nodeClients) {
            sentinel.shutdown();
        }

        super.shutdown();
    }
}

