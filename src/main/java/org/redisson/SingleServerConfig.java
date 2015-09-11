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
package org.redisson;

import java.net.URI;

import org.redisson.misc.URIBuilder;

public class SingleServerConfig extends BaseConfig<SingleServerConfig> {

    /**
     * Redis server address
     *
     */
    private URI address;

    /**
     * Redis subscription connection pool size
     *
     */
    private int subscriptionConnectionPoolSize = 25;

    /**
     * Redis connection pool size
     */
    private int connectionPoolSize = 100;

        
    /**
     * Should the server address be monitored for changes in DNS? Useful for 
     * AWS ElastiCache where the client is pointed at the endpoint for a replication group
     * which is a DNS alias to the current master node.<br>
     * <em>NB: applications must ensure the JVM DNS cache TTL is low enough to support this.</em> 
     * e.g., http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/java-dg-jvm-ttl.html
     */
    private boolean dnsMonitoring = false;

    /**
     * Interval in milliseconds to check DNS
     */
    private long dnsMonitoringInterval = 5000;
    
    SingleServerConfig() {
    }

    SingleServerConfig(SingleServerConfig config) {
        super(config);
        setAddress(config.getAddress());
        setConnectionPoolSize(config.getConnectionPoolSize());
        setSubscriptionConnectionPoolSize(config.getSubscriptionConnectionPoolSize());
        setDnsMonitoring(config.isDnsMonitoring());
        setDnsMonitoringInterval(config.getDnsMonitoringInterval());
    }

    /**
     * Redis connection pool size
     * Default is 100
     *
     * @param connectionPoolSize
     */
    public SingleServerConfig setConnectionPoolSize(int connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
        return this;
    }
    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    /**
     * Redis subscription-connection pool size limit
     * Default is 25
     *
     * @param connectionPoolSize
     * @return
     */
    public SingleServerConfig setSubscriptionConnectionPoolSize(int subscriptionConnectionPoolSize) {
        this.subscriptionConnectionPoolSize = subscriptionConnectionPoolSize;
        return this;
    }
    public int getSubscriptionConnectionPoolSize() {
        return subscriptionConnectionPoolSize;
    }

    /**
     * Set server address. Use follow format -- host:port
     *
     * @param address
     */
    public SingleServerConfig setAddress(String address) {
        this.address = URIBuilder.create(address);
        return this;
    }
    public URI getAddress() {
        return address;
    }
    void setAddress(URI address) {
        this.address = address;
    }

    /**
     * Monitoring of the endpoint address for DNS changes.
     * Default is false.
     * 
     * @param dnsMonitoring
     * @return
     */
    public SingleServerConfig setDnsMonitoring(boolean dnsMonitoring) {
        this.dnsMonitoring = dnsMonitoring;
        return this;
    }
    public boolean isDnsMonitoring() {
        return dnsMonitoring;
    }

    /**
     * Interval in milliseconds to check the endpoint DNS if {@link #isDnsMonitoring()} is true.
     * Default is 5000.
     * 
     * @param dnsMonitoringInterval
     * @return
     */
    public SingleServerConfig setDnsMonitoringInterval(long dnsMonitoringInterval) {
        this.dnsMonitoringInterval = dnsMonitoringInterval;
        return this;
    }
    public long getDnsMonitoringInterval() {
        return dnsMonitoringInterval;
    }
}
