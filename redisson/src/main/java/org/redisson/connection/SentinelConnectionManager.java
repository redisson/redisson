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
package org.redisson.connection;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.RedisAuthRequiredException;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.BaseMasterSlaveServersConfig;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.config.SentinelServersConfig;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.misc.CountableListener;
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
public class SentinelConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Set<RedisURI> sentinelHosts = new HashSet<>();
    private final ConcurrentMap<RedisURI, RedisClient> sentinels = new ConcurrentHashMap<>();
    private final AtomicReference<String> currentMaster = new AtomicReference<>();

    private final Set<RedisURI> disconnectedSlaves = new HashSet<>();
    private ScheduledFuture<?> monitorFuture;
    private AddressResolver<InetSocketAddress> sentinelResolver;
    
    private final Map<String, String> natMap;

    private boolean usePassword = false;

    public SentinelConnectionManager(SentinelServersConfig cfg, Config config, UUID id) {
        super(config, id);
        
        if (cfg.getMasterName() == null) {
            throw new IllegalArgumentException("masterName parameter is not defined!");
        }
        if (cfg.getSentinelAddresses().isEmpty()) {
            throw new IllegalArgumentException("At least one sentinel node should be defined!");
        }

        this.config = create(cfg);
        initTimer(this.config);
        
        this.natMap=cfg.getNatMap();
        
        this.sentinelResolver = resolverGroup.getResolver(getGroup().next());
        
        for (String address : cfg.getSentinelAddresses()) {
            RedisURI addr = new RedisURI(address);
            RedisClient client = createClient(NodeType.SENTINEL, addr, this.config.getConnectTimeout(), this.config.getRetryInterval() * this.config.getRetryAttempts(), null);
            try {
                RedisConnection c = client.connect();
                try {
                    c.sync(RedisCommands.PING);
                } catch (RedisAuthRequiredException e) {
                    usePassword = true;
                }
                client.shutdown();
                break;
            } catch (Exception e) {
                // skip
            }
        }
        
        for (String address : cfg.getSentinelAddresses()) {
            RedisURI addr = new RedisURI(address);
            if (NetUtil.createByteArrayFromIpAddressString(addr.getHost()) == null && !addr.getHost().equals("localhost")) {
                sentinelHosts.add(convert(addr.getHost(), "" + addr.getPort()));
            }
            
            RedisClient client = createClient(NodeType.SENTINEL, addr, this.config.getConnectTimeout(), this.config.getRetryInterval() * this.config.getRetryAttempts(), null);
            try {
                RedisConnection connection = client.connect();
                if (!connection.isActive()) {
                    continue;
                }

                List<String> master = connection.sync(RedisCommands.SENTINEL_GET_MASTER_ADDR_BY_NAME, cfg.getMasterName());
                if (master.isEmpty()) {
                    throw new RedisConnectionException("Master node is undefined! SENTINEL GET-MASTER-ADDR-BY-NAME command returns empty result!");
                }
                
                String masterHost = createAddress(master.get(0), master.get(1));
                this.config.setMasterAddress(masterHost);
                currentMaster.set(masterHost);
                log.info("master: {} added", masterHost);

                List<Map<String, String>> sentinelSlaves = connection.sync(StringCodec.INSTANCE, RedisCommands.SENTINEL_SLAVES, cfg.getMasterName());
                for (Map<String, String> map : sentinelSlaves) {
                    if (map.isEmpty()) {
                        continue;
                    }

                    String ip = map.get("ip");
                    String port = map.get("port");
                    String flags = map.get("flags");

                    String host = createAddress(ip, port);

                    this.config.addSlaveAddress(host);
                    log.debug("slave {} state: {}", host, map);
                    log.info("slave: {} added", host);

                    if (flags.contains("s_down") || flags.contains("disconnected")) {
                        RedisURI uri = new RedisURI(host);
                        disconnectedSlaves.add(uri);
                        log.warn("slave: {} is down", host);
                    }
                }
                
                List<Map<String, String>> sentinelSentinels = connection.sync(StringCodec.INSTANCE, RedisCommands.SENTINEL_SENTINELS, cfg.getMasterName());
                List<RFuture<Void>> connectionFutures = new ArrayList<>(sentinelSentinels.size());
                for (Map<String, String> map : sentinelSentinels) {
                    if (map.isEmpty()) {
                        continue;
                    }

                    String ip = map.get("ip");
                    String port = map.get("port");

                    RedisURI sentinelAddr = convert(ip, port);
                    RFuture<Void> future = registerSentinel(sentinelAddr, this.config);
                    connectionFutures.add(future);
                }
                
                RedisURI currentAddr = convert(client.getAddr().getAddress().getHostAddress(), "" + client.getAddr().getPort());
                RFuture<Void> f = registerSentinel(currentAddr, this.config);
                connectionFutures.add(f);
                
                for (RFuture<Void> future : connectionFutures) {
                    future.awaitUninterruptibly(this.config.getConnectTimeout());
                }

                break;
            } catch (RedisConnectionException e) {
                log.warn("Can't connect to sentinel server. {}", e.getMessage());
            } finally {
                client.shutdownAsync();
            }
        }

        if (sentinels.isEmpty()) {
            stopThreads();
            throw new RedisConnectionException("At least two sentinels should be defined in Redis configuration! SENTINEL SENTINELS command returns empty result!");
        }
        
        if (currentMaster.get() == null) {
            stopThreads();
            throw new RedisConnectionException("Can't connect to servers!");
        }
        if (this.config.getReadMode() != ReadMode.MASTER && this.config.getSlaveAddresses().isEmpty()) {
            log.warn("ReadMode = " + this.config.getReadMode() + ", but slave nodes are not found!");
        }
        
        initSingleEntry();
        
        scheduleChangeCheck(cfg, null);
    }
    
    @Override
    protected void startDNSMonitoring(RedisClient masterHost) {
        if (config.getDnsMonitoringInterval() == -1 || sentinelHosts.isEmpty()) {
            return;
        }
        
        scheduleSentinelDNSCheck();
    }
    
    @Override
    protected RedisClientConfig createRedisConfig(NodeType type, RedisURI address, int timeout, int commandTimeout,
            String sslHostname) {
        RedisClientConfig result = super.createRedisConfig(type, address, timeout, commandTimeout, sslHostname);
        if (type == NodeType.SENTINEL && !usePassword) {
            result.setPassword(null);
        }
        return result;
    }

    private void scheduleSentinelDNSCheck() {
        monitorFuture = group.schedule(new Runnable() {
            @Override
            public void run() {
                AtomicInteger sentinelsCounter = new AtomicInteger(sentinelHosts.size());
                FutureListener<List<InetSocketAddress>> commonListener = new FutureListener<List<InetSocketAddress>>() {
                    @Override
                    public void operationComplete(Future<List<InetSocketAddress>> future) throws Exception {
                        if (sentinelsCounter.decrementAndGet() == 0) {
                            scheduleSentinelDNSCheck();
                        }
                    }
                };
                
                for (RedisURI host : sentinelHosts) {
                    Future<List<InetSocketAddress>> allNodes = sentinelResolver.resolveAll(InetSocketAddress.createUnresolved(host.getHost(), host.getPort()));
                    allNodes.addListener(new FutureListener<List<InetSocketAddress>>() {
                        @Override
                        public void operationComplete(Future<List<InetSocketAddress>> future) throws Exception {
                            if (!future.isSuccess()) {
                                log.error("Unable to resolve " + host.getHost(), future.cause());
                                return;
                            }
                            
                            Set<RedisURI> newUris = future.getNow().stream()
                                                .map(addr -> convert(addr.getAddress().getHostAddress(), "" + addr.getPort()))
                                                .collect(Collectors.toSet());

                            for (RedisURI uri : newUris) {
                                if (!sentinels.containsKey(uri)) {
                                    registerSentinel(uri, getConfig());
                                }
                            }
                        }
                    });
                    allNodes.addListener(commonListener);
                }
            }
        }, config.getDnsMonitoringInterval(), TimeUnit.MILLISECONDS);
    }
    
    private void scheduleChangeCheck(SentinelServersConfig cfg, Iterator<RedisClient> iterator) {
        monitorFuture = group.schedule(new Runnable() {
            @Override
            public void run() {
                AtomicReference<Throwable> lastException = new AtomicReference<Throwable>();
                Iterator<RedisClient> iter = iterator;
                if (iter == null) {
                    // Shuffle the list so all clients don't prefer the same sentinel
                    List<RedisClient> clients = new ArrayList<>(sentinels.values());
                    Collections.shuffle(clients);
                    iter = clients.iterator();
                }
                checkState(cfg, iter, lastException);
            }
        }, cfg.getScanInterval(), TimeUnit.MILLISECONDS);
    }

    private void checkState(SentinelServersConfig cfg, Iterator<RedisClient> iterator, AtomicReference<Throwable> lastException) {
        if (!iterator.hasNext()) {
            if (lastException.get() != null) {
                log.error("Can't update cluster state", lastException.get());
            }
            scheduleChangeCheck(cfg, null);
            return;
        }
        if (!getShutdownLatch().acquire()) {
            return;
        }

        RedisClient client = iterator.next();
        RFuture<RedisConnection> connectionFuture = connectToNode(null, null, client, null);
        connectionFuture.onComplete((connection, e) -> {
            if (e != null) {
                lastException.set(e);
                getShutdownLatch().release();
                checkState(cfg, iterator, lastException);
                return;
            }

            updateState(cfg, connection, iterator);
        });

    }

    private void updateState(SentinelServersConfig cfg, RedisConnection connection, Iterator<RedisClient> iterator) {
        AtomicInteger commands = new AtomicInteger(2);
        BiConsumer<Object, Throwable> commonListener = new BiConsumer<Object, Throwable>() {
            
            private final AtomicBoolean failed = new AtomicBoolean();
            
            @Override
            public void accept(Object t, Throwable u) {
                if (commands.decrementAndGet() == 0) {
                    getShutdownLatch().release();
                    if (failed.get()) {
                        scheduleChangeCheck(cfg, iterator);
                    } else {
                        scheduleChangeCheck(cfg, null);
                    }
                }
                if (u != null && failed.compareAndSet(false, true)) {
                    log.error("Can't execute SENTINEL commands on " + connection.getRedisClient().getAddr(), u);
                    closeNodeConnection(connection);
                }
            }
        };
        
        RFuture<List<String>> masterFuture = connection.async(StringCodec.INSTANCE, RedisCommands.SENTINEL_GET_MASTER_ADDR_BY_NAME, cfg.getMasterName());
        masterFuture.onComplete((master, e) -> {
            if (e != null) {
                return;
            }

            String current = currentMaster.get();
            String newMaster = createAddress(master.get(0), master.get(1));
            if (!newMaster.equals(current)
                    && currentMaster.compareAndSet(current, newMaster)) {
                RFuture<RedisClient> changeFuture = changeMaster(singleSlotRange.getStartSlot(), new RedisURI(newMaster));
                changeFuture.onComplete((res, ex) -> {
                    if (ex != null) {
                        currentMaster.compareAndSet(newMaster, current);
                    }
                });
            }
        });
        masterFuture.onComplete(commonListener);
        
        if (!config.checkSkipSlavesInit()) {
            RFuture<List<Map<String, String>>> slavesFuture = connection.async(StringCodec.INSTANCE, RedisCommands.SENTINEL_SLAVES, cfg.getMasterName());
            commands.incrementAndGet();
            slavesFuture.onComplete((slavesMap, e) -> {
                if (e != null) {
                    return;
                }
                
                Set<String> currentSlaves = new HashSet<String>(slavesMap.size());
                List<RFuture<Void>> futures = new ArrayList<>();
                for (Map<String, String> map : slavesMap) {
                    if (map.isEmpty()) {
                        continue;
                    }
                    
                    String ip = map.get("ip");
                    String port = map.get("port");
                    String flags = map.get("flags");
                    String masterHost = map.get("master-host");
                    String masterPort = map.get("master-port");
                    
                    if (flags.contains("s_down") || flags.contains("disconnected")) {
                        slaveDown(ip, port);
                        continue;
                    }
                    if ("?".equals(masterHost) || !isUseSameMaster(ip, port, masterHost, masterPort)) {
                        continue;
                    }
                    
                    String slaveAddr = createAddress(ip, port);
                    currentSlaves.add(slaveAddr);
                    RFuture<Void> slaveFuture = addSlave(ip, port, slaveAddr);
                    futures.add(slaveFuture);
                }
                
                CountableListener<Void> listener = new CountableListener<Void>() {
                    @Override
                    protected void onSuccess(Void value) {
                        MasterSlaveEntry entry = getEntry(singleSlotRange.getStartSlot());
                        Set<String> removedSlaves = new HashSet<String>();
                        for (ClientConnectionsEntry e : entry.getAllEntries()) {
                            InetSocketAddress addr = e.getClient().getAddr();
                            String slaveAddr = createAddress(addr.getAddress().getHostAddress(), addr.getPort());
                            removedSlaves.add(slaveAddr);
                        }
                        removedSlaves.removeAll(currentSlaves);
                        
                        for (String slave : removedSlaves) {
                            if (slave.equals(currentMaster.get())) {
                                continue;
                            }
                            String hostPort = slave.replace("redis://", "");
                            int lastColonIdx = hostPort.lastIndexOf(":");
                            String host = hostPort.substring(0, lastColonIdx);
                            String port = hostPort.substring(lastColonIdx + 1);
                            slaveDown(host, port);
                        }
                    };
                };
                
                listener.setCounter(futures.size());
                for (RFuture<Void> f : futures) {
                    f.onComplete(listener);
                }
            });
            slavesFuture.onComplete(commonListener);
        }
                
        RFuture<List<Map<String, String>>> sentinelsFuture = connection.async(StringCodec.INSTANCE, RedisCommands.SENTINEL_SENTINELS, cfg.getMasterName());
        sentinelsFuture.onComplete((list, e) -> {
            if (e != null) {
                return;
            }
            
            Set<RedisURI> newUris = list.stream().filter(m -> {
                String flags = m.get("flags");
                if (!m.isEmpty() && !flags.contains("disconnected") && !flags.contains("s_down")) {
                    return true;
                }
                return false;
            }).map(m -> {
                String ip = m.get("ip");
                String port = m.get("port");
                return convert(ip, port);
            }).collect(Collectors.toSet());
            
            InetSocketAddress addr = connection.getRedisClient().getAddr();
            RedisURI currentAddr = convert(addr.getAddress().getHostAddress(), "" + addr.getPort());
            newUris.add(currentAddr);
            
            updateSentinels(newUris);
        });
        sentinelsFuture.onComplete(commonListener);
    }

    private void updateSentinels(Set<RedisURI> newUris) {
        Set<RedisURI> currentUris = new HashSet<>(SentinelConnectionManager.this.sentinels.keySet());
        Set<RedisURI> addedUris = new HashSet<>(newUris);
        addedUris.removeAll(currentUris);
        
        for (RedisURI uri : addedUris) {
            registerSentinel(uri, getConfig());
        }
        currentUris.removeAll(newUris);
        
        for (RedisURI uri : currentUris) {
            RedisClient sentinel = SentinelConnectionManager.this.sentinels.remove(uri);
            if (sentinel != null) {
                sentinel.shutdownAsync();
                log.warn("sentinel: {} has down", uri);
            }
        }
    }

    private String createAddress(String host, Object port) {
        if (host.contains(":")){
            String pureHost = host.replaceAll("[\\[\\]]", "");
            host = applyNatMap(pureHost);
            if (host.contains(":")) {
                host = "[" + host + "]";
            }
        } else {
            host = applyNatMap(host);
        }
        return "redis://" + host + ":" + port;
    }

    @Override
    protected MasterSlaveEntry createMasterSlaveEntry(MasterSlaveServersConfig config) {
        MasterSlaveEntry entry = new MasterSlaveEntry(this, config);
        List<RFuture<Void>> fs = entry.initSlaveBalancer(disconnectedSlaves);
        for (RFuture<Void> future : fs) {
            future.syncUninterruptibly();
        }
        return entry;
    }

    private RFuture<Void> registerSentinel(RedisURI addr, MasterSlaveServersConfig c) {
        RedisClient sentinel = sentinels.get(addr);
        if (sentinel != null) {
            return RedissonPromise.newSucceededFuture(null);
        }
        
        RedisClient client = createClient(NodeType.SENTINEL, addr, c.getConnectTimeout(), c.getRetryInterval() * c.getRetryAttempts(), null);
        RPromise<Void> result = new RedissonPromise<Void>();
        RFuture<InetSocketAddress> future = client.resolveAddr();
        future.onComplete((res, e) -> {
            if (e != null) {
                result.tryFailure(e);
                return;
            }

            RFuture<RedisConnection> f = client.connectAsync();
            f.onComplete((connection, ex) -> {
                if (ex != null) {
                    result.tryFailure(ex);
                    return;
                }

                RFuture<String> r = connection.async(config.getTimeout(), RedisCommands.PING);
                r.onComplete((resp, exc) -> {
                    if (exc != null) {
                        result.tryFailure(exc);
                        return;
                    }
                    
                    if (sentinels.putIfAbsent(addr, client) == null) {
                        log.info("sentinel: {} added", addr);
                    }
                    result.trySuccess(null);
                });
            });

        });
        return result;
    }

    private RFuture<Void> addSlave(String ip, String port, String addr) {
        RPromise<Void> result = new RedissonPromise<Void>();
        // to avoid addition twice
        MasterSlaveEntry entry = getEntry(singleSlotRange.getStartSlot());
        RedisURI uri = convert(ip, port);
        if (!entry.hasSlave(uri) && !config.checkSkipSlavesInit()) {
            RFuture<Void> future = entry.addSlave(new RedisURI(addr));
            future.onComplete((res, e) -> {
                if (e != null) {
                    result.tryFailure(e);
                    log.error("Can't add slave: " + addr, e);
                    return;
                }

                if (entry.isSlaveUnfreezed(uri) || entry.slaveUp(uri, FreezeReason.MANAGER)) {
                    String slaveAddr = ip + ":" + port;
                    log.info("slave: {} added", slaveAddr);
                    result.trySuccess(null);
                }
            });
        } else {
            if (entry.hasSlave(uri)) {
                slaveUp(ip, port);
            }
            result.trySuccess(null);
        }
        return result;
    }

    private RedisURI convert(String ip, String port) {
        String addr = createAddress(ip, port);
        RedisURI uri = new RedisURI(addr);
        return uri;
    }
    
    private void slaveDown(String ip, String port) {
        if (config.checkSkipSlavesInit()) {
            log.warn("slave: {}:{} has down", ip, port);
        } else {
            MasterSlaveEntry entry = getEntry(singleSlotRange.getStartSlot());
            RedisURI uri = convert(ip, port);
            if (entry.slaveDown(uri, FreezeReason.MANAGER)) {
                log.warn("slave: {}:{} has down", ip, port);
            }
        }
    }

    private boolean isUseSameMaster(String slaveIp, String slavePort, String slaveMasterHost, String slaveMasterPort) {
        String master = currentMaster.get();
        String slaveMaster = createAddress(slaveMasterHost, slaveMasterPort);
        if (!master.equals(slaveMaster)) {
            log.warn("Skipped slave up {} for master {} differs from current {}", slaveIp + ":" + slavePort, slaveMaster, master);
            return false;
        }
        return true;
    }
    
    private void slaveUp(String ip, String port) {
        if (config.checkSkipSlavesInit()) {
            String slaveAddr = ip + ":" + port;
            log.info("slave: {} has up", slaveAddr);
            return;
        }

        RedisURI uri = convert(ip, port);
        if (getEntry(singleSlotRange.getStartSlot()).slaveUp(uri, FreezeReason.MANAGER)) {
            String slaveAddr = ip + ":" + port;
            log.info("slave: {} has up", slaveAddr);
        }
    }

    @Override
    protected MasterSlaveServersConfig create(BaseMasterSlaveServersConfig<?> cfg) {
        MasterSlaveServersConfig res = super.create(cfg);
        res.setDatabase(((SentinelServersConfig) cfg).getDatabase());
        return res;
    }
    
    public Collection<RedisClient> getSentinels() {
        return sentinels.values();
    }

    @Override
    public void shutdown() {
        if (monitorFuture != null) {
            monitorFuture.cancel(true);
        }
        
        List<RFuture<Void>> futures = new ArrayList<>();
        for (RedisClient sentinel : sentinels.values()) {
            RFuture<Void> future = sentinel.shutdownAsync();
            futures.add(future);
        }
        
        for (RFuture<Void> future : futures) {
            future.syncUninterruptibly();
        }
        
        super.shutdown();
    }
    
    private String applyNatMap(String ip) {
        String mappedAddress = natMap.get(ip);
        if (mappedAddress != null) {
            return mappedAddress;
        }
        return ip;
    }
}

