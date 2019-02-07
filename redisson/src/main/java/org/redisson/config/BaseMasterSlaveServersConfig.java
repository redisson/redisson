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
package org.redisson.config;

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
     * Сonnection load balancer for multiple Redis slave servers
     */
    private LoadBalancer loadBalancer = new RoundRobinLoadBalancer();

    /**
     * Redis 'slave' node minimum idle connection amount for <b>each</b> slave node
     */
    private int slaveConnectionMinimumIdleSize = 32;

    /**
     * Redis 'slave' node maximum connection pool size for <b>each</b> slave node
     */
    private int slaveConnectionPoolSize = 64;

    private int failedSlaveReconnectionInterval = 3000;
    
    private int failedSlaveCheckInterval = 180000;
    
    /**
     * Redis 'master' node minimum idle connection amount for <b>each</b> slave node
     */
    private int masterConnectionMinimumIdleSize = 32;

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
        setFailedSlaveCheckInterval(config.getFailedSlaveCheckInterval());
        setFailedSlaveReconnectionInterval(config.getFailedSlaveReconnectionInterval());
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
        return (T)this;
    }
    public int getSlaveConnectionPoolSize() {
        return slaveConnectionPoolSize;
    }
    
    /**
     * Interval of Redis Slave reconnection attempt when
     * it was excluded from internal list of available servers.
     * <p>
     * On every such timeout event Redisson tries
     * to connect to disconnected Redis server.
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
     * Redis Slave node failing to execute commands is excluded from the internal list of available nodes
     * when the time interval from the moment of first Redis command execution failure
     * on this server reaches <code>slaveFailsInterval</code> value.
     * <p>
     * Default is <code>180000</code>
     *
     * @param slaveFailsInterval - time interval in milliseconds
     * @return config
     */
    public T setFailedSlaveCheckInterval(int slaveFailsInterval) {
        this.failedSlaveCheckInterval = slaveFailsInterval;
        return (T) this;
    }
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
        return (T)this;
    }
    public int getMasterConnectionPoolSize() {
        return masterConnectionPoolSize;
    }

    /**
     * Сonnection load balancer to multiple Redis slave servers.
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
        return (T)this;
    }
    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    /**
     * @deprecated use {@link #setSubscriptionConnectionPoolSize(int)}
     * 
     * @param slaveSubscriptionConnectionPoolSize - pool size
     * @return config
     */
    @Deprecated
    public T setSlaveSubscriptionConnectionPoolSize(int slaveSubscriptionConnectionPoolSize) {
        return setSubscriptionConnectionPoolSize(slaveSubscriptionConnectionPoolSize);
    }
    @Deprecated
    public int getSlaveSubscriptionConnectionPoolSize() {
        return getSubscriptionConnectionPoolSize();
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
        return (T)this;
    }
    public int getSubscriptionConnectionPoolSize() {
        return subscriptionConnectionPoolSize;
    }

    
    /**
     * Minimum idle connection pool size for subscription (pub/sub) channels
     * <p>
     * Default is <code>10</code>
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
     * Default is <code>10</code>
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
     * @deprecated use {@link #setSubscriptionConnectionMinimumIdleSize(int)}
     * 
     * @param slaveSubscriptionConnectionMinimumIdleSize - pool size
     * @return config
     */
    @Deprecated
    public T setSlaveSubscriptionConnectionMinimumIdleSize(int slaveSubscriptionConnectionMinimumIdleSize) {
        return setSubscriptionConnectionMinimumIdleSize(slaveSubscriptionConnectionMinimumIdleSize);
    }
    @Deprecated
    public int getSlaveSubscriptionConnectionMinimumIdleSize() {
        return getSubscriptionConnectionMinimumIdleSize();
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
    
    public boolean checkSkipSlavesInit() {
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
    
}
