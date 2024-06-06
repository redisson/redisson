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

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.NetUtil;
import io.netty.util.Timeout;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.redisson.client.RedisClient;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.misc.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * DNS changes monitor.
 * 
 * @author Nikita Koksharov
 *
 */
public class DNSMonitor {
    
    private static final Logger log = LoggerFactory.getLogger(DNSMonitor.class);

    private final AddressResolver<InetSocketAddress> resolver;
    private final ConnectionManager connectionManager;
    private final Map<RedisURI, InetSocketAddress> masters = new HashMap<>();
    private final Map<RedisURI, InetSocketAddress> slaves = new HashMap<>();
    
    private volatile Timeout dnsMonitorFuture;
    private final long dnsMonitoringInterval;

    private boolean printed;

    public DNSMonitor(ConnectionManager connectionManager, RedisClient masterHost, Collection<RedisURI> slaveHosts, long dnsMonitoringInterval, AddressResolverGroup<InetSocketAddress> resolverGroup) {
        this.resolver = resolverGroup.getResolver(connectionManager.getServiceManager().getGroup().next());
        
        masterHost.resolveAddr().join();
        masters.put(masterHost.getConfig().getAddress(), masterHost.getAddr());
        
        for (RedisURI host : slaveHosts) {
            Future<InetSocketAddress> resolveFuture = resolver.resolve(InetSocketAddress.createUnresolved(host.getHost(), host.getPort()));
            resolveFuture.syncUninterruptibly();
            slaves.put(host, resolveFuture.getNow());
        }
        this.connectionManager = connectionManager;
        this.dnsMonitoringInterval = dnsMonitoringInterval;
    }
    
    public void start() {
        monitorDnsChange();
        log.debug("DNS monitoring enabled; Current masters: {}, slaves: {}", masters, slaves);
    }
    
    public void stop() {
        if (dnsMonitorFuture != null) {
            dnsMonitorFuture.cancel();
        }
    }
    
    private void monitorDnsChange() {
        dnsMonitorFuture = connectionManager.getServiceManager().newTimeout(t -> {
            if (connectionManager.getServiceManager().isShuttingDown()) {
                return;
            }

            CompletableFuture<Void> mf = monitorMasters();
            CompletableFuture<Void> sf = monitorSlaves();
            CompletableFuture.allOf(mf, sf)
                    .whenComplete((r, e) -> monitorDnsChange());
        }, dnsMonitoringInterval, TimeUnit.MILLISECONDS);
    }

    private CompletableFuture<Void> monitorMasters() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Entry<RedisURI, InetSocketAddress> entry : masters.entrySet()) {
            CompletableFuture<Void> promise = new CompletableFuture<>();
            futures.add(promise);

            CompletableFuture<List<RedisURI>> ipsFuture = connectionManager.getServiceManager().resolveAll(entry.getKey());
            ipsFuture.whenComplete((addresses, ex) -> {
                if (ex != null) {
                    log.error("Unable to resolve {}", entry.getKey().getHost(), ex);
                    promise.complete(null);
                    return;
                }

                if (addresses.size() > 1) {
                    if (!printed) {
                        log.info("Try Redisson PRO with Proxy mode to use all ip addresses: {}", addresses);
                        printed = true;
                    }
                }

                for (RedisURI address : addresses) {
                    if (address.equals(entry.getValue())) {
                        log.debug("{} resolved to {}", entry.getKey().getHost(), addresses);
                        promise.complete(null);
                        return;
                    }
                }

                int index = 0;
                if (addresses.size() > 1) {
                    addresses.sort(Comparator.comparing(RedisURI::getHost));
                }

                RedisURI address = addresses.get(index);

                log.debug("{} resolved to {} and {} selected", entry.getKey().getHost(), addresses, address);



                try {
                    InetSocketAddress currentMasterAddr = entry.getValue();
                    byte[] addr = NetUtil.createByteArrayFromIpAddressString(address.getHost());
                    InetSocketAddress newMasterAddr = new InetSocketAddress(InetAddress.getByAddress(entry.getKey().getHost(), addr), address.getPort());
                    if (!address.equals(currentMasterAddr)) {
                        log.info("Detected DNS change. Master {} has changed ip from {} to {}",
                                entry.getKey(), currentMasterAddr.getAddress().getHostAddress(),
                                newMasterAddr.getAddress().getHostAddress());

                        MasterSlaveEntry masterSlaveEntry = connectionManager.getEntry(currentMasterAddr);
                        if (masterSlaveEntry == null) {
                            log.error("Unable to find entry for current master {}", currentMasterAddr);
                            promise.complete(null);
                            return;
                        }

                        CompletableFuture<RedisClient> changeFuture = masterSlaveEntry.changeMaster(newMasterAddr, entry.getKey());
                        changeFuture.whenComplete((r, e) -> {
                            promise.complete(null);

                            if (e == null) {
                                masters.put(entry.getKey(), newMasterAddr);
                            }
                        });
                    } else {
                        promise.complete(null);
                    }
                } catch (UnknownHostException e) {
                    log.error(e.getMessage(), e);
                    promise.complete(null);
                }
            });
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private CompletableFuture<Void> monitorSlaves() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Entry<RedisURI, InetSocketAddress> entry : slaves.entrySet()) {
            CompletableFuture<Void> promise = new CompletableFuture<>();
            futures.add(promise);
            log.debug("Request sent to resolve ip address for slave host: {}", entry.getKey().getHost());

            Future<InetSocketAddress> resolveFuture = resolver.resolve(InetSocketAddress.createUnresolved(entry.getKey().getHost(), entry.getKey().getPort()));
            resolveFuture.addListener((FutureListener<InetSocketAddress>) future -> {
                if (!future.isSuccess()) {
                    log.error("Unable to resolve {}", entry.getKey().getHost(), future.cause());
                    promise.complete(null);
                    return;
                }

                log.debug("Resolved ip: {} for slave host: {}", future.getNow().getAddress(), entry.getKey().getHost());

                InetSocketAddress currentSlaveAddr = entry.getValue();
                InetSocketAddress newSlaveAddr = future.getNow();
                if (!newSlaveAddr.getAddress().equals(currentSlaveAddr.getAddress())) {
                    log.info("Detected DNS change. Slave {} has changed ip from {} to {}",
                            entry.getKey().getHost(), currentSlaveAddr.getAddress().getHostAddress(), newSlaveAddr.getAddress().getHostAddress());
                    boolean slaveFound = false;
                    for (MasterSlaveEntry masterSlaveEntry : connectionManager.getEntrySet()) {
                        if (!masterSlaveEntry.hasSlave(currentSlaveAddr)) {
                            continue;
                        }

                        slaveFound = true;
                        if (masterSlaveEntry.hasSlave(newSlaveAddr)) {
                            CompletableFuture<Boolean> slaveUpFuture = masterSlaveEntry.slaveUpAsync(newSlaveAddr, FreezeReason.MANAGER);
                            slaveUpFuture.whenComplete((r, e) -> {
                                if (e != null) {
                                    promise.complete(null);
                                    return;
                                }
                                if (r) {
                                    slaves.put(entry.getKey(), newSlaveAddr);
                                    masterSlaveEntry.slaveDown(currentSlaveAddr, FreezeReason.MANAGER);
                                }
                                promise.complete(null);
                            });
                        } else {
                            CompletableFuture<Void> addFuture = masterSlaveEntry.addSlave(newSlaveAddr, entry.getKey());
                            addFuture.whenComplete((res, e) -> {
                                if (e != null) {
                                    log.error("Can't add slave: {}", newSlaveAddr, e);
                                    promise.complete(null);
                                    return;
                                }

                                slaves.put(entry.getKey(), newSlaveAddr);
                                masterSlaveEntry.slaveDown(currentSlaveAddr, FreezeReason.MANAGER);
                                promise.complete(null);
                            });
                        }
                        break;
                    }
                    if (!slaveFound) {
                        promise.complete(null);
                    }
                } else {
                    promise.complete(null);
                }
            });
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

}
