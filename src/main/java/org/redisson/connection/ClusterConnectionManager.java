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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.ClusterServersConfig;
import org.redisson.Config;
import org.redisson.MasterSlaveServersConfig;
import org.redisson.SentinelServersConfig;
import org.redisson.connection.ClusterNodeInfo.Flag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

public class ClusterConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final List<RedisClient> nodeClients = new ArrayList<RedisClient>();

    public ClusterConnectionManager(ClusterServersConfig cfg, Config config) {
        init(config);

        Map<String, ClusterPartition> partitions = new HashMap<String, ClusterPartition>();

        for (URI addr : cfg.getNodeAddresses()) {
            RedisClient client = new RedisClient(group, addr.getHost(), addr.getPort(), cfg.getTimeout());
            RedisAsyncConnection<String, String> connection = client.connectAsync();
            String nodesValue = connection.clusterNodes().awaitUninterruptibly().getNow();
            System.out.println(nodesValue);

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

            for (ClusterPartition partition : partitions.values()) {
                if (partition.isMasterFail()) {
                    continue;
                }

                MasterSlaveServersConfig c = create(cfg);
                log.info("master: {}", partition.getMasterAddress());
                c.setMasterAddress(partition.getMasterAddress());
                for (String slaveAddress : partition.getSlaveAddresses()) {
                    log.info("slave: {}", slaveAddress);
                    c.addSlaveAddress(slaveAddress);
                }

                MasterSlaveEntry entry = new MasterSlaveEntry(codec, group, c);
                entries.put(partition.getEndSlot(), entry);
            }

            client.shutdown();
            break;
        }

        this.config = create(cfg);
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
                node.addFlag(ClusterNodeInfo.Flag.valueOf(flag.toUpperCase()));
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

    private void init(ClusterServersConfig cfg, Config config) {
//        monitorMasterChange(cfg);
    }

    private void monitorMasterChange(final SentinelServersConfig cfg) {
        final AtomicReference<String> master = new AtomicReference<String>();
        final Set<String> freezeSlaves = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        final Set<String> addedSlaves = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        for (final URI addr : cfg.getSentinelAddresses()) {
            RedisClient client = new RedisClient(group, addr.getHost(), addr.getPort(), cfg.getTimeout());
            nodeClients.add(client);

            RedisPubSubConnection<String, String> pubsub = client.connectPubSub();
            pubsub.addListener(new RedisPubSubAdapter<String>() {
                @Override
                public void subscribed(String channel, long count) {
                    log.info("subscribed to channel: {} from Sentinel {}:{}", channel, addr.getHost(), addr.getPort());
                }

                @Override
                public void message(String channel, String msg) {
                    if ("+slave".equals(channel)) {
                        onSlaveAdded(addedSlaves, addr, msg);
                    }
                    if ("+sdown".equals(channel)) {
                        onSlaveDown(freezeSlaves, addr, msg);
                    }
                    if ("-sdown".equals(channel)) {
                        onSlaveUp(freezeSlaves, addr, msg);
                    }
                    if ("+switch-master".equals(channel)) {
                        onMasterChange(cfg, master, addr, msg);
                    }
                }

            });
            pubsub.subscribe("+switch-master", "+sdown", "-sdown", "+slave");
        }
    }

    protected void onSlaveAdded(Set<String> addedSlaves, URI addr, String msg) {
        String[] parts = msg.split(" ");

        if (parts.length > 4
                 && "slave".equals(parts[0])) {
            String ip = parts[2];
            String port = parts[3];

            String slaveAddr = ip + ":" + port;

            // to avoid addition twice
            if (addedSlaves.add(slaveAddr)) {
                log.debug("Slave has been added - {}", slaveAddr);
                addSlave(ip, Integer.valueOf(port));
            }
        } else {
            log.warn("Invalid message: {} from Sentinel {}:{}", msg, addr.getHost(), addr.getPort());
        }
    }

    private void onSlaveDown(final Set<String> freezeSlaves, final URI addr, String msg) {
        String[] parts = msg.split(" ");

        if (parts.length > 4
                 && "slave".equals(parts[0])) {
            String ip = parts[2];
            String port = parts[3];

            String slaveAddr = ip + ":" + port;

            // to avoid freeze twice
            if (freezeSlaves.add(slaveAddr)) {
                log.debug("Slave has down - {}", slaveAddr);
                slaveDown(ip, Integer.valueOf(port));
            }
        } else {
            log.warn("Invalid message: {} from Sentinel {}:{}", msg, addr.getHost(), addr.getPort());
        }
    }

    protected void onSlaveUp(Set<String> freezeSlaves, URI addr, String msg) {
        String[] parts = msg.split(" ");

        if (parts.length > 4
                 && "slave".equals(parts[0])) {
            String ip = parts[2];
            String port = parts[3];

            String slaveAddr = ip + ":" + port;
            if (freezeSlaves.remove(slaveAddr)) {
                log.debug("Slave has up - {}", slaveAddr);
                slaveUp(ip, Integer.valueOf(port));
            }
        } else {
            log.warn("Invalid message: {} from Sentinel {}:{}", msg, addr.getHost(), addr.getPort());
        }
    }

    private void onMasterChange(final SentinelServersConfig cfg,
            final AtomicReference<String> master, final URI addr, String msg) {
        String[] parts = msg.split(" ");

        if (parts.length > 3) {
            if (cfg.getMasterName().equals(parts[0])) {
                String ip = parts[3];
                String port = parts[4];

                String current = master.get();
                String newMaster = ip + ":" + port;
                if (!newMaster.equals(current)
                        && master.compareAndSet(current, newMaster)) {
                    log.debug("changing master from {} to {}", current, newMaster);
                    changeMaster(ip, Integer.valueOf(port));
                }
            }
        } else {
            log.warn("Invalid message: {} from Sentinel {}:{}", msg, addr.getHost(), addr.getPort());
        }
    }

    @Override
    public void shutdown() {
        for (RedisClient sentinel : nodeClients) {
            sentinel.shutdown();
        }

        super.shutdown();
    }
}

