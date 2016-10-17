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
package org.redisson.connection;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.BaseMasterSlaveServersConfig;
import org.redisson.Config;
import org.redisson.MasterSlaveServersConfig;
import org.redisson.ReadMode;
import org.redisson.SentinelServersConfig;
import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.misc.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.PlatformDependent;

public class SentinelConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ConcurrentMap<String, RedisClient> sentinels = PlatformDependent.newConcurrentHashMap();
    private final AtomicReference<String> currentMaster = new AtomicReference<String>();
    private final ConcurrentMap<String, Boolean> slaves = PlatformDependent.newConcurrentHashMap();

    private final Set<URI> disconnectedSlaves = new HashSet<URI>();

    public SentinelConnectionManager(SentinelServersConfig cfg, Config config) {
        super(config);

        final MasterSlaveServersConfig c = create(cfg);

        for (URI addr : cfg.getSentinelAddresses()) {
            RedisClient client = createClient(addr.getHost(), addr.getPort(), c.getConnectTimeout(), c.getRetryInterval() * c.getRetryAttempts());
            try {
                RedisConnection connection = client.connect();
                if (!connection.isActive()) {
                    continue;
                }

                // TODO async
                List<String> master = connection.sync(RedisCommands.SENTINEL_GET_MASTER_ADDR_BY_NAME, cfg.getMasterName());
                String masterHost = master.get(0) + ":" + master.get(1);
                c.setMasterAddress(masterHost);
                currentMaster.set(masterHost);
                log.info("master: {} added", masterHost);
                slaves.put(masterHost, true);

                List<Map<String, String>> sentinelSlaves = connection.sync(StringCodec.INSTANCE, RedisCommands.SENTINEL_SLAVES, cfg.getMasterName());
                for (Map<String, String> map : sentinelSlaves) {
                    if (map.isEmpty()) {
                        continue;
                    }

                    String ip = map.get("ip");
                    String port = map.get("port");
                    String flags = map.get("flags");

                    String host = ip + ":" + port;

                    c.addSlaveAddress(host);
                    slaves.put(host, true);
                    log.debug("slave {} state: {}", host, map);
                    log.info("slave: {} added", host);

                    if (flags.contains("s_down") || flags.contains("disconnected")) {
                        URI url = URIBuilder.create(host);
                        disconnectedSlaves.add(url);
                        log.warn("slave: {} is down", host);
                    }
                }
                break;
            } catch (RedisConnectionException e) {
                log.warn("Can't connect to sentinel server. {}", e.getMessage());
            } finally {
                client.shutdownAsync();
            }
        }

        if (currentMaster.get() == null) {
            throw new RedisConnectionException("Can't connect to servers!");
        }
        init(c);

        List<Future<RedisPubSubConnection>> connectionFutures = new ArrayList<Future<RedisPubSubConnection>>(cfg.getSentinelAddresses().size());
        for (URI addr : cfg.getSentinelAddresses()) {
            Future<RedisPubSubConnection> future = registerSentinel(cfg, addr, c);
            connectionFutures.add(future);
        }

        for (Future<RedisPubSubConnection> future : connectionFutures) {
            future.awaitUninterruptibly();
        }
    }

    @Override
    protected MasterSlaveEntry createMasterSlaveEntry(MasterSlaveServersConfig config,
            HashSet<ClusterSlotRange> slots) {
        MasterSlaveEntry entry = new MasterSlaveEntry(slots, this, config);
        List<Future<Void>> fs = entry.initSlaveBalancer(disconnectedSlaves);
        for (Future<Void> future : fs) {
            future.syncUninterruptibly();
        }
        Future<Void> f = entry.setupMasterEntry(config.getMasterAddress().getHost(), config.getMasterAddress().getPort());
        f.syncUninterruptibly();
        return entry;
    }

    private Future<RedisPubSubConnection> registerSentinel(final SentinelServersConfig cfg, final URI addr, final MasterSlaveServersConfig c) {
        RedisClient client = createClient(addr.getHost(), addr.getPort(), c.getConnectTimeout(), c.getRetryInterval() * c.getRetryAttempts());
        RedisClient oldClient = sentinels.putIfAbsent(addr.getHost() + ":" + addr.getPort(), client);
        if (oldClient != null) {
            return newSucceededFuture(null);
        }

        Future<RedisPubSubConnection> pubsubFuture = client.connectPubSubAsync();
        pubsubFuture.addListener(new FutureListener<RedisPubSubConnection>() {
            @Override
            public void operationComplete(Future<RedisPubSubConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    log.warn("Can't connect to sentinel: {}:{}", addr.getHost(), addr.getPort());
                    return;
                }

                RedisPubSubConnection pubsub = future.getNow();
                pubsub.addListener(new BaseRedisPubSubListener() {

                    @Override
                    public void onMessage(String channel, Object msg) {
                        if ("+sentinel".equals(channel)) {
                            onSentinelAdded(cfg, (String) msg, c);
                        }
                        if ("+slave".equals(channel)) {
                            onSlaveAdded(addr, (String) msg);
                        }
                        if ("+sdown".equals(channel)) {
                            onNodeDown(addr, (String) msg);
                        }
                        if ("-sdown".equals(channel)) {
                            onNodeUp(addr, (String) msg);
                        }
                        if ("+switch-master".equals(channel)) {
                            onMasterChange(cfg, addr, (String) msg);
                        }
                    }

                    @Override
                    public boolean onStatus(PubSubType type, String channel) {
                        if (type == PubSubType.SUBSCRIBE) {
                            log.debug("subscribed to channel: {} from Sentinel {}:{}", channel, addr.getHost(), addr.getPort());
                        }
                        return true;
                    }
                });

                pubsub.subscribe(StringCodec.INSTANCE, "+switch-master", "+sdown", "-sdown", "+slave", "+sentinel");
                log.info("sentinel: {}:{} added", addr.getHost(), addr.getPort());
            }
        });

        return pubsubFuture;
    }

    protected void onSentinelAdded(SentinelServersConfig cfg, String msg, MasterSlaveServersConfig c) {
        String[] parts = msg.split(" ");
        if ("sentinel".equals(parts[0])) {
            String ip = parts[2];
            String port = parts[3];

            String addr = ip + ":" + port;
            URI uri = URIBuilder.create(addr);
            registerSentinel(cfg, uri, c);
        }
    }

    protected void onSlaveAdded(URI addr, String msg) {
        String[] parts = msg.split(" ");

        if (parts.length > 4
                 && "slave".equals(parts[0])) {
            final String ip = parts[2];
            final String port = parts[3];

            final String slaveAddr = ip + ":" + port;

            // to avoid addition twice
            if (slaves.putIfAbsent(slaveAddr, true) == null) {
                Future<Void> future = getEntry(singleSlotRange.getStartSlot()).addSlave(ip, Integer.valueOf(port));
                future.addListener(new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        if (!future.isSuccess()) {
                            slaves.remove(slaveAddr);
                            log.error("Can't add slave: " + slaveAddr, future.cause());
                            return;
                        }

                        if (getEntry(singleSlotRange.getStartSlot()).slaveUp(ip, Integer.valueOf(port), FreezeReason.MANAGER)) {
                            String slaveAddr = ip + ":" + port;
                            log.info("slave: {} added", slaveAddr);
                        }
                    }
                });
            } else {
                slaveUp(ip, port);
            }
        } else {
            log.warn("onSlaveAdded. Invalid message: {} from Sentinel {}:{}", msg, addr.getHost(), addr.getPort());
        }
    }

    private void onNodeDown(URI sentinelAddr, String msg) {
        String[] parts = msg.split(" ");

        if (parts.length > 3) {
            if ("slave".equals(parts[0])) {
                String ip = parts[2];
                String port = parts[3];

                slaveDown(ip, port);
            } else if ("sentinel".equals(parts[0])) {
                String ip = parts[2];
                String port = parts[3];

                String addr = ip + ":" + port;
                RedisClient sentinel = sentinels.remove(addr);
                if (sentinel != null) {
                    sentinel.shutdownAsync();
                    log.warn("sentinel: {} has down", addr);
                }
            } else if ("master".equals(parts[0])) {
            }
        } else {
            log.warn("onSlaveDown. Invalid message: {} from Sentinel {}:{}", msg, sentinelAddr.getHost(), sentinelAddr.getPort());
        }
    }

    private void slaveDown(String ip, String port) {
        if (config.getReadMode() == ReadMode.MASTER) {
            log.warn("slave: {}:{} has down", ip, port);
        } else {
            MasterSlaveEntry entry = getEntry(singleSlotRange.getStartSlot());
            if (entry.slaveDown(ip, Integer.valueOf(port), FreezeReason.MANAGER)) {
                log.warn("slave: {}:{} has down", ip, port);
            }
        }
    }

    private void onNodeUp(URI addr, String msg) {
        String[] parts = msg.split(" ");

        if (parts.length > 3) {
            if ("slave".equals(parts[0])) {
                String ip = parts[2];
                String port = parts[3];

                slaveUp(ip, port);
            } else if ("master".equals(parts[0])) {
                String ip = parts[2];
                String port = parts[3];

                String masterAddr = ip + ":" + port;
                MasterSlaveEntry entry = getEntry(singleSlotRange.getStartSlot());
                if (entry.isFreezed()
                        && entry.getClient().getAddr().equals(new InetSocketAddress(ip, Integer.valueOf(port)))) {
                    entry.unfreeze();
                    log.info("master: {} has up", masterAddr);
                }
            } else {
                log.warn("onSlaveUp. Invalid message: {} from Sentinel {}:{}", msg, addr.getHost(), addr.getPort());
            }
        }
    }

    private void slaveUp(String ip, String port) {
        if (config.getReadMode() == ReadMode.MASTER) {
            String slaveAddr = ip + ":" + port;
            log.info("slave: {} has up", slaveAddr);
            return;
        }

        if (getEntry(singleSlotRange.getStartSlot()).slaveUp(ip, Integer.valueOf(port), FreezeReason.MANAGER)) {
            String slaveAddr = ip + ":" + port;
            log.info("slave: {} has up", slaveAddr);
        }
    }

    private void onMasterChange(SentinelServersConfig cfg, URI addr, String msg) {
        String[] parts = msg.split(" ");

        if (parts.length > 3) {
            if (cfg.getMasterName().equals(parts[0])) {
                String ip = parts[3];
                String port = parts[4];

                String current = currentMaster.get();
                String newMaster = ip + ":" + port;
                if (!newMaster.equals(current)
                        && currentMaster.compareAndSet(current, newMaster)) {
                    changeMaster(singleSlotRange.getStartSlot(), ip, Integer.valueOf(port));
                    log.info("master {} changed to {}", current, newMaster);
                }
            }
        } else {
            log.warn("Invalid message: {} from Sentinel {}:{}", msg, addr.getHost(), addr.getPort());
        }
    }

    @Override
    protected MasterSlaveServersConfig create(BaseMasterSlaveServersConfig<?> cfg) {
        MasterSlaveServersConfig res = super.create(cfg);
        res.setDatabase(((SentinelServersConfig)cfg).getDatabase());
        return res;
    }

    @Override
    public void shutdown() {
        super.shutdown();

        for (RedisClient sentinel : sentinels.values()) {
            sentinel.shutdown();
        }
    }
}

