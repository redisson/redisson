/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.config;

import org.redisson.client.FailedConnectionDetector;
import org.redisson.client.FailedNodeDetector;
import org.redisson.connection.balancer.LoadBalancer;
import org.redisson.connection.balancer.RoundRobinLoadBalancer;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> config type
 */
public class BaseMasterSlaveServersConfig<T extends BaseMasterSlaveServersConfig<T>> extends BaseConfig<T> {

    /**
     * Connection load balancer for multiple Redis slave servers
     */
    private LoadBalancer loadBalancer = new RoundRobinLoadBalancer();

    /**
     * Redis 'slave' node minimum idle connection amount for <b>each</b> slave node
     */
    private int slaveConnectionMinimumIdleSize = 24;

    /**
     * Redis 'slave' node maximum connection pool size for <b>each</b> slave node
     */
    private int slaveConnectionPoolSize = 64;

    private int failedSlaveReconnectionInterval = 3000;

    @Deprecated
    private int failedSlaveCheckInterval = 180000;
    
    /**
     * Redis 'master' node minimum idle connection amount for <b>each</b> slave node
     */
    private int masterConnectionMinimumIdleSize = 24;

    /**
     * Redis 'master' node maximum connection pool size
     */
    private int masterConnectionPoolSize = 64;

    private ReadMode readMode = ReadMode.SLAVE;
    
    private SubscriptionMode subscriptionMode = SubscriptionMode.MASTER;
    
    /**
     * Redis 'slave' node minimum idle subscription (pub/sub) connection amount for <b>each</b> slave node
     */
    private int subscriptionConnectionMinimumIdleSize = 1;

    /**
     * Redis 'slave' node maximum subscription (pub/sub) connection pool size for <b>each</b> slave node
     */
    private int subscriptionConnectionPoolSize = 50;

    private long dnsMonitoringInterval = 5000;

    private FailedNodeDetector failedSlaveNodeDetector = new FailedConnectionDetector();
    
    public BaseMasterSlaveServersConfig() {
    }

    BaseMasterSlaveServersConfig(T config) {
        super(config);
        setLoadBalancer(config.getLoadBalancer());
        setMasterConnectionPoolSize(config.getMasterConnectionPoolSize());
        setSlaveConnectionPoolSize(config.getSlaveConnectionPoolSize());
        setSubscriptionConnectionPoolSize(config.getSubscriptionConnectionPoolSize());
        setMasterConnectionMinimumIdleSize(config.getMasterConnectionMinimumIdleSize());
        setSlaveConnectionMinimumIdleSize(config.getSlaveConnectionMinimumIdleSize());
        setSubscriptionConnectionMinimumIdleSize(config.getSubscriptionConnectionMinimumIdleSize());
        setReadMode(config.getReadMode());
        setSubscriptionMode(config.getSubscriptionMode());
        setDnsMonitoringInterval(config.getDnsMonitoringInterval());
        setFailedSlaveReconnectionInterval(config.getFailedSlaveReconnectionInterval());
        setFailedSlaveNodeDetector(config.getFailedSlaveNodeDetector());
    }

    /**
     * Redis 'slave' servers connection pool size for <b>each</b> slave node.
     * <p>
     * Default is <code>64</code>
     * <p>
     * @see #setSlaveConnectionMinimumIdleSize(int)
     *
     * @param slaveConnectionPoolSize - size of pool
     * @return config
     */
    public T setSlaveConnectionPoolSize(int slaveConnectionPoolSize) {
        this.slaveConnectionPoolSize = slaveConnectionPoolSize;
        return (T) this;
    }
    public int getSlaveConnectionPoolSize() {
        return slaveConnectionPoolSize;
    }
    
    /**
     * When the retry interval <code>failedSlavesReconnectionTimeout<code/>
     * reached Redisson tries to connect to failed Redis node reported by <code>failedSlaveNodeDetector</code>.
     * <p>
     * On every such timeout event Redisson tries
     * to connect to failed Redis server.
     * <p>
     * Default is 3000
     *
     * @param failedSlavesReconnectionTimeout - retry timeout in milliseconds
     * @return config
     */

    public T setFailedSlaveReconnectionInterval(int failedSlavesReconnectionTimeout) {
        this.failedSlaveReconnectionInterval = failedSlavesReconnectionTimeout;
        return (T) this;
    }

    public int getFailedSlaveReconnectionInterval() {
        return failedSlaveReconnectionInterval;
    }

    
    /**
     * Use {@link #setFailedSlaveNodeDetector(FailedNodeDetector)} instead.
     *
     * @param slaveFailsInterval - time interval in milliseconds
     * @return config
     */
    @Deprecated
    public T setFailedSlaveCheckInterval(int slaveFailsInterval) {
        log.error("failedSlaveCheckInterval setting is deprecated and will be removed in future releases. Use failedSlaveNodeDetector setting instead");
        this.failedSlaveCheckInterval = slaveFailsInterval;
        this.failedSlaveNodeDetector = new FailedConnectionDetector(slaveFailsInterval);
        return (T) this;
    }
    @Deprecated
    public int getFailedSlaveCheckInterval() {
        return failedSlaveCheckInterval;
    }

    /**
     * Redis 'master' server connection pool size.
     * <p>
     * Default is <code>64</code>
     *
     * @see #setMasterConnectionMinimumIdleSize(int)
     * 
     * @param masterConnectionPoolSize - pool size
     * @return config
     *
     */
    public T setMasterConnectionPoolSize(int masterConnectionPoolSize) {
        this.masterConnectionPoolSize = masterConnectionPoolSize;
        return (T) this;
    }
    public int getMasterConnectionPoolSize() {
        return masterConnectionPoolSize;
    }

    /**
     * Connection load balancer to multiple Redis slave servers.
     * Uses Round-robin algorithm by default
     *
     * @param loadBalancer object
     * @return config
     *
     * @see org.redisson.connection.balancer.RandomLoadBalancer
     * @see org.redisson.connection.balancer.RoundRobinLoadBalancer
     * @see org.redisson.connection.balancer.WeightedRoundRobinBalancer
     */
    public T setLoadBalancer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        return (T) this;
    }
    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    /**
     * Maximum connection pool size for subscription (pub/sub) channels
     * <p>
     * Default is <code>50</code>
     * <p>
     * @see #setSubscriptionConnectionMinimumIdleSize(int)
     * 
     * @param subscriptionConnectionPoolSize - pool size
     * @return config
     */
    public T setSubscriptionConnectionPoolSize(int subscriptionConnectionPoolSize) {
        this.subscriptionConnectionPoolSize = subscriptionConnectionPoolSize;
        return (T) this;
    }
    public int getSubscriptionConnectionPoolSize() {
        return subscriptionConnectionPoolSize;
    }

    
    /**
     * Minimum idle connection pool size for subscription (pub/sub) channels
     * <p>
     * Default is <code>24</code>
     * <p>
     * @see #setSlaveConnectionPoolSize(int)
     * 
     * @param slaveConnectionMinimumIdleSize - pool size
     * @return config
     */
    public T setSlaveConnectionMinimumIdleSize(int slaveConnectionMinimumIdleSize) {
        this.slaveConnectionMinimumIdleSize = slaveConnectionMinimumIdleSize;
        return (T) this;
    }
    public int getSlaveConnectionMinimumIdleSize() {
        return slaveConnectionMinimumIdleSize;
    }

    /**
     * Redis 'master' node minimum idle connection amount for <b>each</b> slave node
     * <p>
     * Default is <code>24</code>
     * <p>
     * @see #setMasterConnectionPoolSize(int)
     * 
     * @param masterConnectionMinimumIdleSize - pool size
     * @return config
     */
    public T setMasterConnectionMinimumIdleSize(int masterConnectionMinimumIdleSize) {
        this.masterConnectionMinimumIdleSize = masterConnectionMinimumIdleSize;
        return (T) this;
    }
    public int getMasterConnectionMinimumIdleSize() {
        return masterConnectionMinimumIdleSize;
    }

    /**
     * Redis 'slave' node minimum idle subscription (pub/sub) connection amount for <b>each</b> slave node.
     * <p>
     * Default is <code>1</code>
     * <p>
     * @see #setSubscriptionConnectionPoolSize(int)
     * 
     * @param subscriptionConnectionMinimumIdleSize - pool size
     * @return config
     */
    public T setSubscriptionConnectionMinimumIdleSize(int subscriptionConnectionMinimumIdleSize) {
        this.subscriptionConnectionMinimumIdleSize = subscriptionConnectionMinimumIdleSize;
        return (T) this;
    }
    public int getSubscriptionConnectionMinimumIdleSize() {
        return subscriptionConnectionMinimumIdleSize;
    }

    
    /**
     * Set node type used for read operation.
     * <p>
     * Default is <code>SLAVE</code>
     *
     * @param readMode param
     * @return config
     */
    public T setReadMode(ReadMode readMode) {
        this.readMode = readMode;
        return (T) this;
    }
    public ReadMode getReadMode() {
        return readMode;
    }
    
    public boolean isSlaveNotUsed() {
        return getReadMode() == ReadMode.MASTER && getSubscriptionMode() == SubscriptionMode.MASTER;
    }

    /**
     * Set node type used for subscription operation.
     * <p>
     * Default is <code>MASTER</code>
     *
     * @param subscriptionMode param
     * @return config
     */
    public T setSubscriptionMode(SubscriptionMode subscriptionMode) {
        this.subscriptionMode = subscriptionMode;
        return (T) this;
    }
    public SubscriptionMode getSubscriptionMode() {
        return subscriptionMode;
    }

    /**
     * Interval in milliseconds to check the endpoint's DNS<p>
     * Applications must ensure the JVM DNS cache TTL is low enough to support this.<p>
     * Set <code>-1</code> to disable.
     * <p>
     * Default is <code>5000</code>.
     *
     * @param dnsMonitoringInterval time
     * @return config
     */
    public T setDnsMonitoringInterval(long dnsMonitoringInterval) {
        this.dnsMonitoringInterval = dnsMonitoringInterval;
        return (T) this;
    }
    public long getDnsMonitoringInterval() {
        return dnsMonitoringInterval;
    }

    /**
     * Defines failed Redis Slave node detector object
     * which implements failed node detection logic.
     * <p>
     * Default is <code>org.redisson.client.FailedConnectionDetector</code>
     *
     * @param failedNodeDetector Redis Slave node detector object
     * @return config
     *
     * @see org.redisson.client.FailedConnectionDetector
     * @see org.redisson.client.FailedCommandsDetector
     * @see org.redisson.client.FailedCommandsTimeoutDetector
     *
     */
    public T setFailedSlaveNodeDetector(FailedNodeDetector failedNodeDetector) {
        this.failedSlaveNodeDetector = failedNodeDetector;
        return (T) this;
    }
    public FailedNodeDetector getFailedSlaveNodeDetector() {
        return failedSlaveNodeDetector;
    }

}
