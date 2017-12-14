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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.api.RFuture;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.misc.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.resolver.AddressResolver;
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
    private final Map<URI, InetAddress> masters = new HashMap<URI, InetAddress>();
    private final Map<URI, InetAddress> slaves = new HashMap<URI, InetAddress>();
    
    private ScheduledFuture<?> dnsMonitorFuture;
    private long dnsMonitoringInterval;

    public DNSMonitor(ConnectionManager connectionManager, InetSocketAddress masterHost, Collection<URI> slaveHosts, long dnsMonitoringInterval, DnsAddressResolverGroup resolverGroup) {
        this.resolver = resolverGroup.getResolver(connectionManager.getGroup().next());
        
        URI uri = URIBuilder.create("redis://" + masterHost.getAddress().getHostAddress() + ":" + masterHost.getPort());
        masters.put(uri, masterHost.getAddress());
        
        for (URI host : slaveHosts) {
            Future<InetSocketAddress> resolveFuture = resolver.resolve(InetSocketAddress.createUnresolved(host.getHost(), 0));
            resolveFuture.syncUninterruptibly();
            slaves.put(host, resolveFuture.getNow().getAddress());
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
                final AtomicInteger counter = new AtomicInteger(masters.size() + slaves.size());
                for (final Entry<URI, InetAddress> entry : masters.entrySet()) {
                    Future<InetSocketAddress> resolveFuture = resolver.resolve(InetSocketAddress.createUnresolved(entry.getKey().getHost(), 0));
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
                            
                            InetAddress master = entry.getValue();
                            InetAddress now = future.get().getAddress();
                            if (!now.getHostAddress().equals(master.getHostAddress())) {
                                log.info("Detected DNS change. Master {} has changed ip from {} to {}", entry.getKey(), master.getHostAddress(), now.getHostAddress());
                                for (MasterSlaveEntry entrySet : connectionManager.getEntrySet()) {
                                    if (entrySet.getClient().getAddr().getHostName().equals(entry.getKey().getHost())
                                            && entrySet.getClient().getAddr().getPort() == entry.getKey().getPort()) {
                                        entrySet.changeMaster(entry.getKey());
                                        break;
                                    }
                                }
                                masters.put(entry.getKey(), now);
                            }
                        }
                    });
                }
                
                for (final Entry<URI, InetAddress> entry : slaves.entrySet()) {
                    Future<InetSocketAddress> resolveFuture = resolver.resolve(InetSocketAddress.createUnresolved(entry.getKey().getHost(), 0));
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
                            
                            InetAddress slave = entry.getValue();
                            final InetAddress updatedSlave = future.get().getAddress();
                            if (!updatedSlave.getHostAddress().equals(slave.getHostAddress())) {
                                log.info("Detected DNS change. Slave {} has changed ip from {} to {}", entry.getKey().getHost(), slave.getHostAddress(), updatedSlave.getHostAddress());
                                for (final MasterSlaveEntry masterSlaveEntry : connectionManager.getEntrySet()) {
                                    final URI uri = URIBuilder.create(slave.getHostAddress() + ":" + entry.getKey().getPort());

                                    if (masterSlaveEntry.hasSlave(uri)) {
                                        RFuture<Void> addFuture = masterSlaveEntry.addSlave(entry.getKey());
                                        addFuture.addListener(new FutureListener<Void>() {
                                            @Override
                                            public void operationComplete(Future<Void> future) throws Exception {
                                                if (!future.isSuccess()) {
                                                    log.error("Can't add slave: " + updatedSlave, future.cause());
                                                    return;
                                                }
                                                
                                                masterSlaveEntry.slaveDown(uri, FreezeReason.MANAGER);
                                            }
                                        });
                                        break;
                                    }
                                }
                                slaves.put(entry.getKey(), updatedSlave);
                            }
                        }
                    });
                }
            }

        }, dnsMonitoringInterval, TimeUnit.MILLISECONDS);
    }

    
}
