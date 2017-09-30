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
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.redisson.client.RedisConnectionException;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.misc.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.ScheduledFuture;

/**
 * DNS changes monitor.
 * 
 * @author Nikita Koksharov
 *
 */
public class DNSMonitor {
    
    private static final Logger log = LoggerFactory.getLogger(DNSMonitor.class);

    private ScheduledFuture<?> dnsMonitorFuture;
    
    private ConnectionManager connectionManager;

    private final Map<URI, InetAddress> masters = new HashMap<URI, InetAddress>();
    private final Map<URI, InetAddress> slaves = new HashMap<URI, InetAddress>();
    
    private long dnsMonitoringInterval;

    public DNSMonitor(ConnectionManager connectionManager, Set<URI> masterHosts, Set<URI> slaveHosts, long dnsMonitoringInterval) {
        for (URI host : masterHosts) {
            try {
                masters.put(host, InetAddress.getByName(host.getHost()));
            } catch (UnknownHostException e) {
                throw new RedisConnectionException("Unknown host: " + host.getHost(), e);
            }
        }
        for (URI host : slaveHosts) {
            try {
                slaves.put(host, InetAddress.getByName(host.getHost()));
            } catch (UnknownHostException e) {
                throw new RedisConnectionException("Unknown host: " + host.getHost(), e);
            }
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
        dnsMonitorFuture = GlobalEventExecutor.INSTANCE.schedule(new Runnable() {
            @Override
            public void run() {
                // As InetAddress.getByName call is blocking. Method should be run in dedicated thread 
                connectionManager.getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            for (Entry<URI, InetAddress> entry : masters.entrySet()) {
                                InetAddress master = entry.getValue();
                                InetAddress now = InetAddress.getByName(entry.getKey().getHost());
                                if (!now.getHostAddress().equals(master.getHostAddress())) {
                                    log.info("Detected DNS change. {} has changed from {} to {}", entry.getKey().getHost(), master.getHostAddress(), now.getHostAddress());
                                    for (MasterSlaveEntry entrySet : connectionManager.getEntrySet()) {
                                        if (entrySet.getClient().getAddr().getHostName().equals(entry.getKey().getHost())
                                                && entrySet.getClient().getAddr().getPort() == entry.getKey().getPort()) {
                                            entrySet.changeMaster(entry.getKey());
                                        }
                                    }
                                    masters.put(entry.getKey(), now);
                                    log.info("Master {} has been changed", entry.getKey().getHost());
                                }
                            }
                            
                            for (Entry<URI, InetAddress> entry : slaves.entrySet()) {
                                InetAddress slave = entry.getValue();
                                InetAddress updatedSlave = InetAddress.getByName(entry.getKey().getHost());
                                if (!updatedSlave.getHostAddress().equals(slave.getHostAddress())) {
                                    log.info("Detected DNS change. {} has changed from {} to {}", entry.getKey().getHost(), slave.getHostAddress(), updatedSlave.getHostAddress());
                                    for (MasterSlaveEntry masterSlaveEntry : connectionManager.getEntrySet()) {
                                        URI uri = URIBuilder.create("redis://" + slave.getHostAddress() + ":" + entry.getKey().getPort());
                                        if (masterSlaveEntry.slaveDown(uri, FreezeReason.MANAGER)) {
                                            masterSlaveEntry.slaveUp(entry.getKey(), FreezeReason.MANAGER);
                                        }
                                    }
                                    slaves.put(entry.getKey(), updatedSlave);
                                    log.info("Slave {} has been changed", entry.getKey().getHost());
                                }
                            }
                            
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                        } finally {
                            monitorDnsChange();
                        }
                    }
                });
            }

        }, dnsMonitoringInterval, TimeUnit.MILLISECONDS);
    }

    
}
