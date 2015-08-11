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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.Config;
import org.redisson.MasterSlaveServersConfig;
import org.redisson.SentinelServersConfig;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.core.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SentinelConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final List<RedisClient> sentinels = new ArrayList<RedisClient>();

    public SentinelConnectionManager(final SentinelServersConfig cfg, Config config) {
        init(config);

        final MasterSlaveServersConfig c = new MasterSlaveServersConfig();
        c.setRetryInterval(cfg.getRetryInterval());
        c.setRetryAttempts(cfg.getRetryAttempts());
        c.setTimeout(cfg.getTimeout());
        c.setPingTimeout(cfg.getPingTimeout());
        c.setLoadBalancer(cfg.getLoadBalancer());
        c.setPassword(cfg.getPassword());
        c.setDatabase(cfg.getDatabase());
        c.setMasterConnectionPoolSize(cfg.getMasterConnectionPoolSize());
        c.setSlaveConnectionPoolSize(cfg.getSlaveConnectionPoolSize());
        c.setSlaveSubscriptionConnectionPoolSize(cfg.getSlaveSubscriptionConnectionPoolSize());
        c.setSubscriptionsPerConnection(cfg.getSubscriptionsPerConnection());

        final Set<String> addedSlaves = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        for (URI addr : cfg.getSentinelAddresses()) {
            RedisClient client = createClient(addr.getHost(), addr.getPort(), c.getTimeout());
            try {
                RedisConnection connection = client.connect();

                // TODO async
                List<String> master = connection.sync(RedisCommands.SENTINEL_GET_MASTER_ADDR_BY_NAME, cfg.getMasterName());
                String masterHost = master.get(0) + ":" + master.get(1);
                c.setMasterAddress(masterHost);
                log.info("master: {} added", masterHost);
//            c.addSlaveAddress(masterHost);

                // TODO async
                List<Map<String, String>> slaves = connection.sync(RedisCommands.SENTINEL_SLAVES, cfg.getMasterName());
                for (Map<String, String> map : slaves) {
                    String ip = map.get("ip");
                    String port = map.get("port");
                    String flags = map.get("flags");

                    if (flags.contains("s_down") || flags.contains("disconnected")) {
                        log.info("slave: {}:{} is disconnected. skipped, params: {}", ip, port, map);
                        continue;
                    }

                    log.info("slave: {}:{} added, params: {}", ip, port, map);
                    c.addSlaveAddress(ip + ":" + port);
                    String host = ip + ":" + port;
                    addedSlaves.add(host);
                }
                break;
            } finally {
                client.shutdownAsync();
            }
        }

        init(c);

        monitorMasterChange(cfg, addedSlaves);
    }

    private void monitorMasterChange(final SentinelServersConfig cfg, final Set<String> addedSlaves) {
        final AtomicReference<String> master = new AtomicReference<String>();
        final Set<String> freezeSlaves = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        for (final URI addr : cfg.getSentinelAddresses()) {
            RedisClient client = createClient(addr.getHost(), addr.getPort());
            sentinels.add(client);

            RedisPubSubConnection pubsub = client.connectPubSub();
            pubsub.addListener(new RedisPubSubListener<String>() {

                @Override
                public void onMessage(String channel, String msg) {
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

                @Override
                public void onPatternMessage(String pattern, String channel, String message) {
                }

                @Override
                public boolean onStatus(PubSubType type, String channel) {
                    if (type == PubSubType.SUBSCRIBE) {
                        log.info("subscribed to channel: {} from Sentinel {}:{}", channel, addr.getHost(), addr.getPort());
                    }
                    return true;
                }
            });

            pubsub.subscribe(StringCodec.INSTANCE, "+switch-master", "+sdown", "-sdown", "+slave");
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
                slaveDown(-1, ip, Integer.valueOf(port));
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
                    changeMaster(-1, ip, Integer.valueOf(port));
                }
            }
        } else {
            log.warn("Invalid message: {} from Sentinel {}:{}", msg, addr.getHost(), addr.getPort());
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();

        for (RedisClient sentinel : sentinels) {
            sentinel.shutdown();
        }
    }
}

