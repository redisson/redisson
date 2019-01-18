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
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;

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
    private final Map<URI, InetSocketAddress> masters = new HashMap<URI, InetSocketAddress>();
    private final Map<URI, InetSocketAddress> slaves = new HashMap<URI, InetSocketAddress>();
    
    private ScheduledFuture<?> dnsMonitorFuture;
    private long dnsMonitoringInterval;

    public DNSMonitor(ConnectionManager connectionManager, RedisClient masterHost, Collection<URI> slaveHosts, long dnsMonitoringInterval, AddressResolverGroup<InetSocketAddress> resolverGroup) {
        this.resolver = resolverGroup.getResolver(connectionManager.getGroup().next());
        
        masterHost.resolveAddr().syncUninterruptibly();
        masters.put(masterHost.getConfig().getAddress(), masterHost.getAddr());
        
        for (URI host : slaveHosts) {
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
            dnsMonitorFuture.cancel(true);
        }
    }
    
    private void monitorDnsChange() {
        dnsMonitorFuture = connectionManager.getGroup().schedule(new Runnable() {
            @Override
            public void run() {
                if (connectionManager.isShuttingDown()) {
                    return;
                }
                
                AtomicInteger counter = new AtomicInteger(masters.size() + slaves.size());
                monitorMasters(counter);
                monitorSlaves(counter);
            }

        }, dnsMonitoringInterval, TimeUnit.MILLISECONDS);
    }

    private void monitorMasters(final AtomicInteger counter) {
        for (final Entry<URI, InetSocketAddress> entry : masters.entrySet()) {
            Future<InetSocketAddress> resolveFuture = resolver.resolve(InetSocketAddress.createUnresolved(entry.getKey().getHost(), entry.getKey().getPort()));
            resolveFuture.addListener(new FutureListener<InetSocketAddress>() {
                @Override
                public void operationComplete(Future<InetSocketAddress> future) throws Exception {
                    if (counter.decrementAndGet() == 0) {
                        monitorDnsChange();
                    }

                    if (!future.isSuccess()) {
                        log.error("Unable to resolve " + entry.getKey().getHost(), future.cause());
                        return;
                    }
                    
                    InetSocketAddress currentMasterAddr = entry.getValue();
                    InetSocketAddress newMasterAddr = future.getNow();
                    if (!newMasterAddr.getAddress().equals(currentMasterAddr.getAddress())) {
                        log.info("Detected DNS change. Master {} has changed ip from {} to {}", 
                                entry.getKey(), currentMasterAddr.getAddress().getHostAddress(), newMasterAddr.getAddress().getHostAddress());
                        MasterSlaveEntry masterSlaveEntry = connectionManager.getEntry(currentMasterAddr);
                        if (masterSlaveEntry == null) {
                            log.error("Unable to find master entry for {}", currentMasterAddr);
                            return;
                        }
                        masterSlaveEntry.changeMaster(newMasterAddr, entry.getKey());
                        masters.put(entry.getKey(), newMasterAddr);
                    }
                }
            });
        }
    }

    private void monitorSlaves(final AtomicInteger counter) {
        for (final Entry<URI, InetSocketAddress> entry : slaves.entrySet()) {
            Future<InetSocketAddress> resolveFuture = resolver.resolve(InetSocketAddress.createUnresolved(entry.getKey().getHost(), entry.getKey().getPort()));
            resolveFuture.addListener(new FutureListener<InetSocketAddress>() {
                @Override
                public void operationComplete(Future<InetSocketAddress> future) throws Exception {
                    if (counter.decrementAndGet() == 0) {
                        monitorDnsChange();
                    }

                    if (!future.isSuccess()) {
                        log.error("Unable to resolve " + entry.getKey().getHost(), future.cause());
                        return;
                    }
                    
                    final InetSocketAddress currentSlaveAddr = entry.getValue();
                    final InetSocketAddress newSlaveAddr = future.getNow();
                    if (!newSlaveAddr.getAddress().equals(currentSlaveAddr.getAddress())) {
                        log.info("Detected DNS change. Slave {} has changed ip from {} to {}", 
                                entry.getKey().getHost(), currentSlaveAddr.getAddress().getHostAddress(), newSlaveAddr.getAddress().getHostAddress());
                        for (final MasterSlaveEntry masterSlaveEntry : connectionManager.getEntrySet()) {
                            if (!masterSlaveEntry.hasSlave(currentSlaveAddr)) {
                                continue;
                            }
                            
                            if (masterSlaveEntry.hasSlave(newSlaveAddr)) {
                                masterSlaveEntry.slaveUp(newSlaveAddr, FreezeReason.MANAGER);
                                masterSlaveEntry.slaveDown(currentSlaveAddr, FreezeReason.MANAGER);
                            } else {
                                RFuture<Void> addFuture = masterSlaveEntry.addSlave(newSlaveAddr, entry.getKey());
                                addFuture.addListener(new FutureListener<Void>() {
                                    @Override
                                    public void operationComplete(Future<Void> future) throws Exception {
                                        if (!future.isSuccess()) {
                                            log.error("Can't add slave: " + newSlaveAddr, future.cause());
                                            return;
                                        }
                                        
                                        masterSlaveEntry.slaveDown(currentSlaveAddr, FreezeReason.MANAGER);
                                    }
                                });
                            }
                            break;
                        }
                        slaves.put(entry.getKey(), newSlaveAddr);
                    }
                }
            });
        }
    }

    
}
