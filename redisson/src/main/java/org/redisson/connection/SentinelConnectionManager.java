/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import io.netty.resolver.AddressResolver;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.StringUtil;
import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.*;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.config.BaseMasterSlaveServersConfig;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.config.SentinelServersConfig;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.misc.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SentinelConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Set<RedisURI> sentinelHosts = new HashSet<>();
    private final ConcurrentMap<RedisURI, RedisClient> sentinels = new ConcurrentHashMap<>();
    private final AtomicReference<RedisURI> currentMaster = new AtomicReference<>();

    private final Set<RedisURI> disconnectedSlaves = new HashSet<>();
    private ScheduledFuture<?> monitorFuture;
    private final AddressResolver<InetSocketAddress> sentinelResolver;
    private final Set<RedisURI> disconnectedSentinels = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private RedisStrictCommand<RedisURI> masterHostCommand;

    private boolean usePassword = false;
    private String scheme;
    private SentinelServersConfig cfg;

    public SentinelConnectionManager(SentinelServersConfig cfg, ServiceManager serviceManager) {
        super(cfg, serviceManager);
        this.serviceManager.setNatMapper(cfg.getNatMapper());

        this.sentinelResolver = serviceManager.getResolverGroup().getResolver(serviceManager.getGroup().next());

        for (String address : cfg.getSentinelAddresses()) {
            RedisURI addr = new RedisURI(address);
            scheme = addr.getScheme();
            addr = applyNatMap(addr);
            if (NetUtil.createByteArrayFromIpAddressString(addr.getHost()) == null && !addr.getHost().equals("localhost")) {
                sentinelHosts.add(addr);
            }
        }
    }

    @Override
    public void connect() {
        checkAuth(cfg);

        if ("redis".equals(scheme)) {
            masterHostCommand = RedisCommands.SENTINEL_GET_MASTER_ADDR_BY_NAME;
        } else {
            masterHostCommand = RedisCommands.SENTINEL_GET_MASTER_ADDR_BY_NAME_SSL;
        }

        Throwable lastException = null;
        for (String address : cfg.getSentinelAddresses()) {
            RedisURI addr = new RedisURI(address);
            addr = applyNatMap(addr);

            RedisClient client = createClient(NodeType.SENTINEL, addr, this.config.getConnectTimeout(), this.config.getTimeout(), null);
            try {
                RedisConnection connection = null;
                try {
                    connection = client.connect();
                    if (!connection.isActive()) {
                        continue;
                    }
                } catch (RedisConnectionException e) {
                    continue;
                }

                RedisURI master = connection.sync(masterHostCommand, cfg.getMasterName());
                if (master == null) {
                    throw new RedisConnectionException("Master node is undefined! SENTINEL GET-MASTER-ADDR-BY-NAME command returns empty result!");
                }

                RedisURI masterHost = serviceManager.resolveIP(scheme, master).join();
                this.config.setMasterAddress(masterHost.toString());
                currentMaster.set(masterHost);
                log.info("master: {} added", masterHost);

                List<Map<String, String>> sentinelSlaves = connection.sync(StringCodec.INSTANCE, RedisCommands.SENTINEL_SLAVES, cfg.getMasterName());
                for (Map<String, String> map : sentinelSlaves) {
                    if (map.isEmpty()) {
                        continue;
                    }

                    String host = map.get("ip");
                    String port = map.get("port");
                    String flags = map.getOrDefault("flags", "");
                    String masterLinkStatus = map.getOrDefault("master-link-status", "");

                    RedisURI uri = resolveIP(host, port).join();

                    this.config.addSlaveAddress(uri.toString());
                    log.debug("slave {} state: {}", uri, map);
                    log.info("slave: {} added", uri);

                    if (isSlaveDown(flags, masterLinkStatus)) {
                        disconnectedSlaves.add(uri);
                        log.warn("slave: {} is down", uri);
                    }
                }

                List<Map<String, String>> sentinelSentinels = connection.sync(StringCodec.INSTANCE, RedisCommands.SENTINEL_SENTINELS, cfg.getMasterName());
                List<CompletableFuture<Void>> connectionFutures = new ArrayList<>(sentinelSentinels.size());
                for (Map<String, String> map : sentinelSentinels) {
                    if (map.isEmpty()) {
                        continue;
                    }

                    String ip = map.get("ip");
                    String port = map.get("port");

                    RedisURI uri = resolveIP(ip, port).join();
                    CompletionStage<Void> future = registerSentinel(uri, this.config, null);
                    connectionFutures.add(future.toCompletableFuture());
                }

                RedisURI sentinelIp = toURI(connection.getRedisClient().getAddr());
                CompletionStage<Void> f = registerSentinel(sentinelIp, this.config, null);
                connectionFutures.add(f.toCompletableFuture());

                CompletableFuture<Void> future = CompletableFuture.allOf(connectionFutures.toArray(new CompletableFuture[0]));
                try {
                    future.get(this.config.getConnectTimeout(), TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    // skip
                }

                break;
            } catch (RedisConnectionException e) {
                shutdown();
                throw e;
            } catch (Exception e) {
                if (e instanceof CompletionException) {
                    e = (Exception) e.getCause();
                }
                lastException = e;
                log.warn(e.getMessage());
            } finally {
                client.shutdownAsync();
            }
        }

        if (cfg.isCheckSentinelsList() && cfg.isSentinelsDiscovery()) {
            if (sentinels.isEmpty()) {
                shutdown();
                throw new RedisConnectionException("SENTINEL SENTINELS command returns empty result or connection can't be established to some of them! Set checkSentinelsList = false to avoid this check.", lastException);
            } else if (sentinels.size() < 2) {
                shutdown();
                throw new RedisConnectionException("SENTINEL SENTINELS command returns less than 2 nodes or connection can't be established to some of them! At least two sentinels should be defined in Redis configuration. Set checkSentinelsList = false to avoid this check.", lastException);
            }
        }
        
        if (currentMaster.get() == null) {
            shutdown();
            throw new RedisConnectionException("Can't connect to servers!", lastException);
        }
        if (this.config.getReadMode() != ReadMode.MASTER && this.config.getSlaveAddresses().isEmpty()) {
            log.warn("ReadMode = {}, but slave nodes are not found!", this.config.getReadMode());
        }

        super.connect();

        scheduleChangeCheck(cfg, null);
    }

    private void checkAuth(SentinelServersConfig cfg) {
        if (cfg.getPassword() == null) {
            return;
        }

        for (String address : cfg.getSentinelAddresses()) {
            RedisURI addr = new RedisURI(address);
            addr = applyNatMap(addr);

            RedisClient client = createClient(NodeType.SENTINEL, addr, this.config.getConnectTimeout(), this.config.getTimeout(), null);
            try {
                RedisConnection c = client.connect();
                if (config.getPingConnectionInterval() == 0) {
                    c.sync(RedisCommands.PING);
                }
                return;
            } catch (RedisAuthRequiredException e) {
                usePassword = true;
                return;
            } catch (RedisConnectionException e) {
                log.warn("Can't connect to sentinel server", e);
            } catch (Exception e) {
                // skip
            } finally {
                client.shutdown();
            }
        }

        shutdown();
        StringBuilder list = new StringBuilder();
        for (String address : cfg.getSentinelAddresses()) {
            list.append(address).append(", ");
        }
        throw new RedisConnectionException("Unable to connect to Redis sentinel servers: " + list);
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
            result.setUsername(null);
            result.setPassword(null);
        } else if (type == NodeType.SENTINEL && usePassword) {
            result.setUsername(cfg.getSentinelUsername());
            if (cfg.getSentinelPassword() != null) {
                result.setPassword(cfg.getSentinelPassword());
            }
        }
        return result;
    }

    private void scheduleSentinelDNSCheck() {
        monitorFuture = serviceManager.getGroup().schedule(new Runnable() {
            @Override
            public void run() {
                AtomicInteger sentinelsCounter = new AtomicInteger(sentinelHosts.size());
                performSentinelDNSCheck(future -> {
                    if (sentinelsCounter.decrementAndGet() == 0) {
                        scheduleSentinelDNSCheck();
                    }
                });
            }
        }, config.getDnsMonitoringInterval(), TimeUnit.MILLISECONDS);
    }

    private void performSentinelDNSCheck(FutureListener<List<InetSocketAddress>> commonListener) {
        for (RedisURI host : sentinelHosts) {
            Future<List<InetSocketAddress>> allNodes = sentinelResolver.resolveAll(InetSocketAddress.createUnresolved(host.getHost(), host.getPort()));
            allNodes.addListener((FutureListener<List<InetSocketAddress>>) future -> {
                if (!future.isSuccess()) {
                    log.error("Unable to resolve {}", host.getHost(), future.cause());
                    return;
                }

                future.getNow().stream()
                        .map(addr -> toURI(addr))
                        .filter(uri -> !sentinels.containsKey(uri) && !disconnectedSentinels.contains(uri))
                        .forEach(uri -> registerSentinel(uri, serviceManager.getConfig(), host.getHost()));
            });
            if (commonListener != null) {
                allNodes.addListener(commonListener);
            }
        }
    }
    
    private void scheduleChangeCheck(SentinelServersConfig cfg, Iterator<RedisClient> iterator) {
        monitorFuture = serviceManager.getGroup().schedule(new Runnable() {
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
            disconnectedSentinels.clear();
            performSentinelDNSCheck(null);
            scheduleChangeCheck(cfg, null);
            return;
        }
        if (!serviceManager.getShutdownLatch().acquire()) {
            return;
        }

        RedisClient client = iterator.next();
        RedisURI addr = toURI(client.getAddr());
        CompletionStage<RedisConnection> connectionFuture = connectToNode(NodeType.SENTINEL, cfg, addr, null);
        connectionFuture.whenComplete((connection, e) -> {
            if (e != null) {
                lastException.set(e);
                serviceManager.getShutdownLatch().release();
                checkState(cfg, iterator, lastException);
                return;
            }

            updateState(cfg, connection, iterator);
        });

    }

    private void updateState(SentinelServersConfig cfg, RedisConnection connection, Iterator<RedisClient> iterator) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        CompletionStage<RedisClient> masterFuture = checkMasterChange(cfg, connection);
        futures.add(masterFuture.toCompletableFuture());

        if (!config.isSlaveNotUsed()) {
            CompletionStage<Void> slavesFuture = checkSlavesChange(cfg, connection);
            futures.add(slavesFuture.toCompletableFuture());
        }

        CompletionStage<Void> sentinelsFuture = checkSentinelsChange(cfg, connection);
        futures.add(sentinelsFuture.toCompletableFuture());

        CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        future.whenComplete((r, e) -> {
            if (e != null) {
                log.error("Can't execute SENTINEL commands on {}", connection.getRedisClient().getAddr(), e);
            }

            serviceManager.getShutdownLatch().release();
            if (e != null) {
                scheduleChangeCheck(cfg, iterator);
            } else {
                scheduleChangeCheck(cfg, null);
            }
        });
    }

    private CompletionStage<Void> checkSentinelsChange(SentinelServersConfig cfg, RedisConnection connection) {
        if (!cfg.isSentinelsDiscovery()) {
            return CompletableFuture.completedFuture(null);
        }

        RFuture<List<Map<String, String>>> sentinelsFuture = connection.async(StringCodec.INSTANCE, RedisCommands.SENTINEL_SENTINELS, cfg.getMasterName());
        return sentinelsFuture.thenCompose(list -> {
            if (list.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            List<CompletableFuture<RedisURI>> newUris = list.stream().filter(m -> {
                String flags = m.getOrDefault("flags", "");
                String masterLinkStatus = m.getOrDefault("master-link-status", "");
                if (!m.isEmpty() && !isSlaveDown(flags, masterLinkStatus)) {
                    return true;
                }
                return false;
            }).map(m -> {
                String ip = m.get("ip");
                String port = m.get("port");
                return serviceManager.toURI(scheme, ip, port);
            }).map(addr -> {
                CompletionStage<RedisURI> f = serviceManager.resolveIP(addr);
                return f.exceptionally(ex -> {
                    log.error("unable to resolve hostname", ex);
                    return null;
                }).toCompletableFuture();
            }).collect(Collectors.toList());

            CompletableFuture<Void> futures = CompletableFuture.allOf(newUris.toArray(new CompletableFuture[0]));
            return futures.whenComplete((r, ex) -> {
                List<RedisURI> uris = newUris.stream().map(u -> {
                    try {
                        return u.getNow(null);
                    } catch (Exception exc) {
                        return null;
                    }
                }).filter(u -> u != null).collect(Collectors.toList());

                InetSocketAddress addr = connection.getRedisClient().getAddr();
                RedisURI currentAddr = toURI(addr);
                uris.add(currentAddr);

                updateSentinels(uris);
            });
        });
    }

    private CompletionStage<Void> checkSlavesChange(SentinelServersConfig cfg, RedisConnection connection) {
        RFuture<List<Map<String, String>>> slavesFuture = connection.async(StringCodec.INSTANCE, RedisCommands.SENTINEL_SLAVES, cfg.getMasterName());
        return slavesFuture.thenCompose(slavesMap -> {
            Set<RedisURI> currentSlaves = Collections.newSetFromMap(new ConcurrentHashMap<>(slavesMap.size()));
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Map<String, String> map : slavesMap) {
                if (map.isEmpty()) {
                    continue;
                }

                String host = map.get("ip");
                String port = map.get("port");
                String flags = map.getOrDefault("flags", "");
                String masterLinkStatus = map.getOrDefault("master-link-status", "");
                String masterHost = map.get("master-host");
                String masterPort = map.get("master-port");

                CompletableFuture<RedisURI> slaveAddrFuture = resolveIP(host, port);
                CompletableFuture<RedisURI> masterAddrFuture;
                if ("?".equals(masterHost)) {
                    masterAddrFuture = CompletableFuture.completedFuture(null);
                } else {
                    masterAddrFuture = resolveIP(masterHost, masterPort);
                }

                CompletableFuture<Void> resolvedFuture = CompletableFuture.allOf(masterAddrFuture,
                                                                                    slaveAddrFuture);
                futures.add(resolvedFuture
                        .whenComplete((r, exc) -> {
                            if (exc != null) {
                                log.error("Unable to resolve addresses {} and/or {}", host, masterHost, exc);
                            }
                        })
                        .thenCompose(res -> {
                            RedisURI slaveAddr = slaveAddrFuture.getNow(null);
                            RedisURI masterAddr = masterAddrFuture.getNow(null);
                            if (isSlaveDown(flags, masterLinkStatus)) {
                                slaveDown(slaveAddr);
                                return CompletableFuture.completedFuture(res);
                            }
                            if ("?".equals(masterHost) || !isUseSameMaster(slaveAddr, masterAddr)) {
                                return CompletableFuture.completedFuture(res);
                            }

                            currentSlaves.add(slaveAddr);
                            return addSlave(slaveAddr).whenComplete((r, e) -> {
                                if (e != null) {
                                    log.error("Unable to add slave {}", slaveAddr, e);
                                }
                            });
                }));
            }

            CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return future.whenComplete((r, exc) -> {
                MasterSlaveEntry entry = getEntry(singleSlotRange.getStartSlot());
                entry.getAllEntries().stream()
                        .map(e -> e.getClient().getAddr())
                        .map(a -> serviceManager.toURI(scheme, a.getAddress().getHostAddress(), String.valueOf(a.getPort())))
                        .filter(a -> !currentSlaves.contains(a) && !a.equals(currentMaster.get()))
                        .forEach(a -> slaveDown(a));
            });
        });
    }

    private CompletionStage<RedisClient> checkMasterChange(SentinelServersConfig cfg, RedisConnection connection) {
        RFuture<RedisURI> masterFuture = connection.async(StringCodec.INSTANCE, masterHostCommand, cfg.getMasterName());
        return masterFuture
                .thenCompose(u -> serviceManager.resolveIP(scheme, u))
                .thenCompose(newMaster -> {
                    RedisURI current = currentMaster.get();
                    if (!newMaster.equals(current)
                            && currentMaster.compareAndSet(current, newMaster)) {
                        CompletableFuture<RedisClient> changeFuture = changeMaster(singleSlotRange.getStartSlot(), newMaster);
                        return changeFuture.exceptionally(ex -> {
                            currentMaster.compareAndSet(newMaster, current);
                            return null;
                        });
                    }
                    return CompletableFuture.completedFuture(null);
                });
    }

    private void updateSentinels(Collection<RedisURI> newUris) {
        newUris.stream()
                .filter(uri -> !sentinels.containsKey(uri))
                .forEach(uri -> {
                    disconnectedSentinels.remove(uri);
                    registerSentinel(uri, serviceManager.getConfig(), null);
                });

        sentinels.keySet().stream()
                .filter(uri -> !newUris.contains(uri))
                .forEach(uri -> {
                    RedisClient sentinel = sentinels.remove(uri);
                    if (sentinel != null) {
                        disconnectNode(uri);
                        sentinel.shutdownAsync();
                        disconnectedSentinels.add(uri);
                        log.warn("sentinel: {} is down", uri);
                    }
                });
    }

    @Override
    protected Collection<RedisURI> getDisconnectedNodes() {
        return disconnectedSlaves;
    }

    private CompletionStage<Void> registerSentinel(RedisURI addr, MasterSlaveServersConfig c, String sslHostname) {
        boolean isHostname = NetUtil.createByteArrayFromIpAddressString(addr.getHost()) == null;
        if (!isHostname) {
            RedisClient sentinel = sentinels.get(addr);
            if (sentinel != null) {
                return CompletableFuture.completedFuture(null);
            }
        }

        RedisClient client = createClient(NodeType.SENTINEL, addr, c.getConnectTimeout(), c.getTimeout(), sslHostname);
        CompletableFuture<InetSocketAddress> future = client.resolveAddr();
        return future.thenCompose(res -> {
            RedisURI ipAddr = toURI(client.getAddr());
            if (isHostname) {
                RedisClient sentinel = sentinels.get(ipAddr);
                if (sentinel != null) {
                    return CompletableFuture.completedFuture(null);
                }
            }

            CompletionStage<RedisConnection> f = client.connectAsync();
            return f.handle((resp, e) -> {
                if (e != null) {
                    log.error(e.getMessage(), e);
                    throw new CompletionException(e);
                }
                if (sentinels.putIfAbsent(ipAddr, client) == null) {
                    log.info("sentinel: {} added", ipAddr);
                }
                return null;
            });
        });
    }

    private CompletableFuture<RedisURI> resolveIP(String host, String port) {
        RedisURI uri = serviceManager.toURI(scheme, host, port);
        return serviceManager.resolveIP(uri);
    }

    private RedisURI toURI(InetSocketAddress addr) {
        return serviceManager.toURI(scheme, addr.getAddress().getHostAddress(), "" + addr.getPort());
    }

    private CompletableFuture<Void> addSlave(RedisURI uri) {
        if (config.isSlaveNotUsed()) {
            log.info("slave: {} is up", uri);
            return CompletableFuture.completedFuture(null);
        }

        // to avoid addition twice
        MasterSlaveEntry entry = getEntry(singleSlotRange.getStartSlot());
        if (!entry.hasSlave(uri)) {
            CompletableFuture<Void> future = entry.addSlave(uri);
            return future.thenApply(res -> {
                log.info("slave: {} added", uri);
                return null;
            });
        }

        CompletableFuture<Boolean> f = entry.slaveUpAsync(uri, FreezeReason.MANAGER);
        return f.thenCompose(e -> {
            if (e) {
                log.info("slave: {} is up", uri);
                return entry.excludeMasterFromSlaves(uri);
            }
            return CompletableFuture.completedFuture(e);
        }).thenApply(e -> null);
    }

    private void slaveDown(RedisURI uri) {
        if (config.isSlaveNotUsed()) {
            log.warn("slave: {} is down", uri);
        } else {
            MasterSlaveEntry entry = getEntry(singleSlotRange.getStartSlot());
            entry.slaveDownAsync(uri, FreezeReason.MANAGER).thenAccept(r -> {
                if (r) {
                    log.warn("slave: {} is down", uri);
                }
            });
        }
    }

    private boolean isSlaveDown(String flags, String masterLinkStatus) {
        boolean baseStatus = flags.contains("s_down") || flags.contains("disconnected");
        if (cfg.isCheckSlaveStatusWithSyncing() && !StringUtil.isNullOrEmpty(masterLinkStatus)) {
            return baseStatus || masterLinkStatus.contains("err");
        }
        return baseStatus;
    }

    private boolean isUseSameMaster(RedisURI slaveAddr, RedisURI slaveMasterAddr) {
        RedisURI master = currentMaster.get();
        if (!master.equals(slaveMasterAddr)) {
            log.warn("Skipped slave up {} for master {} differs from current {}", slaveAddr, slaveMasterAddr, master);
            return false;
        }
        return true;
    }

    @Override
    protected MasterSlaveServersConfig create(BaseMasterSlaveServersConfig<?> cfg) {
        this.cfg = (SentinelServersConfig) cfg;
        if (this.cfg.getMasterName() == null) {
            throw new IllegalArgumentException("masterName parameter is not defined!");
        }
        if (this.cfg.getSentinelAddresses().isEmpty()) {
            throw new IllegalArgumentException("At least one sentinel node should be defined!");
        }

        MasterSlaveServersConfig res = super.create(cfg);
        res.setDatabase(this.cfg.getDatabase());
        return res;
    }
    
    public Collection<RedisClient> getSentinels() {
        return sentinels.values();
    }

    @Override
    public void shutdown(long quietPeriod, long timeout, TimeUnit unit) {
        if (monitorFuture != null) {
            monitorFuture.cancel(true);
        }

        sentinels.values().stream()
                .map(s -> s.shutdownAsync())
                .forEach(f -> f.toCompletableFuture().join());

        super.shutdown(quietPeriod, timeout, unit);
    }

    private RedisURI applyNatMap(RedisURI address) {
        RedisURI result = cfg.getNatMapper().map(address);
        if (!result.equals(address)) {
            log.debug("nat mapped uri: {} to {}", address, result);
        }
        return result;
    }

}

