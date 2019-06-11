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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.misc.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
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
    private final Map<RedisURI, InetSocketAddress> masters = new HashMap<>();
    private final Map<RedisURI, InetSocketAddress> slaves = new HashMap<>();
    
    private ScheduledFuture<?> dnsMonitorFuture;
    private long dnsMonitoringInterval;

    public DNSMonitor(ConnectionManager connectionManager, RedisClient masterHost, Collection<RedisURI> slaveHosts, long dnsMonitoringInterval, AddressResolverGroup<InetSocketAddress> resolverGroup) {
        this.resolver = resolverGroup.getResolver(connectionManager.getGroup().next());
        
        masterHost.resolveAddr().syncUninterruptibly();
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

    private void monitorMasters(AtomicInteger counter) {
        for (Entry<RedisURI, InetSocketAddress> entry : masters.entrySet()) {
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
                            if (connectionManager instanceof SingleConnectionManager) {
                                log.error("Unable to find master entry for {}. Multiple IP bindings for single hostname supported only in Redisson PRO!", currentMasterAddr);
                            } else {
                                log.error("Unable to find master entry for {}", currentMasterAddr);
                            }
                            return;
                        }
                        masterSlaveEntry.changeMaster(newMasterAddr, entry.getKey());
                        masters.put(entry.getKey(), newMasterAddr);
                    }
                }
            });
        }
    }

    private void monitorSlaves(AtomicInteger counter) {
        for (Entry<RedisURI, InetSocketAddress> entry : slaves.entrySet()) {
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
                    
                    InetSocketAddress currentSlaveAddr = entry.getValue();
                    InetSocketAddress newSlaveAddr = future.getNow();
                    if (!newSlaveAddr.getAddress().equals(currentSlaveAddr.getAddress())) {
                        log.info("Detected DNS change. Slave {} has changed ip from {} to {}", 
                                entry.getKey().getHost(), currentSlaveAddr.getAddress().getHostAddress(), newSlaveAddr.getAddress().getHostAddress());
                        for (MasterSlaveEntry masterSlaveEntry : connectionManager.getEntrySet()) {
                            if (!masterSlaveEntry.hasSlave(currentSlaveAddr)) {
                                continue;
                            }
                            
                            if (masterSlaveEntry.hasSlave(newSlaveAddr)) {
                                masterSlaveEntry.slaveUp(newSlaveAddr, FreezeReason.MANAGER);
                                masterSlaveEntry.slaveDown(currentSlaveAddr, FreezeReason.MANAGER);
                            } else {
                                RFuture<Void> addFuture = masterSlaveEntry.addSlave(newSlaveAddr, entry.getKey());
                                addFuture.onComplete((res, e) -> {
                                    if (e != null) {
                                        log.error("Can't add slave: " + newSlaveAddr, e);
                                        return;
                                    }
                                    
                                    masterSlaveEntry.slaveDown(currentSlaveAddr, FreezeReason.MANAGER);
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
