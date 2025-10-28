/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import io.netty.util.NetUtil;
import io.netty.util.Timeout;
import io.netty.util.internal.StringUtil;
import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.*;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.decoder.RedisURIDecoder;
import org.redisson.config.*;
import org.redisson.misc.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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

    private volatile Timeout monitorFuture;
    private final Set<RedisURI> disconnectedSentinels = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private RedisStrictCommand<RedisURI> masterHostCommand;

    private boolean usePassword = false;
    private String scheme;
    private SentinelServersConfig cfg;

    SentinelConnectionManager(SentinelServersConfig cfg, Config configCopy) {
        super(cfg, configCopy);
        this.serviceManager.setNatMapper(cfg.getNatMapper());

        for (String address : cfg.getSentinelAddresses()) {
            RedisURI addr = new RedisURI(address);
            scheme = addr.getScheme();
            addr = applyNatMap(addr);
            if (NetUtil.createByteArrayFromIpAddressString(addr.getHost()) == null && !addr.getHost().equals("localhost")) {
                sentinelHosts.add(addr);
            }
        }

        masterHostCommand = new RedisStrictCommand<>("SENTINEL", "GET-MASTER-ADDR-BY-NAME",
                new RedisURIDecoder(scheme));
    }

    @Override
    public void doConnect(Function<RedisURI, String> hostnameMapper) {
        checkAuth(cfg);

        Map<RedisURI, String> uri2hostname = new HashMap<>();
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

                List<CompletableFuture<Void>> connectionFutures = new LinkedList<>();
                if (currentMaster.get() == null) {
                    RedisURI master = connection.sync(masterHostCommand, cfg.getMasterName());
                    if (master == null) {
                        throw new RedisConnectionException("Master node is undefined! SENTINEL GET-MASTER-ADDR-BY-NAME command returns empty result!");
                    }

                    InetSocketAddress masterHost = resolveIP(master.getHost(), String.valueOf(master.getPort())).join();
                    RedisURI masterUri = toURI(masterHost);
                    if (!master.isIP()) {
                        uri2hostname.put(masterUri, master.getHost());
                    }
                    this.config.setMasterAddress(masterUri.toString());
                    currentMaster.set(masterUri);
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

                        InetSocketAddress slaveAddr = resolveIP(host, port).join();
                        RedisURI uri = toURI(slaveAddr);
                        if (isHostname(host)) {
                            uri2hostname.put(uri, host);
                        }

                        log.debug("slave {} state: {}", slaveAddr, map);

                        if (isSlaveDown(flags, masterLinkStatus)) {
                            log.warn("slave: {} is down", slaveAddr);
                        } else {
                            this.config.addSlaveAddress(uri.toString());
                            log.info("slave: {} added", slaveAddr);
                        }
                    }

                    if (cfg.isSentinelsDiscovery()) {
                        List<Map<String, String>> sentinelSentinels = connection.sync(StringCodec.INSTANCE, RedisCommands.SENTINEL_SENTINELS, cfg.getMasterName());
                        for (Map<String, String> map : sentinelSentinels) {
                            if (map.isEmpty()) {
                                continue;
                            }

                            String ip = map.get("ip");
                            String port = map.get("port");

                            InetSocketAddress sentinelAddr = resolveIP(ip, port).join();
                            CompletionStage<Void> future = registerSentinel(sentinelAddr);
                            connectionFutures.add(future.toCompletableFuture());
                        }
                    }
                }

                CompletionStage<Void> f = registerSentinel(connection.getRedisClient().getAddr());
                connectionFutures.add(f.toCompletableFuture());

                CompletableFuture<Void> future = CompletableFuture.allOf(connectionFutures.toArray(new CompletableFuture[0]));
                try {
                    future.get(this.config.getConnectTimeout(), TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    // skip
                }
                break;
            } catch (RedisConnectionException e) {
                internalShutdown();
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
                internalShutdown();
                throw new RedisConnectionException("SENTINEL SENTINELS command returns empty result or connection can't be established to some of them! Set checkSentinelsList = false to avoid this check.", lastException);
            } else if (sentinels.size() < 2) {
                internalShutdown();
                throw new RedisConnectionException("SENTINEL SENTINELS command returns less than 2 nodes or connection can't be established to some of them! At least two sentinels should be defined in Redis configuration. Set checkSentinelsList = false to avoid this check.", lastException);
            }
        }
        
        if (currentMaster.get() == null) {
            internalShutdown();
            throw new RedisConnectionException("Can't connect to servers!", lastException);
        }
        if (this.config.getReadMode() != ReadMode.MASTER && this.config.getSlaveAddresses().isEmpty()) {
            log.warn("ReadMode = {}, but slave nodes are not found!", this.config.getReadMode());
        }

        super.doConnect(uri2hostname::get);

        scheduleChangeCheck(cfg, null, null);
    }

    private static boolean isHostname(String host) {
        return NetUtil.createByteArrayFromIpAddressString(host) == null;
    }

    private void checkAuth(SentinelServersConfig cfg) {
        if (serviceManager.getCfg().getPassword() == null && cfg.getPassword() == null) {
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

        internalShutdown();
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
        monitorFuture = serviceManager.newTimeout(t -> {
            CompletableFuture<Void> f = performSentinelDNSCheck();
            f.whenComplete((r, e) -> scheduleSentinelDNSCheck());
        }, config.getDnsMonitoringInterval(), TimeUnit.MILLISECONDS);
    }

    private CompletableFuture<Void> performSentinelDNSCheck() {
        List<CompletableFuture<List<RedisURI>>> futures = new ArrayList<>();
        for (RedisURI host : sentinelHosts) {
            CompletableFuture<List<RedisURI>> allNodes = serviceManager.resolveAll(host);
            CompletableFuture<List<RedisURI>> f = allNodes.whenComplete((nodes, ex) -> {
                if (ex != null) {
                    log.error("Unable to resolve {}", host.getHost(), ex);
                    return;
                }

                nodes.stream()
                        .filter(uri -> {
                            return !sentinels.containsKey(uri) && !disconnectedSentinels.contains(uri);
                        })
                        .forEach(uri -> {
                            try {
                                byte[] addr = NetUtil.createByteArrayFromIpAddressString(uri.getHost());
                                InetSocketAddress address = new InetSocketAddress(InetAddress.getByAddress(host.getHost(), addr), uri.getPort());
                                registerSentinel(address);
                            } catch (UnknownHostException e) {
                                log.error(e.getMessage(), e);
                            }
                        });
            });
            futures.add(f);
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    private void scheduleChangeCheck(SentinelServersConfig cfg, Iterator<RedisClient> iterator, AtomicReference<Throwable> lastException) {
        AtomicReference<Throwable> exceptionReference = Optional.ofNullable(lastException)
                                                                .orElseGet(() -> new AtomicReference<>());
        monitorFuture = serviceManager.newTimeout(t -> {
            Iterator<RedisClient> iter = Optional.ofNullable(iterator)
                                                    .orElseGet(() -> {
                                                        // Shuffle the list so all clients don't prefer the same sentinel
                                                        List<RedisClient> clients = new ArrayList<>(sentinels.values());
                                                        Collections.shuffle(clients);
                                                        return clients.iterator();
                                                    });

            checkState(cfg, iter, exceptionReference);
        }, cfg.getScanInterval(), TimeUnit.MILLISECONDS);
    }

    private void checkState(SentinelServersConfig cfg, Iterator<RedisClient> iterator, AtomicReference<Throwable> lastException) {
        if (!iterator.hasNext()) {
            if (lastException.get() != null) {
                log.error("Can't update cluster state. A new attempt will be made.", lastException.getAndSet(null));
            }
            disconnectedSentinels.clear();
            CompletableFuture<Void> f = performSentinelDNSCheck();
            f.whenComplete((r, e) -> scheduleChangeCheck(cfg, null, null));
            return;
        }
        if (serviceManager.isShuttingDown()) {
            return;
        }

        RedisClient client = iterator.next();
        RedisURI addr = toURI(client.getAddr());
        String hostname = null;
        if (isHostname(client.getAddr().getHostName())) {
            hostname = client.getAddr().getHostName();
        }
        CompletionStage<RedisConnection> connectionFuture = connectToNode(NodeType.SENTINEL, cfg, addr, hostname);
        connectionFuture.whenComplete((connection, e) -> {
            if (e != null) {
                if (!lastException.compareAndSet(null, e)) {
                    lastException.get().addSuppressed(e);
                }
                checkState(cfg, iterator, lastException);
                return;
            }

            updateState(cfg, connection, iterator, lastException);
        });

    }

    private void updateState(SentinelServersConfig cfg, RedisConnection connection, Iterator<RedisClient> iterator,
                             AtomicReference<Throwable> lastException) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        CompletionStage<?> f = checkMasterChange(cfg, connection);

        if (!config.isSlaveNotUsed()) {
            f = f.thenCompose(r -> checkSlavesChange(cfg, connection));
        }
        futures.add(f.toCompletableFuture());

        CompletionStage<Void> sentinelsFuture = checkSentinelsChange(cfg, connection);
        futures.add(sentinelsFuture.toCompletableFuture());

        CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        future.whenComplete((r, e) -> {
            if (e != null) {
                if (!lastException.compareAndSet(null, e)) {
                    lastException.get().addSuppressed(e);
                }
                scheduleChangeCheck(cfg, iterator, lastException);
                return;
            }

            scheduleChangeCheck(cfg, null, null);
        });
    }

    private CompletionStage<Void> checkSentinelsChange(SentinelServersConfig cfg, RedisConnection connection) {
        if (!cfg.isSentinelsDiscovery()) {
            return CompletableFuture.completedFuture(null);
        }

        RFuture<List<Map<String, String>>> sentinelsFuture = connection.async(1, cfg.getRetryDelay(), cfg.getTimeout(),
                                                                                StringCodec.INSTANCE, RedisCommands.SENTINEL_SENTINELS, cfg.getMasterName());
        return sentinelsFuture.thenCompose(list -> {
            if (list.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            List<CompletableFuture<InetSocketAddress>> newUris = list.stream().filter(m -> {
                String flags = m.getOrDefault("flags", "");
                String masterLinkStatus = m.getOrDefault("master-link-status", "");
                if (!m.isEmpty() && !isSlaveDown(flags, masterLinkStatus)) {
                    return true;
                }
                return false;
            }).map(m -> {
                String ip = m.get("ip");
                String port = m.get("port");
                CompletionStage<InetSocketAddress> f = resolveIP(ip, port);
                return f.exceptionally(ex -> {
                    log.error("unable to resolve hostname", ex);
                    return null;
                }).toCompletableFuture();
            }).collect(Collectors.toList());

            CompletableFuture<Void> futures = CompletableFuture.allOf(newUris.toArray(new CompletableFuture[0]));
            return futures.whenComplete((r, ex) -> {
                List<InetSocketAddress> uris = newUris.stream().map(u -> {
                    try {
                        return u.getNow(null);
                    } catch (Exception exc) {
                        return null;
                    }
                }).filter(u -> u != null).collect(Collectors.toList());

                InetSocketAddress addr = connection.getRedisClient().getAddr();
                uris.add(addr);

                updateSentinels(uris);
            });
        });
    }

    private CompletionStage<Void> checkSlavesChange(SentinelServersConfig cfg, RedisConnection connection) {
        RFuture<List<Map<String, String>>> slavesFuture = connection.async(1, cfg.getRetryDelay(), cfg.getTimeout(),
                                                                            StringCodec.INSTANCE, RedisCommands.SENTINEL_SLAVES, cfg.getMasterName());
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

                CompletableFuture<InetSocketAddress> slaveAddrFuture = resolveIP(host, port);
                CompletableFuture<InetSocketAddress> masterAddrFuture;
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
                            InetSocketAddress slaveAddr = slaveAddrFuture.getNow(null);
                            InetSocketAddress masterAddr = masterAddrFuture.getNow(null);
                            if (flags.contains("promoted")) {
                                return CompletableFuture.completedFuture(res);
                            }
                            if (isSlaveDown(flags, masterLinkStatus)) {
                                slaveDown(slaveAddr);
                                return CompletableFuture.completedFuture(res);
                            }
                            if ("?".equals(masterHost) || !isUseSameMaster(slaveAddr, masterAddr)) {
                                return CompletableFuture.completedFuture(res);
                            }

                            RedisURI uri = toURI(slaveAddr);
                            currentSlaves.add(uri);
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
                        .filter(a -> {
                            RedisURI uri = toURI(a);
                            return !currentSlaves.contains(uri) && !uri.equals(currentMaster.get());
                        })
                        .forEach(a -> slaveDown(a));
            });
        });
    }

    private CompletionStage<RedisClient> checkMasterChange(SentinelServersConfig cfg, RedisConnection connection) {
        RFuture<RedisURI> masterFuture = connection.async(1, cfg.getRetryDelay(), cfg.getTimeout(),
                                                            StringCodec.INSTANCE, masterHostCommand, cfg.getMasterName());
        return masterFuture
                .thenCompose(u -> resolveIP(u.getHost(), "" + u.getPort()))
                .thenApply(this::toURI)
                .thenCompose(newMaster -> {
                    RedisURI current = currentMaster.get();
                    if (!newMaster.equals(current)
                            && currentMaster.compareAndSet(current, newMaster)) {
                        RedisURI host = newMaster;
                        if (newMaster.isSsl()) {
                            RedisURI h = masterFuture.toCompletableFuture().join();
                            if (!h.isIP()) {
                                host = new RedisURI(scheme, h.getHost(), h.getPort());
                            }
                        }
                        CompletableFuture<RedisClient> changeFuture = changeMaster(singleSlotRange.getStartSlot(), host);
                        return changeFuture.exceptionally(ex -> {
                            currentMaster.compareAndSet(newMaster, current);
                            return null;
                        });
                    }
                    return CompletableFuture.completedFuture(null);
                });
    }

    private void updateSentinels(Collection<InetSocketAddress> newAddrs) {
        newAddrs.stream()
                .filter(addr -> {
                    RedisURI uri = toURI(addr);
                    return !sentinels.containsKey(uri);
                })
                .forEach(addr -> {
                    RedisURI uri = toURI(addr);
                    disconnectedSentinels.remove(uri);
                    registerSentinel(addr);
                });

        sentinels.keySet().stream()
                .filter(uri -> {
                    for (InetSocketAddress addr : newAddrs) {
                        if (uri.equals(addr)) {
                            return false;
                        }
                    }
                    return true;
                })
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

    private CompletionStage<Void> registerSentinel(InetSocketAddress addr) {
        RedisURI uri = toURI(addr);
        RedisClient sentinel = sentinels.get(uri);
        if (sentinel != null) {
            return CompletableFuture.completedFuture(null);
        }

        RedisURI hostname = serviceManager.toURI(scheme, addr.getAddress().getHostName(), "" + addr.getPort());
        RedisClient client = createClient(NodeType.SENTINEL, addr, hostname, null);
        CompletableFuture<InetSocketAddress> future = client.resolveAddr();
        return future.thenCompose(res -> {
            RedisURI ipAddr = toURI(res);
            RedisClient s = sentinels.get(ipAddr);
            if (s != null) {
                return CompletableFuture.completedFuture(null);
            }

            CompletionStage<RedisConnection> f = client.connectAsync();
            return f.handle((resp, e) -> {
                if (e != null) {
                    log.error(e.getMessage(), e);
                    throw new CompletionException(e);
                }
                if (sentinels.putIfAbsent(ipAddr, client) == null) {
                    log.info("sentinel: {} added", ipAddr);
                } else {
                    client.shutdownAsync();
                }
                return null;
            });
        });
    }

    private CompletableFuture<InetSocketAddress> resolveIP(String host, String port) {
        RedisURI uri = serviceManager.toURI(scheme, host, port);
        return serviceManager.resolve(uri);
    }

    private RedisURI toURI(InetSocketAddress addr) {
        return serviceManager.toURI(scheme, addr.getAddress().getHostAddress(), "" + addr.getPort());
    }

    private CompletableFuture<Void> addSlave(InetSocketAddress addr) {
        if (config.isSlaveNotUsed()) {
            log.info("slave: {} is up", addr);
            return CompletableFuture.completedFuture(null);
        }

        // to avoid addition twice
        MasterSlaveEntry entry = getEntry(singleSlotRange.getStartSlot());
        if (!entry.hasSlave(addr)) {
            RedisURI uri = serviceManager.toURI(scheme, addr.getHostName(), "" + addr.getPort());
            CompletableFuture<Void> future = entry.addSlave(addr, uri);
            return future.thenApply(res -> {
                log.info("slave: {} added", addr);
                return null;
            });
        }

        CompletableFuture<Boolean> f = entry.slaveUpNoMasterExclusionAsync(addr);
        return f.thenApply(e -> {
                            if (e) {
                                log.info("slave: {} is up", addr);
                                entry.excludeMasterFromSlaves(addr);
                            }
                            return null;
                        });
    }

    private void slaveDown(InetSocketAddress addr) {
        if (config.isSlaveNotUsed()) {
            log.warn("slave: {} is down", addr);
        } else {
            MasterSlaveEntry entry = getEntry(singleSlotRange.getStartSlot());
            if (entry.slaveDown(addr)) {
                log.warn("slave: {} is down", addr);
            }
        }
    }

    private boolean isSlaveDown(String flags, String masterLinkStatus) {
        boolean baseStatus = flags.contains("s_down") || flags.contains("disconnected");
        if (cfg.isCheckSlaveStatusWithSyncing() && !StringUtil.isNullOrEmpty(masterLinkStatus)) {
            return baseStatus || masterLinkStatus.contains("err");
        }
        return baseStatus;
    }

    private boolean isUseSameMaster(InetSocketAddress slaveAddr, InetSocketAddress slaveMasterAddr) {
        RedisURI master = currentMaster.get();
        if (!master.equals(slaveMasterAddr) && !master.equals(slaveAddr)) {
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
            monitorFuture.cancel();
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

